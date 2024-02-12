package de.weiltweitbau.database.actions.clashes;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bimserver.emf.IdEObject;
import org.bimserver.shared.HashMapVirtualObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.weiltweitbau.geometry.Mesh;
import de.weiltweitbau.geometry.PositionStorage;

public class ClashDetectionResults {
	private final Map<Long, Set<Long>> clashesLeft = new HashMap<>();
	private final Map<Long, Set<Long>> clashesRight = new HashMap<>();
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	private final Map<String, ClashVolume> clashVolumes = new HashMap<>();
	private final Set<Long> openMeshes = new HashSet<>();
	
	public long start = System.nanoTime();
	public int notEnoughData = 0;
	public long totalTimeTriangles = 0;
	public int nrWithoutGeometry = 0;
	public int nrWithOpenGeometry = 0;
	public int nrWithGeometry = 0;
	public long errors = 0;
	public long clashVolumeErrors = 0;
	public long lastDump = 0;
	
	public int totalObjects = 0;
	public int checkedObjects = 0;
	
	private void add(Map<Long, Set<Long>> clashes, HashMapVirtualObject ifcProduct1, HashMapVirtualObject ifcProduct2) {
		Set<Long> clashesWith = clashes.get(ifcProduct1.getOid());

		if (clashesWith == null) {
			clashesWith = new HashSet<Long>();
			clashes.put(ifcProduct1.getOid(), clashesWith);
		}

		clashesWith.add(ifcProduct2.getOid());
	}
	
	public void addLeft(HashMapVirtualObject ifcProduct1, HashMapVirtualObject ifcProduct2) {
		add(clashesLeft, ifcProduct1, ifcProduct2);
	}
	
	public void addRight(HashMapVirtualObject ifcProduct1, HashMapVirtualObject ifcProduct2) {
		add(clashesRight, ifcProduct1, ifcProduct2);
	}
	
	private void add(Map<Long, Set<Long>> clashes, IdEObject ifcProduct1, IdEObject ifcProduct2) {
		Set<Long> clashesWith = clashes.get(ifcProduct1.getOid());

		if (clashesWith == null) {
			clashesWith = new HashSet<Long>();
			clashes.put(ifcProduct1.getOid(), clashesWith);
		}

		clashesWith.add(ifcProduct2.getOid());
	}
	
	public void addLeft(IdEObject ifcProduct1, IdEObject ifcProduct2) {
		add(clashesLeft, ifcProduct1, ifcProduct2);
	}
	
	public void addRight(IdEObject ifcProduct1, IdEObject ifcProduct2) {
		add(clashesRight, ifcProduct1, ifcProduct2);
	}
	
	public void addOpenMesh(Long lOid) {
		openMeshes.add(lOid);
	}
	
	public void addClashVolume(long oidLeft, long oidRight, ClashVolume volume) {
		if(volume == null) {
			return;
		}
		
		clashVolumes.put(getClashVolumeKey(oidLeft, oidRight), volume);
	}
	
	private String getClashVolumeKey(long oidLeft, long oidRight) {
		if(oidLeft < oidRight) {
			return oidLeft + "_" + oidRight;
		}
		
		return oidRight + "_" + oidLeft;
	}
	
	public ObjectNode toJson() {
		ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
		objectNode.set("left", toJson(clashesLeft));
		objectNode.set("right", toJson(clashesRight));
		objectNode.set("clashMeshes", getClashMeshesAsJson());
		objectNode.set("metrics", getMetricsAsJson());
		
		return objectNode;
	}
	
	public ObjectNode getMetricsAsJson() {
		ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
		
		objectNode.put("notEnoughData", notEnoughData);
		objectNode.put("totalTimeTriangles", totalTimeTriangles);
		objectNode.put("nrWithoutGeometry", nrWithoutGeometry);
		objectNode.put("nrWithGeometry", nrWithGeometry);
		objectNode.put("errors", errors);
		objectNode.put("clashVolumeErrors", clashVolumeErrors);
		
		return objectNode;
	}

