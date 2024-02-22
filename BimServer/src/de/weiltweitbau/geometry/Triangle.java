package de.weiltweitbau.geometry;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import org.bimserver.geometry.Matrix;
import org.bimserver.geometry.Vector;

/*
 * 
 * Most of the code has been ported from http://webee.technion.ac.il/labs/cgm/Computer-Graphics-Multimedia/Software/TriangleIntersection/code.cpp
 * 
 */
public class Triangle {
	private double[] vertex1;
	private double[] vertex2;
	private double[] vertex3;
	
	private double[] minMax;
	
	public Triangle(double[] vertex1, double[] vertex2, double[] vertex3) {
		this.vertex1 = vertex1;
		this.vertex2 = vertex2;
		this.vertex3 = vertex3;
	}

	public Triangle(IntBuffer indices, DoubleBuffer vertices, int i, double[] transformation) {
		int index1 = indices.get(i) * 3;
		int index2 = indices.get(i + 1) * 3;
		int index3 = indices.get(i + 2) * 3;
			
		try {
			if (transformation == null) {
				vertex1 = new double[] { vertices.get(index1), vertices.get(index1 + 1), vertices.get(index1 + 2),
						1.0 };
				vertex2 = new double[] { vertices.get(index2), vertices.get(index2 + 1), vertices.get(index2 + 2),
						1.0 };
				vertex3 = new double[] { vertices.get(index3), vertices.get(index3 + 1), vertices.get(index3 + 2),
						1.0 };
			} else {
				vertex1 = Matrix.multiplyV(transformation,
						new double[] { vertices.get(index1), vertices.get(index1 + 1), vertices.get(index1 + 2), 1.0 });
				vertex2 = Matrix.multiplyV(transformation,
						new double[] { vertices.get(index2), vertices.get(index2 + 1), vertices.get(index2 + 2), 1.0 });
				vertex3 = Matrix.multiplyV(transformation,
						new double[] { vertices.get(index3), vertices.get(index3 + 1), vertices.get(index3 + 2), 1.0 });
			} 
		} catch (Exception e) {
//			System.out.println(indices.);
			throw e;
		}
	}
	
	public double[] getMinMax() {
		if(minMax != null) {
			return minMax;
		}
		
		minMax = new double[] {
				min(getVertex1()[0], getVertex2()[0], getVertex3()[0]),
				min(getVertex1()[1], getVertex2()[1], getVertex3()[1]),
				min(getVertex1()[2], getVertex2()[2], getVertex3()[2]),
				max(getVertex1()[0], getVertex2()[0], getVertex3()[0]),
				max(getVertex1()[1], getVertex2()[1], getVertex3()[1]),
				max(getVertex1()[2], getVertex2()[2], getVertex3()[2]),
		}; 
		
		return minMax;
	}
	
	private double min(double d1, double d2, double d3) {
		return Math.min(Math.min(d1, d2), d3);
	}
	
	private double max(double d1, double d2, double d3) {
		return Math.max(Math.max(d1, d2), d3);
	}

	public double[][] getVertices() {
		return new double[][] { vertex1, vertex2, vertex3 };
	}
	
	public double[] getVertex1() {
		return vertex1;
	}
	
	public double[] getVertex2() {
		return vertex2;
	}
	
	public double[] getVertex3() {
		return vertex3;
	}
	
	public void print() {
		System.out.print("Polyline({Point({");
		System.out.print(vertex1[0]);
		System.out.print(",");
		System.out.print(vertex1[1]);
		System.out.print(",");
		System.out.print(vertex1[2]);
		System.out.print("}),Point({");
		System.out.print(vertex2[0]);
		System.out.print(",");
		System.out.print(vertex2[1]);
		System.out.print(",");
		System.out.print(vertex2[2]);
		System.out.print("}),Point({");
		System.out.print(vertex3[0]);
		System.out.print(",");
		System.out.print(vertex3[1]);
		System.out.print(",");
		System.out.print(vertex3[2]);
		System.out.print("}),Point({");
		System.out.print(vertex1[0]);
		System.out.print(",");
		System.out.print(vertex1[1]);
		System.out.print(",");
		System.out.print(vertex1[2]);
		System.out.println("})})");
	}

