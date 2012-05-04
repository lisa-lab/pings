/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.vecmath;

/**
 * Vector math package, converted to look similar to javax.vecmath.
 */
public class Vector3f extends Tuple3f {

	public Vector3f() {
		this( 0, 0, 0 );
	}
	
	public Vector3f( float[] x ) {
		this.x = x[0];
		this.y = x[1];
		this.z = x[2];
	}

	public Vector3f( float x, float y, float z ) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vector3f( Vector3f t ) {
		this.x = t.x;
		this.y = t.y;
		this.z = t.z;
	}

	public Vector3f( Tuple3f t ) {
		this.x = t.x;
		this.y = t.y;
		this.z = t.z;
	}

	public float angle( Vector3f v ) {
		return (float)Math.acos( dot(v) / (length()*v.length()) );
	}

	public float dot( Vector3f v ) {
		return v.x * x + v.y * y + v.z * z;
	}

	public void cross( Vector3f v1, Vector3f v2 ) {
		x = v1.y * v2.z - v1.z * v2.y;
		y = v1.z * v2.x - v1.x * v2.z;
		z = v1.x * v2.y - v1.y * v2.x;
	}

	public float length() {
		return (float)Math.sqrt( x*x+y*y+z*z );
	}

	public void normalize() {
		float d = 1.0f/(float)Math.sqrt( x*x+y*y+z*z );
		x *= d;
		y *= d;
		z *= d;
	}

}
