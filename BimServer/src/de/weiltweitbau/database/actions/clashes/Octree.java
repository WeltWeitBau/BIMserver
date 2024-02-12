package de.weiltweitbau.database.actions.clashes;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import org.bimserver.shared.HashMapVirtualObject;
import org.bimserver.shared.HashMapWrappedVirtualObject;

import de.weiltweitbau.database.actions.clashes.ClashDetector.GeometryModel;

public class Octree {
	public static final int UPPER_BACK_LEFT = 0;
	public static final int UPPER_BACK_RIGHT = 1;
	public static final int UPPER_FRONT_LEFT = 2;
	public static final int UPPER_FRONT_RIGHT = 3;
	public static final int LOWER_BACK_LEFT = 4;
	public static final int LOWER_BACK_RIGHT = 5;
	public static final int LOWER_FRONT_LEFT = 6;
	public static final int LOWER_FRONT_RIGHT = 7;
	
	public final double minX;
	public final double minY;
	public final double minZ;
	
	public final double maxX;
	public final double maxY;
	public final double maxZ;
	
	public final double midX;
	public final double midY;
	public final double midZ;
	
	public final Octree parent;
	
	private final Octree[] children;
	
	private Set<OctreeValue> values;
	
	public Octree(double[] minmax) {
		this(minmax, null);
	}

	public Octree(double[] minmax, Octree parent) {
		this.minX = minmax[0];
		this.minY = minmax[1];
		this.minZ = minmax[2];
		
		this.maxX = minmax[3];
		this.maxY = minmax[4];
		this.maxZ = minmax[5];
		
		this.midX = (maxX + minX)/2;
		this.midY = (maxY + minY)/2;
		this.midZ = (maxZ + minZ)/2;
		
		this.children = new Octree[8];
		this.parent = parent;
	}
	
	public Octree getChild(NodeIndex index) {
		if(children[index.value] == null) {
			children[index.value] = new Octree(getBoundsForQuadrant(index), this);
		}
		
		return children[index.value];
	}
	
	private double[] getBoundsForQuadrant(NodeIndex index) {
		switch (index) {
		case UPPER_FRONT_LEFT:
			return new double[] {minX, minY, midZ, midX, midY , maxZ};
		case UPPER_FRONT_RIGHT:
			return new double[] {midX, minY, midZ, maxX, midY , maxZ};
		case UPPER_BACK_RIGHT:
			return new double[] {midX, midY, midZ, maxX, maxY , maxZ};
		case UPPER_BACK_LEFT:
			return new double[] {minX, midY, midZ, midX, maxY , maxZ};
		case LOWER_FRONT_LEFT:
			return new double[] {minX, minY, minZ, midX, midY , midZ};
		case LOWER_FRONT_RIGHT:
			return new double[] {midX, minY, minZ, maxX, midY , midZ};
		case LOWER_BACK_RIGHT:
			return new double[] {midX, midY, minZ, maxX, maxY , midZ};
		case LOWER_BACK_LEFT:
			return new double[] {minX, midY, minZ, midX, maxY , midZ};
		default:
			return null;
		}
	}
	
	public void setValue(OctreeValue value) throws OctreeException {
		NodeIndex index = getQuadrantIndex(value.minmax);
		
		if(index == null) {
			getValues().add(value);
		} else {
			getChild(index).setValue(value);
		}
	}
	
	public Set<OctreeValue> getValues() {
		if(values == null) {
			values = new TreeSet<>();
		}
		
		return values;
	}
	
	private double[] getMinMax(HashMapWrappedVirtualObject bounds) {
		HashMapWrappedVirtualObject min = (HashMapWrappedVirtualObject) bounds.get("min");
		HashMapWrappedVirtualObject max = (HashMapWrappedVirtualObject) bounds.get("max");
		
		return new double[] {
				(double) min.get("x"),
				(double) min.get("y"),
				(double) min.get("z"),
				(double) max.get("x"),
				(double) max.get("y"),
				(double) max.get("z")};
	}
	
	public void populateOctreeHM(GeometryModel model, boolean isModel1, ClashDetectionResults clashDetectionResults) throws OctreeException {
		for (HashMapVirtualObject ifcProduct : model.getProducts()) {
			if(!ifcProduct.has("geometry")) {
				continue;
			}
			
			HashMapVirtualObject geoInfo = model.getGeoInfo((Long) ifcProduct.get("geometry"));
			
			if(geoInfo == null) {
				continue;
			}
			
			clashDetectionResults.totalObjects++;
			
			double[] minmax = getMinMax((HashMapWrappedVirtualObject) geoInfo.get("bounds"));
			for(int i=0; i<minmax.length; i++) {
				minmax[i] *= model.getMultiplierToM();
			}
			
			setValue(new OctreeValue(ifcProduct, geoInfo, isModel1, minmax));
		}
	}
	
