package de.weiltweitbau.geometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bimserver.geometry.Vector;
import org.bimserver.models.geometry.GeometryData;
import org.bimserver.models.geometry.GeometryInfo;

public class Mesh {
	private IntBuffer indices;
	private DoubleBuffer vertices;
	private DoubleBuffer normals;
	private double[] transformation;
	private double[] minMax;
	
	private boolean isMeshClosed;
	
	private Triangle[] triangles;
	
	public static Mesh fromGeometryInfo(GeometryInfo geometryInfo) {
		GeometryData data = geometryInfo.getData();

		if (data == null || data.getIndices() == null || data.getIndices().getData() == null) {
			return null;
		}

		IntBuffer indices = getIntBuffer(data.getIndices().getData());
		DoubleBuffer vertices = getDoubleBuffer(data.getVertices().getData());
//		DoubleBuffer normals = getDoubleBuffer(data.getNormals().getData());
		DoubleBuffer transformation = getDoubleBuffer(geometryInfo.getTransformation());
		double[] transformationArray = new double[16];
		for (int i = 0; i < 16; i++) {
			transformationArray[i] = transformation.get();
		}

		return new Mesh(indices, vertices, transformationArray);
	}
	
	public static Mesh fromByteArrays(byte[] _indices, byte[] _vertices) {
		IntBuffer indices = getIntBuffer(_indices);
		DoubleBuffer vertices = getDoubleBuffer(_vertices);

		return new Mesh(indices, vertices, null);
	}
	
	private static DoubleBuffer getDoubleBuffer(byte[] input) {
		ByteBuffer vertexBuffer = ByteBuffer.wrap(input);
		vertexBuffer.order(ByteOrder.LITTLE_ENDIAN);
		DoubleBuffer doubleBuffer = vertexBuffer.asDoubleBuffer();
		doubleBuffer.position(0);
		return doubleBuffer;
	}

	private static IntBuffer getIntBuffer(byte[] input) {
		ByteBuffer indicesBuffer = ByteBuffer.wrap(input);
		indicesBuffer.order(ByteOrder.LITTLE_ENDIAN);
		IntBuffer indicesIntBuffer = indicesBuffer.asIntBuffer();
		return indicesIntBuffer;
	}
	
	public Mesh(IntBuffer indices, DoubleBuffer vertices, double[] transformation) {
		this(indices, vertices, null, transformation);
	}
	
	public Mesh(IntBuffer indices, DoubleBuffer vertices, DoubleBuffer normals, double[] transformation) {
		this.indices = indices;
		this.vertices = vertices;
		this.normals = normals;
		this.transformation = transformation;
		
		populateTriangles();
		this.isMeshClosed = computeIsClosed();
	}
	
	private void populateTriangles() {
		triangles = new Triangle[indices.capacity()/3];
		
		for(int i=0; i<getTriangleCount(); i++) {
			triangles[i] = new Triangle(indices, vertices, i*3, transformation);
		}
	}
	
	public boolean computeIsClosed() {
		PositionStorage store = new PositionStorage(0.00000001);
		
		for(int i=0; i<getTriangleCount(); i++) {
			Triangle triangle = getTriangle(i);
			
			int _i = i*3;
			
			indices.put(_i, store.put(triangle.getVertices()[0]));
			indices.put(_i + 1, store.put(triangle.getVertices()[1]));
			indices.put(_i + 2, store.put(triangle.getVertices()[2]));
		}
		
		vertices = DoubleBuffer.allocate(store.size() * 3);
		store.forEach((position, index) -> {
			index *= 3;
			vertices.put(index, position[0]);
			vertices.put(index + 1, position[1]);
			vertices.put(index + 2, position[2]);
		});
		
		HashMap<Integer, Set<Integer>> startEndMap = new HashMap<>();
		
		for(int i=0; i<indices.capacity(); i+=3) {
			int index0 = indices.get(i);
			int index1 = indices.get(i + 1);
			int index2 = indices.get(i + 2);
			
			addOrRemoveEdge(index0, index1, startEndMap);
			addOrRemoveEdge(index1, index2, startEndMap);
			addOrRemoveEdge(index2, index0, startEndMap);
		}
		
		return startEndMap.isEmpty();
	}
	
	public boolean isClosed() {
		return isMeshClosed;
	}
	
	private void addOrRemoveEdge(int indexStart, int indexEnd, HashMap<Integer, Set<Integer>> startEndMap) {
		Set<Integer> ends1 = startEndMap.get(indexEnd);
		if(ends1 != null && ends1.remove(indexStart)) {
			if(ends1.isEmpty()) {
				startEndMap.remove(indexEnd);
			}
			
			return;
		}
		
		Set<Integer> ends0 = startEndMap.get(indexStart);
		if(ends0 == null) {
			ends0 = new HashSet<>();
			startEndMap.put(indexStart, ends0);
		}
		ends0.add(indexEnd);
	}
	
	public Triangle getTriangle(int index) {
		return triangles[index];
	}
	
	public int getTriangleCount() {
		return triangles.length;
	}
	
	public IntBuffer getIndices() {
		return indices;
	}
	
	public DoubleBuffer getVertices() {
		return vertices;
	}
	
	public DoubleBuffer getNormals() {
		return normals;
	}
	
	public double computeVolume() {
		if(!isClosed()) {
			return 0;
		}
		
		double volume = 0;
		
		for(Triangle triangle : triangles) {
			double[] p1 = triangle.getVertex1();
			double[] p2 = triangle.getVertex2();
			double[] p3 = triangle.getVertex3();
			
			volume += Vector.dot(p1, Vector.crossProduct(p2, p3)) / 6;
		}

		return Math.abs(volume);
	}
	
	public double computeArea() {
		double area = 0;
		
		for(Triangle triangle : triangles) {
			double[] p1 = triangle.getVertex1();
			double[] p2 = triangle.getVertex2();
			double[] p3 = triangle.getVertex3();

			double[] cross = Vector.crossProduct(Vector.subtract(p2, p1), Vector.subtract(p3, p1));

			area += Vector.length(cross) / 2;
		}

		return Math.abs(area);
	}
	
	public void forEachTriangle(Consumer<Triangle> c) {
		for(Triangle triangle : triangles) {
			c.accept(triangle);
		}
	}
	
	public boolean forSomeTriangles(Function<Triangle, Boolean> f) {
		for(Triangle triangle : triangles) {
			if(f.apply(triangle)) {
				return true;
			}
		}
		
		return false;
	}
	
	public void setMinMax(double[] minMax) {
		this.minMax = minMax;
	}
	
	public double[] getMinMax() {
		return this.minMax;
	}
	
	@Override
	public String toString() {
		StringBuilder asString = new StringBuilder(super.toString());
		
		asString.append("\npositions: ");
		
		vertices.position(0);
		do {
			asString.append(vertices.get()).append(", ");
			asString.append(vertices.get()).append(", ");
			asString.append(vertices.get()).append("\n");
		} while (vertices.hasRemaining());
		
		asString.append("\nindices: ");
		
		indices.position(0);
		do {
			asString.append(indices.get()).append(", ");
			asString.append(indices.get()).append(", ");
			asString.append(indices.get()).append("\n");
		} while (indices.hasRemaining());
		
		return asString.toString();
	}
}
