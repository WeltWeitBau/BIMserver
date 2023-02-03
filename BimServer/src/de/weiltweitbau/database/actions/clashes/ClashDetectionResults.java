package de.weiltweitbau.database.actions.clashes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bimserver.emf.IdEObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ClashDetectionResults {
	private final Map<Long, Set<Long>> clashesLeft = new HashMap<Long, Set<Long>>();
	private final Map<Long, Set<Long>> clashesRight = new HashMap<Long, Set<Long>>();
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	
	public long start = System.nanoTime();
	public int notEnoughData = 0;
	public long totalTimeTriangles = 0;
	public int nrWithoutGeometry = 0;
	public int nrWithGeometry = 0;
	public long lastDump = 0;
	
	public int totalObjects = 0;
	public int checkedObjects = 0;
	
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
	
	public ObjectNode toJson() {
		ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
		objectNode.set("left", toJson(clashesLeft));
		objectNode.set("right", toJson(clashesRight));
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

	public int size() {
		return clashesLeft.size();
	}
}
