package de.weiltweitbau.database.actions.clashes;

public class ClashDetectorRules {
	private double epsilon = 0.00001;
	private boolean skipDefaultRules;
	private String[] onlyCheckWithOwnType;
	private Combinations[] typesToIgnore;
	private Combinations[] typesToCheck;
	
	public ClashDetectorRules() {
		//
	}
	
	public ClashDetectorRules(boolean skipDefaultRules, String[] onlyCheckWithOwnType, Combinations[] typesToIgnore) {
		this.skipDefaultRules = skipDefaultRules;
		this.onlyCheckWithOwnType = onlyCheckWithOwnType;
		this.typesToIgnore = typesToIgnore;
	}

	public boolean isSkipDefaultRules() {
		return skipDefaultRules;
	}

	public void setSkipDefaultRules(boolean skipDefaultRules) {
		this.skipDefaultRules = skipDefaultRules;
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

	public static class Combinations {
		private String type;
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

		public String[] getCombinedWith() {
			return combinedWith;
		}

		public void setCombinedWith(String[] combinedWith) {
			this.combinedWith = combinedWith;
		}
	}
}
