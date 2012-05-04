/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.math;

public class FractalSumFunction extends CompoundFunction2D {
	
	private float octaves = 1.0f;
	
	public FractalSumFunction(Function2D basis) {
		super(basis);
	}
	
	public float evaluate(float x, float y) {
		float t = 0.0f;

		for (float f = 1.0f; f <= octaves; f *= 2)
			t += basis.evaluate(f * x, f * y) / f;
		return t;
	}

}
