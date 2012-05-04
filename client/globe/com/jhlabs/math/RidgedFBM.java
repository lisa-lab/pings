/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.math;

public class RidgedFBM implements Function2D {

	public float evaluate(float x, float y) {
		return 1-Math.abs(Noise.noise2(x, y));
	}

}
