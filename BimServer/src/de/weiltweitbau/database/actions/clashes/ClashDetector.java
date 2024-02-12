package de.weiltweitbau.database.actions.clashes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.queries.QueryObjectProvider;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.models.geometry.Bounds;
import org.bimserver.models.geometry.GeometryInfo;
import org.bimserver.models.geometry.Vector3f;
import org.bimserver.shared.HashMapVirtualObject;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.slf4j.LoggerFactory;

import de.weiltweitbau.database.actions.WwbLongClashDetectorAction.ClashDetectionProgressHandler;
import de.weiltweitbau.database.actions.clashes.ClashDetectorRules.Combinations;
import de.weiltweitbau.database.actions.clashes.Octree.OctreeException;
import de.weiltweitbau.database.actions.clashes.Octree.OctreeValue;
import de.weiltweitbau.geometry.BooleanMeshOperations;
import de.weiltweitbau.geometry.Mesh;
import de.weiltweitbau.geometry.Triangle;

public class ClashDetector {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ClashDetector.class);

	public static class Combination {
		private String type1;
		private String type2;

		public Combination(String type1, String type2) {
			if (type1.compareTo(type2) > 0) {
				this.type1 = type1;
				this.type2 = type2;
			} else {
				this.type1 = type2;
				this.type2 = type1;
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((type1 == null) ? 0 : type1.hashCode());
			result = prime * result + ((type2 == null) ? 0 : type2.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else if (obj instanceof Combination == false) {
				return false;
			}
			
			Combination other = (Combination) obj;
			
			if (type1 == null) {
				if (other.type1 != null) {
					return false;
				}
			} else if (!type1.equals(other.type1)) {
				return false;
			}
				
			if (type2 == null) {
				if (other.type2 != null) {
					return false;
				}
			} else if (!type2.equals(other.type2)) {
				return false;
			}
			
			return true;
		}
	}
	
	private ClashDetectionProgressHandler progressHandler;
	
	private ClashDetectionResults clashDetectionResults;

	private ClashDetectorRules rules;
	
	private GeometryModel geomtryModel1;
	private GeometryModel geomtryModel2;
	
	private Set<Combination> combinationToIgnore = new HashSet<>();
	private Set<Combination> combinationToCheck = new HashSet<>();
	private Set<String> typesToOnlyCheckWithOwnType = new HashSet<>();
	private double epsilon;
	private final boolean bCheckAgainstSelf;
	private Set<Long> checkedOids;
	
	private Map<Long, String> types;
	private Map<Long, Mesh> meshes;
	
	public ClashDetector(GeometryModel model1, GeometryModel model2, ClashDetectorRules rules, ClashDetectionProgressHandler progressHandler, boolean bCheckAgainstSelf) {
		this.geomtryModel1 = model1;
		this.geomtryModel2 = model2;
		this.rules = rules;
		this.progressHandler = progressHandler;
		
		this.bCheckAgainstSelf = bCheckAgainstSelf;
	}

	private void configureRules() {
		if (!rules.isSkipDefaultRules()) {
			typesToOnlyCheckWithOwnType.add("IfcSpace");
			typesToOnlyCheckWithOwnType.add("IfcSite");

			combinationToIgnore.add(new Combination("IfcWall", "IfcOpeningElement"));
			combinationToIgnore.add(new Combination("IfcWallStandardCase", "IfcOpeningElement"));
			combinationToIgnore.add(new Combination("IfcSlab", "IfcOpeningElement"));

			combinationToIgnore.add(new Combination("IfcWall", "IfcWindow"));
			combinationToIgnore.add(new Combination("IfcWallStandardCase", "IfcWindow"));

			combinationToIgnore.add(new Combination("IfcWall", "IfcDoor"));
			combinationToIgnore.add(new Combination("IfcWallStandardCase", "IfcDoor"));

			combinationToIgnore.add(new Combination("IfcOpeningElement", "IfcWindow"));
			combinationToIgnore.add(new Combination("IfcOpeningElement", "IfcDoor"));
		}

		for (String onlyCheckWithOwnType : rules.getOnlyCheckWithOwnType()) {
			typesToOnlyCheckWithOwnType.add(onlyCheckWithOwnType);
		}

		addCombinations(rules.getTypesToIgnore(), combinationToIgnore);
		addCombinations(rules.getTypesToCheck(), combinationToCheck);
		
		this.epsilon = rules.getEpsilon();
		
		types = new HashMap<>();
		meshes = new HashMap<>();
	}

	private void addCombinations(Combinations[] combinations, Set<Combination> target) {
		for (Combinations combination : combinations) {
			for (String combinedWith : combination.getCombinedWith()) {
				target.add(new Combination(combination.getType(), combinedWith));
			}
		}
	}
	
	public ClashDetectionResults findClashesHM() throws Exception {
		configureRules();
		fetchTypes(geomtryModel1);
		
		if(!bCheckAgainstSelf) {
			fetchTypes(geomtryModel2);
		}

		checkedOids = new HashSet<Long>();

		clashDetectionResults = new ClashDetectionResults();
		
		findClashesUsingOctree();

		LOGGER.info("Duration in Milliseconds: " + (System.nanoTime() - clashDetectionResults.start)/1000000);
		LOGGER.info("With geometry: " + clashDetectionResults.nrWithGeometry);
		LOGGER.info("Without geometry: " + clashDetectionResults.nrWithoutGeometry);
		LOGGER.info("With open geometry: " + clashDetectionResults.nrWithOpenGeometry);
		LOGGER.info("Not enough data: " + clashDetectionResults.notEnoughData);
		LOGGER.info("Clashes: " + clashDetectionResults.size());

		return clashDetectionResults;
	}
	
	private void fetchTypes(GeometryModel model) {
		for(HashMapVirtualObject ifcProduct : model.getProducts()) {
			String type = getType(ifcProduct);
			
			if(type == null) {
				type = "";
			}
			
			types.put(ifcProduct.getOid(), type);
		}
	}
	
	private void findClashesUsingOctree() throws Exception {
		Octree octree = createOctreeHM();
		octree.traverseBreadthFirst((node) -> {
			for (OctreeValue value : node.getValues()) {
				if (!value.isModel1) {
					continue;
				}
				
				clashDetectionResults.checkedObjects++;
				
				if (System.nanoTime() - clashDetectionResults.lastDump > 1000000000L) {
					LOGGER.info((clashDetectionResults.checkedObjects * 100f / clashDetectionResults.totalObjects) + "%");
					clashDetectionResults.lastDump = System.nanoTime();
					
					if(progressHandler != null) {
						progressHandler.progress(clashDetectionResults.checkedObjects, clashDetectionResults.totalObjects);
					}
				}

				checkNodeValue1(node, value);
			}
			
			return true;
		});
		
		clashDetectionResults.nrWithGeometry = clashDetectionResults.checkedObjects;
		clashDetectionResults.nrWithoutGeometry = geomtryModel1.products.size() - clashDetectionResults.nrWithGeometry;
	}
	
	private void checkNodeValue1(Octree node, OctreeValue value1) {
		if (bCheckAgainstSelf) {
			checkedOids.add(value1.ifcProduct.getOid());
		}
		
		double[] minmax = value1.minmax;

		node.traverseUpAndBreadthFirstDown((childNode -> {
			if(!childNode.fits(minmax)) {
				return false;
			}
			
			for(OctreeValue value2 : childNode.getValues()) {
				if(value2.isModel1) {
					continue;
				}
				
				checkNodeValue1AgainstNodeValue2(value1, value2);
			}
			
			return true;
		}));
	}
	
	private void checkNodeValue1AgainstNodeValue2(OctreeValue value1, OctreeValue value2) {
		HashMapVirtualObject ifcProduct1 = value1.ifcProduct;
		HashMapVirtualObject ifcProduct2 = value2.ifcProduct;
		
		if (bCheckAgainstSelf && checkedOids.contains(ifcProduct2.getOid())) {
			return;
		}

		if (!shouldCheck(ifcProduct1, ifcProduct2)) {
			return;
		}

		if (!boundingBoxesClash(value1.minmax, value2.minmax)) {
			return;
		}

		if (!enoughData(value1, value2)) {
			clashDetectionResults.notEnoughData++;
			return;
		}
		
		Mesh mesh1 = getMesh(value1);
		Mesh mesh2 = getMesh(value2);

		if (!meshesClash(mesh1, mesh2)) {
			return;
		}
		
		boolean bComputeVolume = rules.isComputeVolumes()
				|| rules.getMinClashHorizontal() > 0
				|| rules.getMinClashVertical() > 0;
				
		if(bComputeVolume && mesh1.isClosed() && mesh2.isClosed()) {
			ClashVolume clashVolume = computeClashVolume(mesh1, mesh2);
			
			if(clashVolume != null) {
				if(clashVolume.getVolume() < rules.getMinClashVolume()
					|| clashVolume.getWidth() < rules.getMinClashHorizontal()
					|| clashVolume.getHeight() < rules.getMinClashVertical()) {
						return;
				}
				
				if(!rules.isComputeVolumes()) {
					clashVolume.clearIntersection();
				}
				
				clashDetectionResults.addClashVolume(ifcProduct1.getOid(), ifcProduct2.getOid(), clashVolume);
			}
		} else {
			
		}
		
		if(!mesh1.isClosed()) {
			
		}
		
		clashDetectionResults.addLeft(ifcProduct1, ifcProduct2);
		
		if(bCheckAgainstSelf) {
			clashDetectionResults.addLeft(ifcProduct2, ifcProduct1);
		} else {
			clashDetectionResults.addRight(ifcProduct2, ifcProduct1);
		}
	}
	
	private Mesh getMesh(OctreeValue value) {
		Mesh mesh = meshes.get(value.geometryInfo.getOid());
		
		if(mesh == null) {
			GeometryModel model = value.isModel1 ? geomtryModel1 : geomtryModel2;
			
			HashMapVirtualObject geometryData = model.getGeoData((Long) value.geometryInfo.get("data"));
			byte[] indices = (byte[]) model.getBuffer((Long) geometryData.get("indices")).get("data");
			byte[] vertices = (byte[]) model.getBuffer((Long) geometryData.get("vertices")).get("data");
			byte[] transformation = (byte[]) value.geometryInfo.get("transformation");
			
			mesh = Mesh.fromByteArrays(indices, vertices, transformation, model.getMultiplierToM());
			meshes.put(value.geometryInfo.getOid(), mesh);
			
			if(!mesh.isClosed()) {
				clashDetectionResults.nrWithOpenGeometry++;
			}
		}
		
		return mesh;
	}
	
	private boolean enoughData(OctreeValue value1, OctreeValue value2) {
		if(value1.geometryInfo == null || value2.geometryInfo == null) {
			return false;
		}
		
		// TODO
		
		return true;
	}
	
	private boolean shouldCheck(HashMapVirtualObject ifcProduct1, HashMapVirtualObject ifcProduct2) {
		if (ifcProduct1.getOid() == ifcProduct2.getOid()) {
			// As this is forbidden in Clash class, we do that early here
			return false;
		}

		String type1 = types.get(ifcProduct1.getOid());
		String type2 = types.get(ifcProduct2.getOid());
		Combination combination = new Combination(type1, type2);

		if (combinationToCheck != null && !combinationToCheck.isEmpty() && !combinationToCheck.contains(combination)) {
			return false;
		}
		
		if ((typesToOnlyCheckWithOwnType.contains(type1) || typesToOnlyCheckWithOwnType.contains(type2))
				&& !type1.equals(type2)) {
			return false;
		}

		if (combinationToIgnore.contains(combination)) {
			return false;
		}

		return true;
	}
	
	private String getType(HashMapVirtualObject ifcProduct) {
		if(!rules.hasProperty() && !rules.hasPropertySet()) {
			return ifcProduct.eClass().getName();
		}
		
		String property = rules.getProperty();
		String propertySet = rules.getPropertySet();
		
		if(ifcProduct.getAdditionalData() == null || ifcProduct.getAdditionalData().get("includedProperties") == null) {
			return "";
		}
		
		HashMap<String, String> includedProperties = (HashMap<String, String>) ifcProduct.getAdditionalData().get("includedProperties");
		return includedProperties.get(propertySet + ":" + property);
	}

	private boolean meshesClash(Mesh mesh1, Mesh mesh2) {
		if (mesh1 == null || mesh2 == null) {
			return false;
		}

		return mesh1.forSomeTriangles(triangle -> triangleClashesWithMesh(triangle, mesh2));
	}
	
	private ClashVolume computeClashVolume(Mesh mesh1, Mesh mesh2) {
		Mesh intersection;
		try {
			intersection = BooleanMeshOperations.intersection(mesh1, mesh2);
			return new ClashVolume(intersection);
		} catch (Throwable e) {
			clashDetectionResults.clashVolumeErrors++;
			LOGGER.error("Could not compute clash volume!", e);
		}
		
		return null;
	}
	
	private boolean triangleClashesWithMesh(Triangle triangle, Mesh mesh) {
		return mesh.forSomeTriangles(triangle2 -> triangleClashesWithTriangle(triangle, triangle2));
	}
	
	private boolean triangleClashesWithTriangle(Triangle triangle1, Triangle triangle2) {
		if (!triangle1.intersects(triangle2, epsilon, epsilon)) {
			return false;
		}
		
		return true;
	}
	
	private boolean triangleIntersectsBoundingBox(Triangle triangle, GeometryInfo geometryInfo2) {
		// TODO
		return true;
	}

	private boolean triangleInBoundingBox(Triangle triangle, GeometryInfo geometryInfo2) {
		for (double[] vertices : triangle.getVertices()) {
			if (vertices[0] >= geometryInfo2.getBounds().getMin().getX()
					&& vertices[0] <= geometryInfo2.getBounds().getMax().getX()
					&& vertices[1] >= geometryInfo2.getBounds().getMin().getY()
					&& vertices[1] <= geometryInfo2.getBounds().getMax().getY()
					&& vertices[2] >= geometryInfo2.getBounds().getMin().getZ()
					&& vertices[2] <= geometryInfo2.getBounds().getMax().getZ()) {
				return true;
			}
		}
		return false;
	}

	private boolean boundingBoxesClash(GeometryInfo geometryInfo1, GeometryInfo geometryInfo2) {
		return (geometryInfo1.getBounds().getMax().getX() > geometryInfo2.getBounds().getMin().getX()
				&& geometryInfo1.getBounds().getMin().getX() < geometryInfo2.getBounds().getMax().getX()
				&& geometryInfo1.getBounds().getMax().getY() > geometryInfo2.getBounds().getMin().getY()
				&& geometryInfo1.getBounds().getMin().getY() < geometryInfo2.getBounds().getMax().getY()
				&& geometryInfo1.getBounds().getMax().getZ() > geometryInfo2.getBounds().getMin().getZ()
				&& geometryInfo1.getBounds().getMin().getZ() < geometryInfo2.getBounds().getMax().getZ());
	}
	
	private boolean boundingBoxesClash(double[] minmax1, double[] minmax2) {
		return (minmax1[3] > minmax2[0]
				&& minmax1[0] < minmax2[3]
				&& minmax1[4] > minmax2[1]
				&& minmax1[1] < minmax2[4]
				&& minmax1[5] > minmax2[2]
				&& minmax1[2] < minmax2[5]);
	}
	
	private Octree createOctreeHM() throws OctreeException {
		Vector3f min1 = geomtryModel1.getBounds().getMin();
		Vector3f max1 = geomtryModel1.getBounds().getMax();
		
		Vector3f min2 = geomtryModel2.getBounds().getMin();
		Vector3f max2 = geomtryModel2.getBounds().getMax();
		
		double[] minmax = new double[] {
				Math.min(min1.getX() * geomtryModel1.getMultiplierToM(), min2.getX() * geomtryModel2.getMultiplierToM()),
				Math.min(min1.getY() * geomtryModel1.getMultiplierToM(), min2.getY() * geomtryModel2.getMultiplierToM()),
				Math.min(min1.getZ() * geomtryModel1.getMultiplierToM(), min2.getZ() * geomtryModel2.getMultiplierToM()),
				Math.max(max1.getX() * geomtryModel1.getMultiplierToM(), max2.getX() * geomtryModel2.getMultiplierToM()),
				Math.max(max1.getY() * geomtryModel1.getMultiplierToM(), max2.getY() * geomtryModel2.getMultiplierToM()),
				Math.max(max1.getZ() * geomtryModel1.getMultiplierToM(), max2.getZ() * geomtryModel2.getMultiplierToM()),
		};
		
		Octree octree = new Octree(minmax);
		
		octree.populateOctreeHM(geomtryModel1, true, clashDetectionResults);
		octree.populateOctreeHM(geomtryModel2, false, clashDetectionResults); // TODO: nur wenn unterschiedliche?
		
		return octree;
	}
	
	public int getProgress() {
		if(clashDetectionResults == null) {
			return -1;
		}
		
		return (clashDetectionResults.checkedObjects / clashDetectionResults.totalObjects) * 100;
	}
	
	public static class GeometryModel {
		private Map<Long, HashMapVirtualObject> products = new HashMap<>();
		private Map<Long, HashMapVirtualObject> geoInfos = new HashMap<>();
		private Map<Long, HashMapVirtualObject> geoDatas = new HashMap<>();
		private Map<Long, HashMapVirtualObject> buffers = new HashMap<>();
		
		private Bounds bounds = null;
		
		private double multiplierToM = 1;
		
		public void setBounds(Bounds bounds) {
			this.bounds = bounds;
		}
		
		public Bounds getBounds() {
			return bounds;
		}
		
		public static GeometryModel fromQueryProvider(QueryObjectProvider queryProvider, double multiplierToM) throws BimserverDatabaseException {
			GeometryModel model = new GeometryModel();
			
			model.multiplierToM = multiplierToM;

			HashMapVirtualObject current = queryProvider.next();
			while (current != null) {
				switch ((String) current.eClass().getName()) {
				case "GeometryInfo":
					model.addGeoInfo(current);
					break;
				case "GeometryData":
					model.addGeoData(current);
					break;
				case "Buffer":
					model.addBuffer(current);
					break;

				default:
					model.addProduct(current);
					break;
				}

				current = queryProvider.next();
			}

			return model;
		}
		
		public void addProduct(HashMapVirtualObject product) {
			products.put(product.getOid(), product);
		}
		
		public Collection<HashMapVirtualObject> getProducts() {
			return products.values();
		}
		
		public void addGeoInfo(HashMapVirtualObject geoInfo) {
			geoInfos.put(geoInfo.getOid(), geoInfo);
		}
		
		public HashMapVirtualObject getGeoInfo(long oid) {
			return geoInfos.get(oid);
		}
		
		public void addGeoData(HashMapVirtualObject geoData) {
			geoDatas.put(geoData.getOid(), geoData);
		}
		
		public HashMapVirtualObject getGeoData(long oid) {
			return geoDatas.get(oid);
		}
		
		public void addBuffer(HashMapVirtualObject buffer) {
			buffers.put(buffer.getOid(), buffer);
		}
		
		public HashMapVirtualObject getBuffer(long oid) {
			return buffers.get(oid);
		}
		
		public double getMultiplierToM() {
			return multiplierToM;
		}
	}
}
