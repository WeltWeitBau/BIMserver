package de.weiltweitbau.geometry;

//import java.nio.DoubleBuffer;
//import java.nio.IntBuffer;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//
//import eu.printingin3d.javascad.coords.Coords3d;
//import eu.printingin3d.javascad.coords.Triangle3d;
//import eu.printingin3d.javascad.vrl.CSG;
//import eu.printingin3d.javascad.vrl.Facet;
//import eu.printingin3d.javascad.vrl.Polygon;

public class BooleanMeshOperationsJavascad {
	
//	public static Mesh intersection(Mesh meshA, Mesh meshB) {
//		double[] offset = getMinMaxOffset(meshA.getMinMax());
//		
//		CSG csgA = convert(meshA, offset);
//		CSG csgB = convert(meshB, offset);
//		
//		CSG csgIntersectA = csgA.intersect(csgB);
//		CSG csgIntersectB = csgB.intersect(csgA);
//		
//		List<Polygon> polygons = new ArrayList<>();
//		polygons.addAll(csgIntersectA.getPolygons());
//		polygons.addAll(csgIntersectB.getPolygons());
//		CSG csgIntersect = new CSG(polygons);
//		
////		CSG csgIntersect = csgIntersectB.union(csgIntersectA);
//		
//		Mesh meshIntersect = convert(csgIntersect, offset);
//		
//		return meshIntersect;
//	}
//	
//	private static double[] getMinMaxOffset(double[] minMax) {
//		return new double[] {
//			(minMax[0] + minMax[3])/(2),
//			(minMax[1] + minMax[4])/(2),
//			(minMax[2] + minMax[5])/(2),
//		};
//	}
//	
//	private static Mesh convert(CSG csg, double[] offset) {
//		Map<Coords3d, Integer> vertices = new LinkedHashMap<>();
//		Map<Integer, Coords3d> normals = new HashMap<>();
//		List<Integer> indices = new LinkedList<>();
//		
//		for (Facet facet : csg.toFacets()) {
//			Triangle3d triangle = facet.getTriangle();
//			List<Coords3d> points = triangle.getPoints();
//			Coords3d firstVertex = points.get(0);
//			Coords3d secondVertex = points.get(1);
//			Coords3d thirdVertex = points.get(2);
//			
//			Coords3d normal = facet.getNormal();
//			
//			addVertex(firstVertex, normal, vertices, indices, normals);
//			addVertex(secondVertex, normal, vertices, indices, normals);
//			addVertex(thirdVertex, normal, vertices, indices, normals);
//			
//		}
//		
//		double minX = Double.MAX_VALUE;
//		double minY = Double.MAX_VALUE;
//		double minZ = Double.MAX_VALUE;
//		
//		double maxX = Double.MIN_VALUE;
//		double maxY = Double.MIN_VALUE;
//		double maxZ = Double.MIN_VALUE;
//		
//		DoubleBuffer vertexBuffer = DoubleBuffer.allocate(vertices.size() * 3);
//		for(Coords3d vertex : vertices.keySet()) {
//			double[] pos = convert(vertex, offset);
//			vertexBuffer.put(pos[0]);
//			vertexBuffer.put(pos[1]);
//			vertexBuffer.put(pos[2]);
//			
//			minX = Math.min(minX, pos[0]);
//			minY = Math.min(minY, pos[1]);
//			minZ = Math.min(minZ, pos[2]);
//			
//			maxX = Math.max(maxX, pos[0]);
//			maxY = Math.max(maxY, pos[1]);
//			maxZ = Math.max(maxZ, pos[2]);
//		}
//		
//		DoubleBuffer normalBuffer = DoubleBuffer.allocate(normals.size() * 3);
//		for(int i=0; i<normals.size(); i++) {
//			Coords3d normal = normals.get(i);
//			normalBuffer.put(normal.getX());
//			normalBuffer.put(normal.getY());
//			normalBuffer.put(normal.getZ());
//		}
//		
//		IntBuffer indexBuffer = IntBuffer.allocate(indices.size());
//		for(int currentIndex : indices) {
//			indexBuffer.put(currentIndex);
//		}
//		
//		Mesh mesh = new Mesh(indexBuffer, vertexBuffer, normalBuffer, null);
//		
//		mesh.setMinMax(new double[] {minX, minY, minZ, maxX, maxY, maxZ});
//		
//		return mesh;
//	}
//	
//	private static double[] convert(Coords3d vertex, double[] offset) {
//		return new double[] {
//				vertex.getX() + offset[0],
//				vertex.getY() + offset[1],
//				vertex.getZ() + offset[2],
//		};
//	}
//	
//	private static void addVertex(Coords3d vertex, Coords3d normal, Map<Coords3d, Integer> vertices, List<Integer> indices,
//			Map<Integer, Coords3d> normals) {
//		Integer currentIndex = vertices.get(vertex);
//		if (currentIndex != null) {
//			indices.add(currentIndex);
//		} else {
//			currentIndex = vertices.size();
//			indices.add(currentIndex);
//			vertices.put(vertex, currentIndex);
//			normals.put(currentIndex, normal);
//		}
//	}
//	
//	private static CSG convert(Mesh mesh, double[] offset) {
//		List<Polygon> polygons = new LinkedList<>();
//
//		mesh.forEachTriangle((triangle) -> {
//			double[][] aVertices = triangle.getVertices();
//
//			Coords3d v1 = convert(aVertices[0], offset);
//			Coords3d v2 = convert(aVertices[1], offset);
//			Coords3d v3 = convert(aVertices[2], offset);
//
//			List<Coords3d> vertices = new LinkedList<>();
//			vertices.add(v1);
//			vertices.add(v2);
//			vertices.add(v3);
//
//			Polygon polygon = Polygon.fromPolygons(vertices, null);
//
//			polygons.add(polygon);
//		});
//		
//		return new CSG(polygons);
//	}
//	
//	private static Coords3d convert(double[] aV, double[] offset) {
//		return new Coords3d(aV[0] - offset[0], aV[1] - offset[1], aV[2] - offset[2]);
//	}
}