	private ObjectNode toJson(Map<Long, Set<Long>> clashes) {
		ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
		
		for(long oid : clashes.keySet()) {
			Set<Long> clashesWith = clashes.get(oid);
			
			ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
			
			for(long oid2 : clashesWith) {
				arrayNode.add(oid2);
			}
			
			objectNode.set(Long.toString(oid), arrayNode);
		}
		
		return objectNode;
	}
	
	private ObjectNode getClashVolumesAsJson() {
		ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
		
		for(String strKey : clashVolumes.keySet()) {
			ClashVolume volume = clashVolumes.get(strKey);
			
			ObjectNode volumeNode = OBJECT_MAPPER.createObjectNode();
			volumeNode.put("volume", volume.getVolume());
			volumeNode.set("intersection", getMeshAsJson(volume.getIntersection()));
			
			objectNode.set(strKey, volumeNode);
		}
		
		return objectNode;
	}
	
	private ObjectNode getClashMeshesAsJson() {
		ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
		
		PositionStorage positionStorage = new PositionStorage(0.0001);
		ArrayNode indices = OBJECT_MAPPER.createArrayNode();
		ObjectNode visibleRanges = OBJECT_MAPPER.createObjectNode();
		ObjectNode volumes = OBJECT_MAPPER.createObjectNode();
		
		for(String strKey : clashVolumes.keySet()) {
			ClashVolume volume = clashVolumes.get(strKey);
			Mesh mesh = volume.getIntersection();
			
			ObjectNode visibleRange = OBJECT_MAPPER.createObjectNode();
			visibleRange.put("offset", indices.size());
			visibleRange.put("count", mesh.getTriangleCount() * 3);
			visibleRanges.set(strKey, visibleRange);
			
			volumes.put(strKey, volume.getVolume());
			
			mesh.forEachTriangle((triangle) -> {
				indices.add(positionStorage.put(triangle.getVertex1()));
				indices.add(positionStorage.put(triangle.getVertex2()));
				indices.add(positionStorage.put(triangle.getVertex3()));
			});
		}
		
		double[] _posistions = new double[positionStorage.size() * 3];
		positionStorage.forEach((position, index) -> {
			index *= 3;
			
			_posistions[index + 0] = position[0] * 1000;
			_posistions[index + 1] = position[1] * 1000;
			_posistions[index + 2] = position[2] * 1000;
		});
		
		ArrayNode positions = OBJECT_MAPPER.getDeserializationConfig().getNodeFactory().arrayNode(positionStorage.size());
		for(double pos : _posistions) {
			positions.add(pos);
		}
		
		objectNode.set("indices", indices);
		objectNode.set("positions", positions);
		objectNode.set("volumes", volumes);
		objectNode.set("visibleRanges", visibleRanges);
		
		return objectNode;
	}
	
	private ObjectNode getMeshAsJson(Mesh mesh) {
		if(mesh == null) {
			return null;
		}
		
		ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
		
		ArrayNode vertices = getBufferAsJsonArray(mesh.getVertices(), 1000);
		ArrayNode normals = getBufferAsJsonArray(mesh.getNormals(), 1);
		ArrayNode indices = getBufferAsJsonArray(mesh.getIndices());
		
		objectNode.set("positions", vertices);
		objectNode.set("normals", normals);
		objectNode.set("indices", indices);
		
		return objectNode;
	}
	
	private ArrayNode getBufferAsJsonArray(DoubleBuffer buffer, int scale) {
		ArrayNode array = OBJECT_MAPPER.createArrayNode();
		
		if(buffer == null) {
			return null;
		}
		
		buffer.position(0);
		while(buffer.hasRemaining()) {
			array.add(buffer.get() * scale);
		}
		
		return array;
	}
	
	private ArrayNode getBufferAsJsonArray(IntBuffer buffer) {
		ArrayNode array = OBJECT_MAPPER.createArrayNode();
		
		if(buffer == null) {
			return null;
		}
		
		buffer.position(0);
		while(buffer.hasRemaining()) {
			array.add(buffer.get());
		}
		
		return array;
	}

	public int size() {
		return clashesLeft.size();
	}
}
