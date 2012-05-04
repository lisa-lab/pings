/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.image;

import java.awt.image.*;

/**
 * A filter which simulates chrome.
 */
public class ChromeFilter extends LightFilter {
	private float amount = 0.5f;
	private float exposure = 1.0f;

	public void setAmount(float amount) {
		this.amount = amount;
	}

	public float getAmount() {
		return amount;
	}

	public void setExposure(float exposure) {
		this.exposure = exposure;
	}
	
	public float getExposure() {
		return exposure;
	}

    public BufferedImage filter( BufferedImage src, BufferedImage dst ) {
		setColorSource( LightFilter.COLORS_CONSTANT );
		dst = super.filter( src, dst );
		TransferFilter tf = new TransferFilter() {
			protected float transferFunction( float v ) {
				v += amount * (float)Math.sin( v * 2 * Math.PI );
				return 1 - (float)Math.exp(-v * exposure);
			}
		};
        return tf.filter( dst, dst );
    }

	public String toString() {
		return "Effects/Chrome...";
	}
}