	private NodeIndex getQuadrantIndex(double[] minmax) throws OctreeException {
		if(minmax[0] < minX || minmax[1] < minY || minmax[2] < minZ) {
			throw new OctreeException("Minimum bounds dont fit!");
		}
		
		if(minmax[3] > maxX || minmax[4] > maxY || minmax[5] > maxZ) {
			throw new OctreeException("Maximum bounds dont fit!");
		}
		
		if(isLowerQuadrant(minmax)) {
			if(isFrontQuadrant(minmax)) {
				if(isLeftQuadrant(minmax)) {
					return NodeIndex.LOWER_FRONT_LEFT;
				} else if(isRightQuadrant(minmax)) {
					return NodeIndex.LOWER_FRONT_RIGHT;
				}
			} else if(isBackQuadrant(minmax)) {
				if(isLeftQuadrant(minmax)) {
					return NodeIndex.LOWER_BACK_LEFT;
				} else if(isRightQuadrant(minmax)) {
					return NodeIndex.LOWER_BACK_RIGHT;
				}
			}
		} else if(isUpperQuadrant(minmax)) {
			if(isFrontQuadrant(minmax)) {
				if(isLeftQuadrant(minmax)) {
					return NodeIndex.UPPER_FRONT_LEFT;
				} else if(isRightQuadrant(minmax)) {
					return NodeIndex.UPPER_FRONT_RIGHT;
				}
			} else if(isBackQuadrant(minmax)) {
				if(isLeftQuadrant(minmax)) {
					return NodeIndex.UPPER_BACK_LEFT;
				} else if(isRightQuadrant(minmax)) {
					return NodeIndex.UPPER_BACK_RIGHT;
				}
			}
		}
		
		return null;
	}
	
	private boolean isLowerQuadrant(double[] minmax) {
		return minmax[2] < midZ && minmax[5] < midZ;
	}
	
	private boolean isUpperQuadrant(double[] minmax) {
		return minmax[2] > midZ && minmax[5] > midZ;
	}
	
	private boolean isFrontQuadrant(double[] minmax) {
		return minmax[1] < midY && minmax[4] < midY;
	}
	
	private boolean isBackQuadrant(double[] minmax) {
		return minmax[1] > midY && minmax[4] > midY;
	}
	
	private boolean isLeftQuadrant(double[] minmax) {
		return minmax[0] < midX && minmax[3] < midX;
	}
	
	private boolean isRightQuadrant(double[] minmax) {
		return minmax[0] > midX && minmax[3] > midX;
	}
	
	public boolean fits(double[] minmax) {
		if(minmax[0] >= minX && minmax[3] <= maxX
				&& minmax[1] >= minY && minmax[4] <= maxY
				&& minmax[2] >= minZ && minmax[5] <= maxZ) {
			return true;
		}
		
		return false;
	}
	
	public void traverseUpAndBreadthFirstDown(ILambdaObject<Octree, Boolean> lambdaObj) {
		traverseUp(lambdaObj);
		traverseBreadthFirst(lambdaObj);
	}
	
	public void traverseUp(ILambdaObject<Octree, Boolean> lambdaObj) {
		Octree parent = this.parent;
		
		while(parent != null) {
			if(!lambdaObj.excute(parent)) {
				return;
			}
			
			parent = parent.parent;
		}
	}
	
	public void traverseBreadthFirst(ILambdaObject<Octree, Boolean> lambdaObj) {
		Deque<Octree> nextNodes = new LinkedList<>();
		
		nextNodes.add(this);
		
		while(!nextNodes.isEmpty()) {
			Octree node = nextNodes.pollFirst();
			
			if(!lambdaObj.excute(node)) {
				continue;
			}
			
			for(Octree child : node.children) {
				if(child == null) {
					continue;
				}
				
				nextNodes.add(child);
			}
		}
	}
	
	public interface ILambdaObject <T, R> {
		public R excute(T obj);
	}
	
	public enum NodeIndex {
		UPPER_BACK_LEFT(0),
		UPPER_BACK_RIGHT(1),
		UPPER_FRONT_LEFT(2),
		UPPER_FRONT_RIGHT(3),
		LOWER_BACK_LEFT(4),
		LOWER_BACK_RIGHT(5),
		LOWER_FRONT_LEFT(6),
		LOWER_FRONT_RIGHT(7);
		
		final int value;
		
		private NodeIndex(int index) {
			this.value = index;
		}
	}
	
	public static class OctreeException extends Exception {
		private static final long serialVersionUID = -2577292202931680547L;

		public OctreeException(String strMessage) {
			super(strMessage);
		}
	}
	
	public static class OctreeValue implements Comparable<OctreeValue> {
		public final HashMapVirtualObject ifcProduct;
		public final HashMapVirtualObject geometryInfo;
		
		public final boolean isModel1;
		public final double[] minmax;
		
		public OctreeValue(HashMapVirtualObject ifcProduct, HashMapVirtualObject geometryInfo, boolean isModel1, double[] minmax) {
			this.ifcProduct = ifcProduct;
			this.isModel1 = isModel1;
			this.geometryInfo = geometryInfo;
			this.minmax = minmax;
		}

		@Override
		public int compareTo(OctreeValue object) {
			if(isModel1 != object.isModel1) {
				return isModel1 ? 1 : -1;
			}
			
			return Long.compare(ifcProduct.getOid(), object.ifcProduct.getOid());
		}
	}
}
