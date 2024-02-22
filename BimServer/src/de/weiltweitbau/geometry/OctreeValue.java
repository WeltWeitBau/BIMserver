package de.weiltweitbau.geometry;

public class OctreeValue implements Comparable<OctreeValue> {
	public final double[] minmax;
	
	public OctreeValue(double[] minmax) {
		this.minmax = minmax;
	}
	
	@Override
	public int compareTo(OctreeValue o) {
		for(int i=0; i<6; i++) {
			int res = Double.compare(minmax[i], o.minmax[i]);
			
			if(res != 0) {
				return res;
			}
		}
		
		return 0;
	}
}
