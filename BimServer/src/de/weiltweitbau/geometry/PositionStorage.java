package de.weiltweitbau.geometry;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.BiConsumer;

public class PositionStorage {
	double epsilon;
	
	int index;
	
	TreeMap<double[], Integer> positions;
	
	public PositionStorage(double epsilon) {
		this.epsilon = epsilon;
		
		this.index = 0;
		
		positions = new TreeMap<>(new Comparator<double[]>() {
			@Override
			public int compare(double[] o1, double[] o2) {
				for(int i=0; i<3; i++) {
					int compare = compare(o1[i], o2[i]);

					if(compare != 0) {
						return compare;
					}
				}
				
				return 0;
			}
			
			private int compare(double d1, double d2) {
				double delta = d1 - d2;
				
				if(delta < -epsilon) {
					return -1;
				}
				
				if(delta > epsilon) {
					return 1;
				}
				
				return 0;
			}
		});
	}
	
	public int put(double[] position) {
		Integer foundIndex = positions.get(position);
		
		if(foundIndex != null) {
			return foundIndex.intValue();
		}
		
		positions.put(position, index);
		
		return index++;
	}
	
	public int size() {
		return positions.size();
	}
	
	public void forEach(BiConsumer<double[], Integer> f) {
		for(Entry<double[], Integer> entry : positions.entrySet()) {
			f.accept(entry.getKey(), entry.getValue());
		}
	}
}
