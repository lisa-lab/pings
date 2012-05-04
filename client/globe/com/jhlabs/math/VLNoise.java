/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.math;

public class VLNoise implements Function2D {

	private float distortion = 10.0f;

	public void setDistortion(float distortion) {
		this.distortion = distortion;
	}

	public float getDistortion() {
		return distortion;
	}

	public float evaluate(float x, float y) {
		float ox = Noise.noise2(x+0.5f, y) * distortion;
		float oy = Noise.noise2(x, y+0.5f) * distortion;
		return Noise.noise2(x+ox, y+oy);
	}

}
