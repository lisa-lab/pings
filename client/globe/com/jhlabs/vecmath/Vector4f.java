/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.vecmath;

/**
 * Vector math package, converted to look similar to javax.vecmath.
 */
public class Vector4f extends Tuple4f {

	public Vector4f() {
		this( 0, 0, 0, 0 );
	}
	
	public Vector4f( float[] x ) {
		this.x = x[0];
		this.y = x[1];
		this.z = x[2];
		this.w = x[2];
	}

	public Vector4f( float x, float y, float z, float w ) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}

	public Vector4f( Vector4f t ) {
		x = t.x;
		y = t.y;
		z = t.z;
		w = t.w;
	}

	public Vector4f( Tuple4f t ) {
		x = t.x;
		y = t.y;
		z = t.z;
		w = t.w;
	}

	public float dot( Vector4f v ) {
		return v.x * x + v.y * y + v.z * z + v.w * w;
	}

	public float length() {
		return (float)Math.sqrt( x*x+y*y+z*z+w*w );
	}

	public void normalize() {
		float d = 1.0f/( x*x+y*y+z*z+w*w );
		x *= d;
		y *= d;
		z *= d;
		w *= d;
	}

}
