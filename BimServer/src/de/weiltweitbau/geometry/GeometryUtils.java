package de.weiltweitbau.geometry;

public class GeometryUtils {
	public static boolean almostEquals(double left, double right, double epsilon) {
		double div = Math.abs(1 - (left / right));

		return div < epsilon;
	}
}
