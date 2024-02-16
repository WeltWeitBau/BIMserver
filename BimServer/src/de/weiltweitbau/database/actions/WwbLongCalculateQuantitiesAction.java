package de.weiltweitbau.database.actions;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bimserver.BimServer;
import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.DatabaseSession;
import org.bimserver.database.OldQuery;
import org.bimserver.database.OperationType;
import org.bimserver.database.queries.QueryObjectProvider;
import org.bimserver.database.queries.om.QueryException;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.interfaces.objects.SProgressTopicType;
import org.bimserver.interfaces.objects.SUser;
import org.bimserver.longaction.LongAction;
import org.bimserver.longaction.LongActionKey;
import org.bimserver.models.store.ConcreteRevision;
import org.bimserver.models.store.Revision;
import org.bimserver.shared.HashMapVirtualObject;
import org.bimserver.webservices.authorization.Authorization;
import org.slf4j.LoggerFactory;

import de.weiltweitbau.geometry.Mesh;

public class WwbLongCalculateQuantitiesAction extends LongAction<LongActionKey> {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(WwbLongCalculateQuantitiesAction.class);
	
	private static final String QUERY = "{"
			+ "\"type\": {"
			+ "    \"name\": \"IfcProduct\","
			+ "    \"includeAllSubTypes\": true,"
			+ "    \"exclude\": ["
			+ "      \"IfcAnnotation\""
			+ "    ]"
			+ "  },"
			+ "  \"include\": {"
			+ "    \"type\": \"IfcProduct\","
			+ "    \"field\": \"geometry\","
			+ "    \"include\": {"
			+ "      \"type\": \"GeometryInfo\","
			+ "      \"field\": \"data\","
			+ "      \"include\": {"
			+ "        \"type\": \"GeometryData\","
			+ "        \"fields\": ["
			+ "          \"indices\","
			+ "          \"vertices\""
			+ "        ]"
			+ "      }"
			+ "    }"
			+ "  }"
			+ "}";
	
	private long roid;
	private float multiplierToM = 1;
	
	private long maxIndex = 0;
	private long currentIndex = 0;
	private long lastProgressUpdate = 0;
	private long progressUpdateIntervalInMillis = 1000;

	public WwbLongCalculateQuantitiesAction(BimServer bimServer, SUser user, Authorization authorization, long roid) {
		super(bimServer, user.getName(), user.getUsername(), authorization);
		
		this.roid = roid;
		
		setProgressTopic(bimServer.getNotificationsManager().createProgressTopic(SProgressTopicType.RUNNING_SERVICE, "CalculateQuantities"));
	}
		
	public WwbLongCalculateQuantitiesAction(LongAction<?> parent, long roid) {
		super(parent.getBimServer(), parent.getUserName(), parent.getUserUsername(), parent.getAuthorization());
		
		this.roid = roid;
		
		setProgressTopic(parent.getProgressTopic());
	}

	@Override
	public String getDescription() {
		return "Calculates surface area and volume for each component";
	}

	@Override
	public void execute() {
		long lStart = System.currentTimeMillis();
		
		LOGGER.info("Start calculating quantities.");
		
		try (DatabaseSession session = getBimServer().getDatabase().createSession(OperationType.READ_WRITE)) {
			QueryObjectProvider queryProvider = createQueryProvider(session);
			
			List<HashMapVirtualObject> geoInfos = new LinkedList<>();
			List<HashMapVirtualObject> geoDatas = new LinkedList<>();
			Map<Long, HashMapVirtualObject> buffers = new HashMap<>();
			
			collectObjects(queryProvider, geoInfos, geoDatas, buffers);
			
			Map<Long, Double[]> quantities = calculateQuantities(geoDatas, buffers);
			
			storeQuantities(quantities, geoInfos);
			
			session.commit();
			
			LOGGER.info("done (" + (System.currentTimeMillis() - lStart) + "ms)");
		} catch (Exception e) {
			LOGGER.error("Could not calculate quantities!", e);
		}
	}
	
	private QueryObjectProvider createQueryProvider(DatabaseSession session)
			throws BimserverDatabaseException, QueryException, IOException {
		Revision revision = session.get(roid, OldQuery.getDefault());
		ConcreteRevision concreteRevision = revision.getLastConcreteRevision();
		
		multiplierToM = concreteRevision.getMultiplierToMm() / 1000;
		
		PackageMetaData packageMetaData = getBimServer().getMetaDataManager().getPackageMetaData(revision.getProject().getSchema());
		
		Set<Long> roids = new HashSet<>();
		roids.add(roid);
		
		return QueryObjectProvider.fromJsonString(session, getBimServer(), QUERY, roids, packageMetaData);
	}
	
	private void collectObjects(QueryObjectProvider queryProvider,
			List<HashMapVirtualObject> geoInfos,
			List<HashMapVirtualObject> geoDatas,
			Map<Long, HashMapVirtualObject> buffers) throws BimserverDatabaseException {
		HashMapVirtualObject current = queryProvider.next();
		while (current != null) {
			switch ((String) current.eClass().getName()) {
				case "GeometryInfo":
					geoInfos.add(current);
					break;
				case "GeometryData":
					geoDatas.add(current);
					break;
				case "Buffer":
					buffers.put(current.getOid(), current);
					break;
	
				default:
					break;
			}
			
			current = queryProvider.next();
		}
	}
	
	private Map<Long, Double[]> calculateQuantities(List<HashMapVirtualObject> geoDatas, Map<Long, HashMapVirtualObject> buffers) {
		maxIndex = geoDatas.size();
		
		Map<Long, Double[]> quantities = new HashMap<>();
		
		double areaMultiplier = Math.pow(multiplierToM, 2);
		double volumeMultiplier = Math.pow(multiplierToM, 3);
		
		for(HashMapVirtualObject geoData : geoDatas) {
			try {
				currentIndex++;
				
				updateCalculationProgress();
				
				long indicesOid = (Long) geoData.get("indices");
				long verticesOid = (Long) geoData.get("vertices");
				
				byte[] indices = (byte[]) buffers.get(indicesOid).get("data");
				byte[] vertices = (byte[]) buffers.get(verticesOid).get("data");
				
				Mesh mesh = Mesh.fromByteArrays(indices, vertices);
				double volume = mesh.computeVolume() * volumeMultiplier;
				double area = mesh.computeArea() * areaMultiplier;
				
				quantities.put(geoData.getOid(), new Double[] {volume, area});
			} catch (Exception e) {
				LOGGER.error("Could not calculate quantities for geoData oid: " + geoData.getOid(), e);
				
				quantities.put(geoData.getOid(), new Double[] {0., 0.});
			}
		}
		
		return quantities;
	}
	
	private void storeQuantities(Map<Long, Double[]> mappedQuantities, List<HashMapVirtualObject> geoInfos)
			throws BimserverDatabaseException {
		for (HashMapVirtualObject geoInfo : geoInfos) {
			Double[] quantities = mappedQuantities.get((Long) geoInfo.get("data"));
			geoInfo.set("volume", quantities[0]);
			geoInfo.set("area", quantities[1]);
			geoInfo.saveOverwrite();
		}
	}
	
	private void updateCalculationProgress() {
		long currentTime = System.currentTimeMillis();
		
		if(currentTime - lastProgressUpdate < progressUpdateIntervalInMillis) {
			return;
		}
		
		if(maxIndex == 0) {
			return;
		}
		
		int percent = (int) ((100 * currentIndex) / maxIndex);
		
		LOGGER.info(percent + "%");
		
		lastProgressUpdate = currentTime;
		
		updateProgress("Calculating quantities", percent);
	}
}
