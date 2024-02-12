package de.weiltweitbau.database.actions;

import java.util.HashSet;
import java.util.Set;

import org.bimserver.BimServer;
import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.BimserverLockConflictException;
import org.bimserver.database.DatabaseSession;
import org.bimserver.database.OldQuery;
import org.bimserver.database.actions.BimDatabaseAction;
import org.bimserver.database.queries.QueryObjectProvider;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.models.store.ConcreteRevision;
import org.bimserver.models.store.Revision;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.webservices.authorization.Authorization;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.weiltweitbau.database.actions.WwbLongClashDetectorAction.ClashDetectionProgressHandler;
import de.weiltweitbau.database.actions.clashes.ClashDetectionResults;
import de.weiltweitbau.database.actions.clashes.ClashDetector;
import de.weiltweitbau.database.actions.clashes.ClashDetector.GeometryModel;
import de.weiltweitbau.database.actions.clashes.ClashDetectorRules;

public class WwbClashDetectorAction extends BimDatabaseAction<ObjectNode> {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(WwbClashDetectorAction.class);
	
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
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
			+ "  },"
			+ "  \"includeProperties\":{\"_v\":[\"1.1.0\"]}"
			+ "}";
	
	private final long roid1;
	private final long roid2;
	
	private final String rules;

	private final BimServer bimServer;
	private Authorization authorization;
	private long serializerOid;
	
	private ClashDetectionProgressHandler progressHandler;
	
	private ClashDetector clashDetector = null;
	private ClashDetectorRules clashDetectionRules = null;
	
	public WwbClashDetectorAction(WwbClashDetectionParameters params, ClashDetectionProgressHandler progressHandler) {
		this(params.getBimServer(), params.getSession(), params.getAccessMethod(), params.getAuthorization(),
				params.getSerializerOid(), params.getRoid1(), params.getRoid2(), params.getRules(), progressHandler);
	}

	public WwbClashDetectorAction(BimServer bimServer, DatabaseSession databaseSession, AccessMethod accessMethod,
			Authorization authorization, long serializerOid, long roid1, long roid2, String rules,
			ClashDetectionProgressHandler progressHandler) {
		super(databaseSession, accessMethod);
		this.bimServer = bimServer;
		this.authorization = authorization;
		this.serializerOid = serializerOid;
		this.roid1 = roid1;
		this.roid2 = roid2;
		this.rules = rules;
		this.progressHandler = progressHandler;
	}

	@Override
	public ObjectNode execute()
			throws UserException, BimserverLockConflictException, BimserverDatabaseException, ServerException {
		this.progressHandler.readGeometries();

		try {
			clashDetectionRules = new ObjectMapper().readValue(rules, ClashDetectorRules.class);
			
			GeometryModel model1 = createModel(roid1, getDatabaseSession());
			GeometryModel model2 = model1;
			
			if(roid1 != roid2) {
				model2 = createModel(roid2, getDatabaseSession());
			}
			
			clashDetector = new ClashDetector(model1, model2, clashDetectionRules, progressHandler, roid1 == roid2);
			ClashDetectionResults results =  clashDetector.findClashesHM();
			ObjectNode resultNode = results.toJson();
			resultNode.put("roid1", roid1);
			resultNode.put("roid2", roid2);
			
			return resultNode;
		} catch (Exception e) {
			LOGGER.error("", e);
			throw new UserException(e);
		} finally {
			clashDetector = null;
		}
	}
	
	private GeometryModel createModel(long roid, DatabaseSession session) throws ServerException {
		try {
			Revision revision = session.get(roid, OldQuery.getDefault());
			
			double multiplierToM = revision.getLastConcreteRevision().getMultiplierToMm() / 1000;

			QueryObjectProvider queryProvider = createQueryProvider(revision, session);
			GeometryModel model = GeometryModel.fromQueryProvider(queryProvider, multiplierToM);
			
			setBounds(model, roid);

			return model;
		} catch (Exception e) {
			throw new ServerException(e);
		}
	}
	
	private QueryObjectProvider createQueryProvider(Revision revision, DatabaseSession session) throws Exception {
			PackageMetaData packageMetaData = bimServer.getMetaDataManager().getPackageMetaData(revision.getProject().getSchema());
			
			Set<Long> roids = new HashSet<>();
			roids.add(revision.getOid());
			
			ObjectNode query = OBJECT_MAPPER.readValue(QUERY, ObjectNode.class);
			
			if(clashDetectionRules.hasPropertySet() && clashDetectionRules.hasProperty()) {
				ObjectNode includeProperties = (ObjectNode) query.get("includeProperties");
				
				ArrayNode propertySet = OBJECT_MAPPER.createArrayNode();
				propertySet.add(clashDetectionRules.getProperty());
				
				includeProperties.set(clashDetectionRules.getPropertySet(), propertySet);
			}
			
			return QueryObjectProvider.fromJsonNode(session, bimServer, query, roids, packageMetaData);
	}
	
	private void setBounds(GeometryModel model, long roid) throws BimserverDatabaseException {
		Revision revision = getDatabaseSession().get(roid, OldQuery.getDefault());
		ConcreteRevision lastConcreteRevision = revision.getLastConcreteRevision();
		model.setBounds(lastConcreteRevision.getBounds());
	}
	
	public int getProgress() {
		if(clashDetector == null) {
			return -1;
		}
		
		return clashDetector.getProgress();
	}
}
