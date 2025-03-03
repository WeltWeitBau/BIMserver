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
import eu.mihosoft.vrl.v3d.ext.quickhull3d.HullUtil;

public class BooleanMeshOperations {
	
	public static Mesh hullIntersection(Mesh meshA, Mesh meshB) {
		double[] offset = getMinMaxOffset(meshA.getMinMax());
		
		CSG csgA = convertHull(meshA, offset);
		CSG csgB = convertHull(meshB, offset);
		
		CSG csgIntersect = csgA.intersect(csgB);
		
		Mesh meshIntersect = convert(csgIntersect, offset);
		
		return meshIntersect;
	}
	
	public static Mesh intersection(Mesh meshA, Mesh meshB) {
		double[] offset = getMinMaxOffset(meshA.getMinMax());
		
		CSG csgA = convert(meshA, offset);
		CSG csgB = convert(meshB, offset);
		
		CSG csgIntersect = csgA.intersect(csgB);
		
		Mesh meshIntersect = convert(csgIntersect, offset);
		
		return meshIntersect;
	}
	
	private static double[] getMinMaxOffset(double[] minMax) {
		return new double[] {
			(minMax[0] + minMax[3])/(2),
			(minMax[1] + minMax[4])/(2),
			(minMax[2] + minMax[5])/(2),
		};
	}
	
	private static Mesh convert(CSG csg, double[] offset) {
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
		for(Vector3d vertex : vertices.keySet()) {
			double[] pos = convert(vertex, offset);
			vertexBuffer.put(pos[0]);
			vertexBuffer.put(pos[1]);
			vertexBuffer.put(pos[2]);
			
			minX = Math.min(minX, pos[0]);
			minY = Math.min(minY, pos[1]);
			minZ = Math.min(minZ, pos[2]);
			
			maxX = Math.max(maxX, pos[0]);
			maxY = Math.max(maxY, pos[1]);
			maxZ = Math.max(maxZ, pos[2]);
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
	
	private static double[] convert(Vector3d vertex, double[] offset) {
		return new double[] {
				vertex.getX() + offset[0],
				vertex.getY() + offset[1],
				vertex.getZ() + offset[2],
		};
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
	
	private static CSG convert(Mesh mesh, double[] offset) {
		List<Polygon> polygons = new LinkedList<>();
		
		for(int i=0; i<mesh.getTriangleCount(); i++) {
			Triangle triangle = mesh.getTriangle(i);
			
			double[][] aVertices = triangle.getVertices();
			
			Vector3d v1 = convert(aVertices[0], offset);
			Vector3d v2 = convert(aVertices[1], offset);
			Vector3d v3 = convert(aVertices[2], offset);
			
			Polygon polygon = Polygon.fromPoints(v1, v2, v3);
			
			polygons.add(polygon);
		}
		
		return CSG.fromPolygons(polygons);
	}
	
	private static Vector3d convert(double[] aV, double[] offset) {
		return new Vector3d(aV[0] - offset[0], aV[1] - offset[1], aV[2] - offset[2]);
	}
	
	private static CSG convertHull(Mesh mesh, double[] offset) {
		List<Vector3d> points = new LinkedList<>();
		
		for(int i=0; i<mesh.getTriangleCount(); i++) {
			Triangle triangle = mesh.getTriangle(i);
			
			double[][] aVertices = triangle.getVertices();
			
			Vector3d v1 = convert(aVertices[0], offset);
			Vector3d v2 = convert(aVertices[1], offset);
			Vector3d v3 = convert(aVertices[2], offset);
			
			points.add(v1);
			points.add(v2);
			points.add(v3);
		}
		
		return HullUtil.hull(points);
	}
	
	public static Mesh convertToHull(Mesh mesh) {
		double[] offset = {0,0,0};
		CSG csgHull = convertHull(mesh, offset);
		return convert(csgHull, offset);
	}
}
