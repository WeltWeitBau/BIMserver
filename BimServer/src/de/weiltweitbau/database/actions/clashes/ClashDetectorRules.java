package de.weiltweitbau.database.actions.clashes;

public class ClashDetectorRules {
	private double epsilon = 0.00001;
	private double minClashVolume = -1;
	private double minClashHorizontal = -1;
	private double minClashVertical = -1;
	private int volumeTriangleThreshold = 10000;
	private boolean skipDefaultRules;
	private boolean computeVolumes;
	private String property;
	private String propertySet;
	private String[] onlyCheckWithOwnType;
	private Combinations[] typesToIgnore;
	private Combinations[] typesToCheck;
	
	public ClashDetectorRules() {
		// Default constructor
	}

	public boolean isSkipDefaultRules() {
		return skipDefaultRules;
	}

	public String getProperty() {
		return property;
	}
	
	public boolean hasProperty() {
		return property != null && !property.isEmpty();
	}

	public void setProperty(String property) {
		this.property = property;
	}
	
	public boolean matchesProperty(String value) {
		if(value == null || value.isEmpty()) {
			return false;
		}
		
		if(property == null || property.isEmpty()) {
			return false;
		}
		
		return property.equals(value);
	}
	
	public String getPropertySet() {
		return propertySet;
	}
	
	public boolean hasPropertySet() {
		return propertySet != null && !propertySet.isEmpty();
	}

	public void setPropertySet(String propertySet) {
		this.propertySet = propertySet;
	}
	
	public boolean matchesPropertySet(String value) {
		if(value == null || value.isEmpty()) {
			return false;
		}
		
		if(propertySet == null || propertySet.isEmpty()) {
			return true;
		}
		
		return propertySet.equals(value);
	}

	public void setSkipDefaultRules(boolean skipDefaultRules) {
		this.skipDefaultRules = skipDefaultRules;
	}
	
	public boolean isComputeVolumes() {
		return computeVolumes;
	}

	public void setComputeVolumes(boolean computeVolumes) {
		this.computeVolumes = computeVolumes;
	}

	public String[] getOnlyCheckWithOwnType() {
		if(onlyCheckWithOwnType == null) {
			onlyCheckWithOwnType = new String[0];
		}
		
		return onlyCheckWithOwnType;
	}

	public void setOnlyCheckWithOwnType(String[] onlyCheckWithOwnType) {
		this.onlyCheckWithOwnType = onlyCheckWithOwnType;
	}
	
	public Combinations[] getTypesToIgnore() {
		if(typesToIgnore == null) {
			typesToIgnore = new Combinations[0];
		}
		
		return typesToIgnore;
	}

	public void setTypesToIgnore(Combinations[] typesToIgnore) {
		this.typesToIgnore = typesToIgnore;
	}

	public double getEpsilon() {
		return epsilon;
	}

	public void setEpsilon(double epsilon) {
		this.epsilon = epsilon;
	}
	
	public double getMinClashVolume() {
		return minClashVolume;
	}

	public void setMinClashVolume(double minClashVolume) {
		this.minClashVolume = minClashVolume;
	}

	public double getMinClashHorizontal() {
		return minClashHorizontal;
	}

	public void setMinClashHorizontal(double minClashHorizontal) {
		this.minClashHorizontal = minClashHorizontal;
	}

	public double getMinClashVertical() {
		return minClashVertical;
	}

	public void setMinClashVertical(double minClashVertical) {
		this.minClashVertical = minClashVertical;
	}

	public Combinations[] getTypesToCheck() {
		if(typesToCheck == null) {
			typesToCheck = new Combinations[0];
		}
		
		return typesToCheck;
	}

	public void setTypesToCheck(Combinations[] typesToCheck) {
		this.typesToCheck = typesToCheck;
	}
	
	public void setName(String strValue) {}
	public String getName() {return "";}
	
	public void setId(String strValue) {}
	public String getId() {return "";}
	
	public int getVolumeTriangleThreshold() {
		return volumeTriangleThreshold;
	}

	public void setVolumeTriangleThreshold(int volumeTriangleThreshold) {
		this.volumeTriangleThreshold = volumeTriangleThreshold;
	}

	public static class Combinations {
		private String type;
		private String id;
		private String[] combinedWith;
		
		public Combinations() {
			
		}
		
		public Combinations(String type,  String[] typesToIgnore) {
			this.type = type;
			this.combinedWith = typesToIgnore;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}
		
		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String[] getCombinedWith() {
			return combinedWith;
		}

		public void setCombinedWith(String[] combinedWith) {
			this.combinedWith = combinedWith;
		}
	}
}
