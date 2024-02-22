package de.weiltweitbau.geometry;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

public class Octree <T extends OctreeValue> {
	public final double minX;
	public final double minY;
	public final double minZ;
	
	public final double maxX;
	public final double maxY;
	public final double maxZ;
	
	public final double midX;
	public final double midY;
	public final double midZ;
	
	public final Octree<T> parent;
	
	private Octree<T> upperBackLeftChild;
	private Octree<T> upperBackRightChild;
	private Octree<T> upperFrontLeftChild;
	private Octree<T> upperFrontRightChild;
	private Octree<T> lowerBackLeftChild;
	private Octree<T> lowerBackRightChild;
	private Octree<T> lowerFrontLeftChild;
	private Octree<T> lowerFrontRightChild;
	
	private Set<T> values;
	
	public Octree(double[] minmax) {
		this(minmax, null);
	}

	public Octree(double[] minmax, Octree<T> parent) {
		this.minX = minmax[0];
		this.minY = minmax[1];
		this.minZ = minmax[2];
		
		this.maxX = minmax[3];
		this.maxY = minmax[4];
		this.maxZ = minmax[5];
		
		this.midX = (maxX + minX)/2;
		this.midY = (maxY + minY)/2;
		this.midZ = (maxZ + minZ)/2;
		
		this.parent = parent;
	}
	
	public Octree<T> getUpperBackLeftChild() {
		if(upperBackLeftChild == null) {
			upperBackLeftChild = new Octree<T>(getBoundsForQuadrant(NodeIndex.UPPER_BACK_LEFT), this);
		}
		
		return upperBackLeftChild;
	}

	public Octree<T> getUpperBackRightChild() {
		if(upperBackRightChild == null) {
			upperBackRightChild = new Octree<T>(getBoundsForQuadrant(NodeIndex.UPPER_BACK_RIGHT), this);
		}
		
		return upperBackRightChild;
	}

	public Octree<T> getUpperFrontLeftChild() {
		if(upperFrontLeftChild == null) {
			upperFrontLeftChild = new Octree<T>(getBoundsForQuadrant(NodeIndex.UPPER_FRONT_LEFT), this);
		}
		
		return upperFrontLeftChild;
	}

	public Octree<T> getUpperFrontRightChild() {
		if(upperFrontRightChild == null) {
			upperFrontRightChild = new Octree<T>(getBoundsForQuadrant(NodeIndex.UPPER_FRONT_RIGHT), this);
		}
		
		return upperFrontRightChild;
	}

	public Octree<T> getLowerBackLeftChild() {
		if(lowerBackLeftChild == null) {
			lowerBackLeftChild = new Octree<T>(getBoundsForQuadrant(NodeIndex.LOWER_BACK_LEFT), this);
		}
		
		return lowerBackLeftChild;
	}

	public Octree<T> getLowerBackRightChild() {
		if(lowerBackRightChild == null) {
			lowerBackRightChild = new Octree<T>(getBoundsForQuadrant(NodeIndex.LOWER_BACK_RIGHT), this);
		}
		
		return lowerBackRightChild;
	}

	public Octree<T> getLowerFrontLeftChild() {
		if(lowerFrontLeftChild == null) {
			lowerFrontLeftChild = new Octree<T>(getBoundsForQuadrant(NodeIndex.LOWER_FRONT_LEFT), this);
		}
		
		return lowerFrontLeftChild;
	}

	public Octree<T> getLowerFrontRightChild() {
		if(lowerFrontRightChild == null) {
			lowerFrontRightChild = new Octree<T>(getBoundsForQuadrant(NodeIndex.LOWER_FRONT_RIGHT), this);
		}
		
		return lowerFrontRightChild;
	}
	
//	public Octree<T> getChild(NodeIndex index) {
//		switch (index) {
//		case UPPER_FRONT_LEFT:
//			return getUpperFrontLeftChild();
//		case UPPER_FRONT_RIGHT:
//			return getUpperFrontRightChild();
//		case UPPER_BACK_RIGHT:
//			return getUpperBackRightChild();
//		case UPPER_BACK_LEFT:
//			return getUpperBackLeftChild();
//		case LOWER_FRONT_LEFT:
//			return getLowerFrontLeftChild();
//		case LOWER_FRONT_RIGHT:
//			return getLowerFrontRightChild();
//		case LOWER_BACK_RIGHT:
//			return getLowerBackRightChild();
//		case LOWER_BACK_LEFT:
//			return getLowerBackLeftChild();
//		default:
//			return null;
//		}
//	}
	
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
	