	public double[] NEWCOMPUTE_INTERVALS(double VV0, double VV1, double VV2, double D0, double D1, double D2,
			double D0D1, double D0D2) {
		double A;
		double B;
		double C;
		double X0;
		double X1;
		if (D0D1 > 0.0f) {
			/* here we know that D0D2<=0.0 */
			/* that is D0, D1 are on the same side, D2 on the other or on the plane */
			A = VV2;
			B = (VV0 - VV2) * D2;
			C = (VV1 - VV2) * D2;
			X0 = D2 - D0;
			X1 = D2 - D1;
		} else if (D0D2 > 0.0f) {
			/* here we know that d0d1<=0.0 */
			A = VV1;
			B = (VV0 - VV1) * D1;
			C = (VV2 - VV1) * D1;
			X0 = D1 - D0;
			X1 = D1 - D2;
		} else if (D1 * D2 > 0.0f || D0 != 0.0f) {
			/* here we know that d0d1<=0.0 or that D0!=0.0 */
			A = VV0;
			B = (VV1 - VV0) * D0;
			C = (VV2 - VV0) * D0;
			X0 = D0 - D1;
			X1 = D0 - D2;
		} else if (D1 != 0.0f) {
			A = VV1;
			B = (VV0 - VV1) * D1;
			C = (VV2 - VV1) * D1;
			X0 = D1 - D0;
			X1 = D1 - D2;
		} else if (D2 != 0.0f) {
			A = VV2;
			B = (VV0 - VV2) * D2;
			C = (VV1 - VV2) * D2;
			X0 = D2 - D0;
			X1 = D2 - D1;
		} else {
			/* triangles are coplanar */
			return null;
		}
		return new double[] { A, B, C, X0, X1 };
	}

	private boolean POINT_IN_TRI(double V0[], double U0[], double U1[], double U2[], int i0, int i1) {
		double a, b, c, d0, d1, d2;
		/* is T1 completly inside T2? */
		/* check if V0 is inside tri(U0,U1,U2) */
		a = U1[i1] - U0[i1];
		b = -(U1[i0] - U0[i0]);
		c = -a * U0[i0] - b * U0[i1];
		d0 = a * V0[i0] + b * V0[i1] + c;

		a = U2[i1] - U1[i1];
		b = -(U2[i0] - U1[i0]);
		c = -a * U1[i0] - b * U1[i1];
		d1 = a * V0[i0] + b * V0[i1] + c;

		a = U0[i1] - U2[i1];
		b = -(U0[i0] - U2[i0]);
		c = -a * U2[i0] - b * U2[i1];
		d2 = a * V0[i0] + b * V0[i1] + c;
		if (d0 * d1 > 0.0) {
			if (d0 * d2 > 0.0)
				return true;
		}
		return false;
	}

	public int coplanar_tri_tri(double N[], double V0[], double V1[], double V2[], double U0[], double U1[],
			double U2[]) {
		double A[] = new double[3];
		short i0, i1;
		/* first project onto an axis-aligned plane, that maximizes the area */
		/* of the triangles, compute indices: i0,i1. */
		A[0] = Math.abs(N[0]);
		A[1] = Math.abs(N[1]);
		A[2] = Math.abs(N[2]);
		if (A[0] > A[1]) {
			if (A[0] > A[2]) {
				i0 = 1; /* A[0] is greatest */
				i1 = 2;
			} else {
				i0 = 0; /* A[2] is greatest */
				i1 = 1;
			}
		} else /* A[0]<=A[1] */
		{
			if (A[2] > A[1]) {
				i0 = 0; /* A[2] is greatest */
				i1 = 1;
			} else {
				i0 = 0; /* A[1] is greatest */
				i1 = 2;
			}
		}

		/* test all edges of triangle 1 against the edges of triangle 2 */

		if (EDGE_AGAINST_TRI_EDGES(V0, V1, U0, U1, U2, i0, i1)) {
			return 1;
		}
		if (EDGE_AGAINST_TRI_EDGES(V1, V2, U0, U1, U2, i0, i1)) {
			return 1;
		}
		if (EDGE_AGAINST_TRI_EDGES(V2, V0, U0, U1, U2, i0, i1)) {
			return 1;
		}

		/* finally, test if tri1 is totally contained in tri2 or vice versa */
		if (POINT_IN_TRI(V0, U0, U1, U2, i0, i1)) {
			return 1;
		}
		if (POINT_IN_TRI(U0, V0, V1, V2, i0, i1)) {
			return 1;
		}

		return 0;
	}

