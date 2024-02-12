package de.weiltweitbau.database.actions.clashes;

import de.weiltweitbau.geometry.Mesh;

public class ClashVolume {
	private Mesh intersection;
	private double volume;
	private double width;
	private double height;
	
	public ClashVolume(Mesh intersection) {
		this.intersection = intersection;
		
		volume = intersection.computeVolume();
		
		double[] minMax = intersection.getMinMax();
		if(minMax != null) {
			width = Math.max(minMax[3] - minMax[0], minMax[4] - minMax[1]);
			height = minMax[5] - minMax[2];
		}
	}
	
	public double getVolume() {
		return volume;
	}
	
	public double getWidth() {
		return width;
	}
	
	public double getHeight() {
		return height;
	}
	
	public Mesh getIntersection() {
		return intersection;
	}
	
	public void clearIntersection() {
		this.intersection = null;
	}
}
