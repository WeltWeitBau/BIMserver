package de.weiltweitbau.geometry;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;

public class BooleanMeshOperations {
	
	public static Mesh intersection(Mesh meshA, Mesh meshB) {
		CSG csgA = convert(meshA);
		CSG csgB = convert(meshB);
		
		CSG csgIntersect = csgA.intersect(csgB);
		
		Mesh meshIntersect = convert(csgIntersect);
		
		return meshIntersect;
	}
	
	private static Mesh convert(CSG csg) {
		Map<Vector3d, Integer> vertices = new LinkedHashMap<>();
		Map<Integer, Vector3d> normals = new HashMap<>();
		List<Integer> indices = new LinkedList<>();
		
		for (Polygon polygon : csg.getPolygons()) {

			Vertex firstVertex = polygon.vertices.get(0);
			for (int i = 0; i < polygon.vertices.size() - 2; i++) {
				Vertex secondVertex = polygon.vertices.get(i + 1);
				Vertex thirdVertex = polygon.vertices.get(i + 2);
				
				addVertex(firstVertex, vertices, indices, normals);
				addVertex(secondVertex, vertices, indices, normals);
				addVertex(thirdVertex, vertices, indices, normals);
			}
		}
		
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		
		double maxX = Double.MIN_VALUE;
		double maxY = Double.MIN_VALUE;
		double maxZ = Double.MIN_VALUE;
		
		DoubleBuffer vertexBuffer = DoubleBuffer.allocate(vertices.size() * 3);
		for(Vector3d pos : vertices.keySet()) {
			vertexBuffer.put(pos.getX());
			vertexBuffer.put(pos.getY());
			vertexBuffer.put(pos.getZ());
			
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		
		DoubleBuffer normalBuffer = DoubleBuffer.allocate(normals.size() * 3);
		for(int i=0; i<normals.size(); i++) {
			Vector3d normal = normals.get(i);
			normalBuffer.put(normal.getX());
			normalBuffer.put(normal.getY());
			normalBuffer.put(normal.getZ());
		}
		
		IntBuffer indexBuffer = IntBuffer.allocate(indices.size());
		for(int currentIndex : indices) {
			indexBuffer.put(currentIndex);
		}
		
		Mesh mesh = new Mesh(indexBuffer, vertexBuffer, normalBuffer, null);
		
		mesh.setMinMax(new double[] {minX, minY, minZ, maxX, maxY, maxZ});
		
		return mesh;
	}
	
	private static void addVertex(Vertex vertex, Map<Vector3d, Integer> vertices, List<Integer> indices,
			Map<Integer, Vector3d> normals) {
		Integer currentIndex = vertices.get(vertex.pos);
		if (currentIndex != null) {
			indices.add(currentIndex);
		} else {
			currentIndex = vertices.size();
			indices.add(currentIndex);
			vertices.put(vertex.pos, currentIndex);
			normals.put(currentIndex, vertex.normal);
		}
	}
	
	private static CSG convert(Mesh mesh) {
		List<Polygon> polygons = new LinkedList<>();
		
		for(int i=0; i<mesh.getTriangleCount(); i++) {
			Triangle triangle = mesh.getTriangle(i);
			
			double[][] aVertices = triangle.getVertices();
			
			double[] aV1 = aVertices[0];
			Vector3d v1 = Vector3d.xyz(aV1[0], aV1[1], aV1[2]);
			
			double[] aV2 = aVertices[1];
			Vector3d v2 = Vector3d.xyz(aV2[0], aV2[1], aV2[2]);
			
			double[] aV3 = aVertices[2];
			Vector3d v3 = Vector3d.xyz(aV3[0], aV3[1], aV3[2]);
			
			Polygon polygon = Polygon.fromPoints(v1, v2, v3);
			
			polygons.add(polygon);
		}
		
		return CSG.fromPolygons(polygons);
	}
}