	private boolean EDGE_EDGE_TEST(double V0[], double U0[], double U1[], int i0, int i1, double Ax, double Ay) {
		double Bx, By, Cx, Cy, e, d, f;
		Bx = U0[i0] - U1[i0];
		By = U0[i1] - U1[i1];
		Cx = V0[i0] - U0[i0];
		Cy = V0[i1] - U0[i1];
		f = Ay * Bx - Ax * By;
		d = By * Cx - Bx * Cy;
		if ((f > 0 && d >= 0 && d <= f) || (f < 0 && d <= 0 && d >= f)) {
			e = Ax * Cy - Ay * Cx;
			if (f > 0) {
				if (e >= 0 && e <= f)
					return true;
			} else {
				if (e <= 0 && e >= f)
					return true;
			}
		}
		return false;
	}

	private boolean EDGE_AGAINST_TRI_EDGES(double[] V0, double[] V1, double[] U0, double[] U1, double[] U2, int i0,
			int i1) {
		double Ax, Ay;
		Ax = V1[i0] - V0[i0];
		Ay = V1[i1] - V0[i1];
		/* test edge U0,U1 against V0,V1 */
		if (EDGE_EDGE_TEST(V0, U0, U1, i0, i1, Ax, Ay)) {
			return true;
		}
		/* test edge U1,U2 against V0,V1 */
		if (EDGE_EDGE_TEST(V0, U1, U2, i0, i1, Ax, Ay)) {
			return true;
		}
		/* test edge U2,U1 against V0,V1 */
		if (EDGE_EDGE_TEST(V0, U2, U0, i0, i1, Ax, Ay)) {
			return true;
		}
		return false;
	}

