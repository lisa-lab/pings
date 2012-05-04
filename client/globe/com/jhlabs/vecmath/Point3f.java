/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package com.jhlabs.vecmath;

/**
 * Vector math package, converted to look similar to javax.vecmath.
 */
public class Point3f extends Tuple3f {

	public Point3f() {
		this( 0, 0, 0 );
	}
	
	public Point3f( float[] x ) {
		this.x = x[0];
		this.y = x[1];
		this.z = x[2];
	}

	public Point3f( float x, float y, float z ) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Point3f( Point3f t ) {
		this.x = t.x;
		this.y = t.y;
		this.z = t.z;
	}

	public Point3f( Tuple3f t ) {
		this.x = t.x;
		this.y = t.y;
		this.z = t.z;
	}

	public float distanceL1( Point3f p ) {
		return Math.abs(x-p.x) + Math.abs(y-p.y) + Math.abs(z-p.z);
	}

	public float distanceSquared( Point3f p ) {
		float dx = x-p.x;
		float dy = y-p.y;
		float dz = z-p.z;
		return dx*dx+dy*dy+dz*dz;
	}

	public float distance( Point3f p ) {
		float dx = x-p.x;
		float dy = y-p.y;
		float dz = z-p.z;
		return (float)Math.sqrt( dx*dx+dy*dy+dz*dz );
	}

}
