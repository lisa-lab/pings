/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.vecmath;

import java.awt.*;

/**
 * Vector math package, converted to look similar to javax.vecmath.
 */
public class Color4f extends Tuple4f {

	public Color4f() {
		this( 0, 0, 0, 0 );
	}
	
	public Color4f( float[] x ) {
		this.x = x[0];
		this.y = x[1];
		this.z = x[2];
		this.w = x[3];
	}

	public Color4f( float x, float y, float z, float w ) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}

	public Color4f( Color4f t ) {
		this.x = t.x;
		this.y = t.y;
		this.z = t.z;
		this.w = t.w;
	}

	public Color4f( Tuple4f t ) {
		this.x = t.x;
		this.y = t.y;
		this.z = t.z;
		this.w = t.w;
	}

	public Color4f( Color c ) {
		set( c );
	}

	public void set( Color c ) {
		set( c.getRGBComponents( null ) );
	}

	public Color get() {
		return new Color( x, y, z, w );
	}
}
