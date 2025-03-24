package de.weiltweitbau.geometry;

public class GeometryUtils {
	public static final double EPSILON = 0.0001;
	
	public static boolean almostEquals(double left, double right, double epsilon) {
		double div = Math.abs(1 - (left / right));

		return div < epsilon;
	}
	
	public static boolean greaterThanOrAlmostEqual(double left, double right, double epsilon) {
		return left > right || almostEquals(left, right, epsilon);
	}
	
	public static boolean lessThanOrAlmostEqual(double left, double right, double epsilon) {
		return left < right || almostEquals(left, right, epsilon);
	}
	
	public static boolean greaterThanOrAlmostEqual(double left, double right) {
		return greaterThanOrAlmostEqual(left, right, EPSILON);
	}
	
	public static boolean lessThanOrAlmostEqual(double left, double right) {
		return lessThanOrAlmostEqual(left, right, EPSILON);
	}
}
