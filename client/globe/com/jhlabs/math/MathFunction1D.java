/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.math;

public class MathFunction1D implements Function1D {

	public final static int SIN = 1;
	public final static int COS = 2;
	public final static int TAN = 3;
	public final static int SQRT = 4;
	public final static int ASIN = -1;
	public final static int ACOS = -2;
	public final static int ATAN = -3;
	public final static int SQR = -4;

	private int operation;
	
	public MathFunction1D(int operation) {
		this.operation = operation;
	}
	
	public float evaluate(float v) {
		switch (operation) {
		case SIN:
			return (float)Math.sin(v);
		case COS:
			return (float)Math.cos(v);
		case TAN:
			return (float)Math.tan(v);
		case SQRT:
			return (float)Math.sqrt(v);
		case ASIN:
			return (float)Math.asin(v);
		case ACOS:
			return (float)Math.acos(v);
		case ATAN:
			return (float)Math.atan(v);
		case SQR:
			return v*v;
		}
		return v;
	}
}