	public void setValue(T value) throws OctreeException {
		Octree<T> child = getQuadrant(value.minmax);
		if(child == null) {
			getValues().add(value);
		} else {
			child.setValue(value);
		}
	}
	
	public Set<T> getValues() {
		if(values == null) {
			values = new TreeSet<>();
		}
		
		return values;
	}
	
	private Octree<T> getQuadrant(double[] minmax) throws OctreeException {
		if(minmax[0] < minX || minmax[1] < minY || minmax[2] < minZ) {
			throw new OctreeException("Minimum bounds dont fit!");
		}
		
		if(minmax[3] > maxX || minmax[4] > maxY || minmax[5] > maxZ) {
			throw new OctreeException("Maximum bounds dont fit!");
		}
		
		if(isLowerQuadrant(minmax)) {
			if(isFrontQuadrant(minmax)) {
				if(isLeftQuadrant(minmax)) {
					return getLowerFrontLeftChild();
				} else if(isRightQuadrant(minmax)) {
					return getLowerFrontRightChild();
				}
			} else if(isBackQuadrant(minmax)) {
				if(isLeftQuadrant(minmax)) {
					return getLowerBackLeftChild();
				} else if(isRightQuadrant(minmax)) {
					return getLowerBackRightChild();
				}
			}
		} else if(isUpperQuadrant(minmax)) {
			if(isFrontQuadrant(minmax)) {
				if(isLeftQuadrant(minmax)) {
					return getUpperFrontLeftChild();
				} else if(isRightQuadrant(minmax)) {
					return getUpperFrontRightChild();
				}
			} else if(isBackQuadrant(minmax)) {
				if(isLeftQuadrant(minmax)) {
					return getUpperBackLeftChild();
				} else if(isRightQuadrant(minmax)) {
					return getUpperBackRightChild();
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
	
	public boolean clashes(double[] minmax) {
		return (maxX > minmax[0]
				&& minX < minmax[3]
				&& maxY > minmax[1]
				&& minY < minmax[4]
				&& maxZ > minmax[2]
				&& minZ < minmax[5]);
	}
	
	public void traverseUpAndBreadthFirstDown(ILambdaObject<Octree<T>, Boolean> lambdaObj) {
		traverseUp(lambdaObj);
		traverseBreadthFirst(lambdaObj);
	}
	
	public void traverseUp(ILambdaObject<Octree<T>, Boolean> lambdaObj) {
		Octree<T> parent = this.parent;
		
		while(parent != null) {
			if(!lambdaObj.excute(parent)) {
				return;
			}
			
			parent = parent.parent;
		}
	}
	
	public void traverseBreadthFirst(ILambdaObject<Octree<T>, Boolean> lambdaObj) {
		Deque<Octree<T>> nextNodes = new LinkedList<>();
		
		nextNodes.add(this);
		
		while(!nextNodes.isEmpty()) {
			Octree<T> node = nextNodes.pollFirst();
			
			if(!lambdaObj.excute(node)) {
				continue;
			}
			
			addChildForTraversal(node.upperBackLeftChild, nextNodes);
			addChildForTraversal(node.upperBackRightChild, nextNodes);
			addChildForTraversal(node.upperFrontLeftChild, nextNodes);
			addChildForTraversal(node.upperFrontRightChild, nextNodes);
			addChildForTraversal(node.lowerBackLeftChild, nextNodes);
			addChildForTraversal(node.lowerBackRightChild, nextNodes);
			addChildForTraversal(node.lowerFrontLeftChild, nextNodes);
			addChildForTraversal(node.lowerFrontRightChild, nextNodes);
		}
	}
	
	private void addChildForTraversal(Octree<T> child, Deque<Octree<T>> nextNodes) {
		if(child == null) {
			return;
		}
		
		nextNodes.add(child);
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
}