	public boolean intersects(Triangle triangle2, double epsilon, double epsilon2) {
		double[] E1 = new double[3];
		double[] E2 = new double[3];
		double[] N1 = new double[3];
		double[] N2 = new double[3];
		double d1;
		double d2;
		double du0, du1, du2, dv0, dv1, dv2;
		double[] D = new double[3];
		double[] isect1 = new double[2];
		double[] isect2 = new double[2];
		double du0du1, du0du2, dv0dv1, dv0dv2;
		short index;
		double vp0, vp1, vp2;
		double up0, up1, up2;
		double bb, cc, max;

		/* compute plane equation of triangle(V0,V1,V2) */
		E1 = Vector.subtract(vertex2, vertex1);
		E2 = Vector.subtract(vertex3, vertex1);
		N1 = Vector.crossProduct(E1, E2);
		d1 = -Vector.dot(N1, vertex1);
		/* plane equation 1: N1.X+d1=0 */

		/*
		 * put U0,U1,U2 into plane equation 1 to compute signed distances to the plane
		 */
		du0 = Vector.dot(N1, triangle2.vertex1) + d1;
		du1 = Vector.dot(N1, triangle2.vertex2) + d1;
		du2 = Vector.dot(N1, triangle2.vertex3) + d1;

		if (Math.abs(du0) < epsilon)
			du0 = 0.0;
		if (Math.abs(du1) < epsilon)
			du1 = 0.0;
		if (Math.abs(du2) < epsilon)
			du2 = 0.0;

		du0du1 = du0 * du1;
		du0du2 = du0 * du2;

		if (du0du1 > -epsilon2 && du0du2 > -epsilon2) /* same sign on all of them + not equal 0 ? */
			return false; /* no intersection occurs */

		/* compute plane of triangle (U0,U1,U2) */
		E1 = Vector.subtract(triangle2.vertex2, triangle2.vertex1);
		E2 = Vector.subtract(triangle2.vertex3, triangle2.vertex1);
		N2 = Vector.crossProduct(E1, E2);
		d2 = -Vector.dot(N2, triangle2.vertex1);
		/* plane equation 2: N2.X+d2=0 */

		/* put V0,V1,V2 into plane equation 2 */
		dv0 = Vector.dot(N2, vertex1) + d2;
		dv1 = Vector.dot(N2, vertex2) + d2;
		dv2 = Vector.dot(N2, vertex3) + d2;

		if (Math.abs(dv0) < epsilon)
			dv0 = 0.0;
		if (Math.abs(dv1) < epsilon)
			dv1 = 0.0;
		if (Math.abs(dv2) < epsilon)
			dv2 = 0.0;

		dv0dv1 = dv0 * dv1;
		dv0dv2 = dv0 * dv2;

		if (dv0dv1 > -epsilon2 && dv0dv2 > -epsilon2) /* same sign on all of them + not equal 0 ? */
			return false; /* no intersection occurs */

		/* compute direction of intersection line */
		D = Vector.crossProduct(N1, N2);

		/* compute and index to the largest component of D */
		max = (double) Math.abs(D[0]);
		index = 0;
		bb = (double) Math.abs(D[1]);
		cc = (double) Math.abs(D[2]);
		if (bb > max) {
			max = bb;
			index = 1;
		}
		if (cc > max) {
			max = cc;
			index = 2;
		}
		;

		/* this is the simplified projection onto L */
		vp0 = vertex1[index];
		vp1 = vertex2[index];
		vp2 = vertex3[index];

		up0 = triangle2.vertex1[index];
		up1 = triangle2.vertex2[index];
		up2 = triangle2.vertex3[index];

		/* compute interval for triangle 1 */
		double[] nci1 = NEWCOMPUTE_INTERVALS(vp0, vp1, vp2, dv0, dv1, dv2, dv0dv1, dv0dv2);
		if (nci1 == null) {
			return coplanar_tri_tri(N1, vertex1, vertex2, vertex3, triangle2.vertex1, triangle2.vertex2,
					triangle2.vertex3) == 1;
		}
		double a = nci1[0];
		double b = nci1[1];
		double c = nci1[2];
		double x0 = nci1[3];
		double x1 = nci1[4];

		/* compute interval for triangle 2 */
		double[] nci2 = NEWCOMPUTE_INTERVALS(up0, up1, up2, du0, du1, du2, du0du1, du0du2);
		if (nci2 == null) {
			return coplanar_tri_tri(N1, vertex1, vertex2, vertex3, triangle2.vertex1, triangle2.vertex2,
					triangle2.vertex3) == 1;
		}
		double d = nci2[0];
		double e = nci2[1];
		double f = nci2[2];
		double y0 = nci2[3];
		double y1 = nci2[4];

		double xx, yy, xxyy, tmp;
		xx = x0 * x1;
		yy = y0 * y1;
		xxyy = xx * yy;

		tmp = a * xxyy;
		isect1[0] = tmp + b * x1 * yy;
		isect1[1] = tmp + c * x0 * yy;

		tmp = d * xxyy;
		isect2[0] = tmp + e * xx * y1;
		isect2[1] = tmp + f * xx * y0;

		if (isect1[0] > isect1[1]) {
			double x = isect1[0];
			isect1[0] = isect1[1];
			isect1[1] = x;
		}
		if (isect2[0] > isect2[1]) {
			double x = isect2[0];
			isect2[0] = isect2[1];
			isect2[1] = x;
		}
		
		if(GeometryUtils.almostEquals(isect1[1], isect2[0], epsilon) || GeometryUtils.almostEquals(isect2[1], isect1[0], epsilon)) {
			return false;
		}

		if (isect1[1] < isect2[0] || isect2[1] < isect1[0]) {
			return false;
		}
		
		return true;
	}
}