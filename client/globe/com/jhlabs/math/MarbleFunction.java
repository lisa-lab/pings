/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.math;

public class MarbleFunction extends CompoundFunction2D {
	
	public MarbleFunction() {
		super(new TurbulenceFunction(new Noise(), 6));
	}
	
	public MarbleFunction(Function2D basis) {
		super(basis);
	}
	
	public float evaluate(float x, float y) {
		return (float)Math.pow(0.5 * (Math.sin(8. * basis.evaluate(x, y)) + 1), 0.77);
	}

}
