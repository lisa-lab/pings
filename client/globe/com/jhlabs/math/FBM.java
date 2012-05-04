/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.math;

public class FBM implements Function2D {

	protected float[] exponents;
	protected float H;
	protected float lacunarity;
	protected float octaves;
	protected Function2D basis;

	public FBM(float H, float lacunarity, float octaves) {
		this(H, lacunarity, octaves, new Noise());
	}
	
	public FBM(float H, float lacunarity, float octaves, Function2D basis) {
		this.H = H;
		this.lacunarity = lacunarity;
		this.octaves = octaves;
		this.basis = basis;

		exponents = new float[(int)octaves+1];
		float frequency = 1.0f;
		for (int i = 0; i <= (int)octaves; i++) {
			exponents[i] = (float)Math.pow(frequency, -H);
			frequency *= lacunarity;
		}
	}

	public void setBasis(Function2D basis) {
		this.basis = basis;
	}

	public Function2D getBasisType() {
		return basis;
	}

	public float evaluate(float x, float y) {
		float value = 0.0f;
		float remainder;
		int i;
		
		// to prevent "cascading" effects
		x += 371;
		y += 529;
		
		for (i = 0; i < (int)octaves; i++) {
			value += basis.evaluate(x, y) * exponents[i];
			x *= lacunarity;
			y *= lacunarity;
		}

		remainder = octaves - (int)octaves;
		if (remainder != 0)
			value += remainder * basis.evaluate(x, y) * exponents[i];

		return value;
	}

}
