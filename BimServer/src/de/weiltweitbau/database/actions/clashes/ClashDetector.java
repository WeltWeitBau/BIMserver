package de.weiltweitbau.database.actions.clashes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.emf.ModelMetaData;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.interfaces.objects.SVector3f;
import org.bimserver.models.geometry.Bounds;
import org.bimserver.models.geometry.GeometryData;
import org.bimserver.models.geometry.GeometryInfo;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.slf4j.LoggerFactory;

import de.weiltweitbau.database.actions.clashes.ClashDetectorRules.Combinations;
import de.weiltweitbau.database.actions.clashes.Octree.OctreeException;
import de.weiltweitbau.database.actions.clashes.Octree.OctreeValue;

public class ClashDetector {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ClashDetector.class);

	public static class Combination {
		private String type1;
		private String type2;

		// TODO: EClass verwenden
		public Combination(String type1, String type2) {
			// Make canonical
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

	private IfcModelInterface model1;
	private IfcModelInterface model2;
	private ClashDetectorRules rules;
	private List<IdEObject> products1;
	private List<IdEObject> products2;
	private Set<Combination> combinationToIgnore = new HashSet<>();
	private Set<Combination> combinationToCheck = new HashSet<>();
	private Set<String> typesToOnlyCheckWithOwnType = new HashSet<>();
	private double epsilon;
	private final boolean bCheckAgainstSelf;
	private Set<Long> checkedOids;
	private ClashDetectionResults clashDetectionResults;

	public ClashDetector(IfcModelInterface model1, IfcModelInterface model2, ClashDetectorRules rules) {
		this.model1 = model1;
		this.model2 = model2;
		this.rules = rules;
		
		this.bCheckAgainstSelf = model1.getModelMetaData().getName().equals(model2.getModelMetaData().getName());
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
	}

	private void addCombinations(Combinations[] combinations, Set<Combination> target) {
		for (Combinations combination : combinations) {
			for (String combinedWith : combination.getCombinedWith()) {
				target.add(new Combination(combination.getType(), combinedWith));
			}
		}
	}

	private List<IdEObject> fetchProducts(IfcModelInterface model) {
		PackageMetaData packageMetaData = model.getPackageMetaData();
		EClass ifcProductClass = packageMetaData.getEClass("IfcProduct");
		return model.getAllWithSubTypes(ifcProductClass);
	}

	public ClashDetectionResults findClashes() throws Exception {
		configureRules();
		products1 = fetchProducts(model1);

		checkedOids = new HashSet<Long>();

		if (bCheckAgainstSelf) {
			products2 = products1;
		} else {
			products2 = fetchProducts(model2);
		}

		clashDetectionResults = new ClashDetectionResults();
		
		findClashesUsingOctree();
		
//		findClashesUsingBruteForce();

		LOGGER.info("Duration in Milliseconds: " + (System.nanoTime() - clashDetectionResults.start)/1000000);
		LOGGER.info("With geometry: " + clashDetectionResults.nrWithGeometry);
		LOGGER.info("Without geometry: " + clashDetectionResults.nrWithoutGeometry);
		LOGGER.info("Not enough data: " + clashDetectionResults.notEnoughData);
		LOGGER.info("Clashes: " + clashDetectionResults.size());

		return clashDetectionResults;
	}
	
	private void findClashesUsingOctree() throws Exception {
		Octree octree = createOctree();
		octree.traverseBreadthFirst((node) -> {
			for (OctreeValue value : node.getValues()) {
				if (!value.isModel1) {
					continue;
				}
				
				clashDetectionResults.checkedObjects++;
				
				if (System.nanoTime() - clashDetectionResults.lastDump > 1000000000L) {
					LOGGER.info((clashDetectionResults.checkedObjects * 100f / clashDetectionResults.totalObjects) + "%");
					clashDetectionResults.lastDump = System.nanoTime();
				}

				checkNodeValue1(node, value);
			}
			
			return true;
		});
		
		clashDetectionResults.nrWithGeometry = clashDetectionResults.checkedObjects;
		clashDetectionResults.nrWithoutGeometry = products1.size() - clashDetectionResults.nrWithGeometry;
	}
	
	private void findClashesUsingBruteForce() {
		for (IdEObject ifcProduct1 : products1) {
			if (bCheckAgainstSelf) {
				checkedOids.add(ifcProduct1.getOid());
			}

			EStructuralFeature geometryFeature = ifcProduct1.eClass().getEStructuralFeature("geometry");
			GeometryInfo geometryInfo1 = (GeometryInfo) ifcProduct1.eGet(geometryFeature);
			if (geometryInfo1 == null) {
				clashDetectionResults.nrWithoutGeometry++;
				continue;
			}

			clashDetectionResults.nrWithGeometry++;

			for (IdEObject ifcProduct2 : products2) {
				if (System.nanoTime() - clashDetectionResults.lastDump > 5000000000L) {
					long totalTime = System.nanoTime() - clashDetectionResults.start;
					LOGGER.info((clashDetectionResults.totalTimeTriangles * 100f / totalTime) + "%");
					clashDetectionResults.lastDump = System.nanoTime();
				}

				if (bCheckAgainstSelf && checkedOids.contains(ifcProduct2.getOid())) {
					continue;
				}

				if (!shouldCheck(ifcProduct1, ifcProduct2)) {
					continue;
				}

				GeometryInfo geometryInfo2 = (GeometryInfo) ifcProduct2.eGet(geometryFeature);
				if (geometryInfo2 == null) {
					continue;
				}

				if (!boundingBoxesClash(geometryInfo1, geometryInfo2)) {
					continue;
				}

				if (!enoughData(geometryInfo1, geometryInfo2)) {
					clashDetectionResults.notEnoughData++;
					continue;
				}

				long startTriangles = System.nanoTime();
				if (trianglesClash(geometryInfo1, geometryInfo2)) {
					clashDetectionResults.addLeft(ifcProduct1, ifcProduct2);

					if (bCheckAgainstSelf) {
						clashDetectionResults.addLeft(ifcProduct2, ifcProduct1);
					} else {
						clashDetectionResults.addRight(ifcProduct2, ifcProduct1);
					}
				}
				long endTriangles = System.nanoTime();
				clashDetectionResults.totalTimeTriangles += (endTriangles - startTriangles);
			}
		}
	}
	
	private void checkNodeValue1(Octree node, OctreeValue value1) {
		if (bCheckAgainstSelf) {
			checkedOids.add(value1.ifcProduct.getOid());
		}
		
		Bounds bounds1 = value1.geometryInfo.getBounds();

		node.traverseUpAndBreadthFirstDown((childNode -> {
			if(!childNode.fitsInside(bounds1)) {
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
		IdEObject ifcProduct1 = value1.ifcProduct;
		IdEObject ifcProduct2 = value2.ifcProduct;
		
		if (bCheckAgainstSelf && checkedOids.contains(ifcProduct2.getOid())) {
			return;
		}

		if (!shouldCheck(ifcProduct1, ifcProduct2)) {
			return;
		}

		GeometryInfo geometryInfo1 = value1.geometryInfo;
		GeometryInfo geometryInfo2 = value2.geometryInfo;

		if (!boundingBoxesClash(geometryInfo1, geometryInfo2)) {
			return;
		}

		if (!enoughData(geometryInfo1, geometryInfo2)) {
			clashDetectionResults.notEnoughData++;
			return;
		}

//		long startTriangles = System.nanoTime();
		if (trianglesClash(geometryInfo1, geometryInfo2)) {
			clashDetectionResults.addLeft(ifcProduct1, ifcProduct2);
			
			if(bCheckAgainstSelf) {
				clashDetectionResults.addLeft(ifcProduct2, ifcProduct1);
			} else {
				clashDetectionResults.addRight(ifcProduct2, ifcProduct1);
			}
		}
	}

	private boolean enoughData(GeometryInfo geometryInfo1, GeometryInfo geometryInfo2) {
		GeometryData data1 = geometryInfo1.getData();
		GeometryData data2 = geometryInfo2.getData();

		if (data1 == null || data2 == null) {
			return false;
		}
		if (data1.getIndices() == null || data2.getIndices() == null) {
			return false;
		}
		if (data1.getIndices().getData() == null || data2.getIndices().getData() == null) {
			return false;
		}
		return true;
	}

	private boolean shouldCheck(IdEObject ifcProduct1, IdEObject ifcProduct2) {
		if (ifcProduct1.getOid() == ifcProduct2.getOid()) {
			// As this is forbidden in Clash class, we do that early here
			return false;
		}

		String type1 = ifcProduct1.eClass().getName();
		String type2 = ifcProduct2.eClass().getName();
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

	private boolean trianglesClash(GeometryInfo geometryInfo1, GeometryInfo geometryInfo2) {
		GeometryData data1 = geometryInfo1.getData();
		GeometryData data2 = geometryInfo2.getData();

		if (data1 == null || data2 == null) {
			return false;
		}
		if (data1.getIndices() == null || data2.getIndices() == null) {
			return false;
		}
		if (data1.getIndices().getData() == null || data2.getIndices().getData() == null) {
			return false;
		}

		IntBuffer indices1 = getIntBuffer(data1.getIndices().getData());
		DoubleBuffer vertices1 = getDoubleBuffer(data1.getVertices().getData());

		IntBuffer indices2 = getIntBuffer(data2.getIndices().getData());
		DoubleBuffer vertices2 = getDoubleBuffer(data2.getVertices().getData());

		DoubleBuffer transformation1 = getDoubleBuffer(geometryInfo1.getTransformation());
		double[] transformationArray1 = new double[16];
		for (int i = 0; i < 16; i++) {
			transformationArray1[i] = transformation1.get();
		}

		DoubleBuffer transformation2 = getDoubleBuffer(geometryInfo2.getTransformation());
		double[] transformationArray2 = new double[16];
		for (int i = 0; i < 16; i++) {
			transformationArray2[i] = transformation2.get();
		}

		for (int i = 0; i < indices1.capacity(); i += 3) {
			Triangle triangle = new Triangle(indices1, vertices1, i, transformationArray1);

//			if (!triangleInBoundingBox(triangle, geometryInfo2)) {
//				continue;
//			}

			for (int j = 0; j < indices2.capacity(); j += 3) {
				Triangle triangle2 = new Triangle(indices2, vertices2, j, transformationArray2);

				if (triangle.intersects(triangle2, epsilon, epsilon)) {
					return true;
				}
			}
		}
		return false;
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

	private DoubleBuffer getDoubleBuffer(byte[] input) {
		ByteBuffer vertexBuffer = ByteBuffer.wrap(input);
		vertexBuffer.order(ByteOrder.LITTLE_ENDIAN);
		DoubleBuffer doubleBuffer = vertexBuffer.asDoubleBuffer();
		doubleBuffer.position(0);
		return doubleBuffer;
	}

	private IntBuffer getIntBuffer(byte[] input) {
		ByteBuffer indicesBuffer = ByteBuffer.wrap(input);
		indicesBuffer.order(ByteOrder.LITTLE_ENDIAN);
		IntBuffer indicesIntBuffer = indicesBuffer.asIntBuffer();
		return indicesIntBuffer;
	}

	private boolean boundingBoxesClash(GeometryInfo geometryInfo1, GeometryInfo geometryInfo2) {
		return (geometryInfo1.getBounds().getMax().getX() > geometryInfo2.getBounds().getMin().getX()
				&& geometryInfo1.getBounds().getMin().getX() < geometryInfo2.getBounds().getMax().getX()
				&& geometryInfo1.getBounds().getMax().getY() > geometryInfo2.getBounds().getMin().getY()
				&& geometryInfo1.getBounds().getMin().getY() < geometryInfo2.getBounds().getMax().getY()
				&& geometryInfo1.getBounds().getMax().getZ() > geometryInfo2.getBounds().getMin().getZ()
				&& geometryInfo1.getBounds().getMin().getZ() < geometryInfo2.getBounds().getMax().getZ());
	}
	
	private Octree createOctree() throws OctreeException {
		ModelMetaData meta1 = model1.getModelMetaData();
		SVector3f min1 = meta1.getMinBounds();
		SVector3f max1 = meta1.getMaxBounds();
		
		ModelMetaData meta2 = model2.getModelMetaData();
		SVector3f min2 = meta2.getMinBounds();
		SVector3f max2 = meta2.getMaxBounds();
		
		double[] minmax = new double[] {
				Math.min(min1.getX(), min2.getX()),
				Math.min(min1.getY(), min2.getY()),
				Math.min(min1.getZ(), min2.getZ()),
				Math.max(max1.getX(), max2.getX()),
				Math.max(max1.getY(), max2.getY()),
				Math.max(max1.getZ(), max2.getZ()),
		};
		
		Octree octree = new Octree(minmax);
		
		octree.populateOctree(products1, true, clashDetectionResults);
		octree.populateOctree(products2, false, clashDetectionResults);
		
		return octree;
	}
}
