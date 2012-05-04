/*
 * Copyright (C) Jerry Huxtable 1998-2001. All rights reserved.
 */
package com.jhlabs.map.shapefile;

import java.io.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * The class describing a Shapefile point
 */
public class ShapePoint extends ShapefileShape {

	public double x,y;
	
	public ShapePoint() {
	}
	
	public ShapePoint(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	public void read( DataInput in ) throws ShapefileException, IOException {
		readHeader(in);
		int type = Shapefile.readLEInt( in );
		if (type != Shapefile.POINT)
			throw new ShapefileException("Point expected");
		x = Shapefile.readLEDouble( in );
		y = Shapefile.readLEDouble( in );
	}

	public void write( DataOutput out ) throws ShapefileException, IOException {
		Shapefile.writeLEInt( out, Shapefile.POINT );
		Shapefile.writeLEDouble( out, x );
		Shapefile.writeLEDouble( out, y );
	}

	public double getX() {
		return x;
	}
	
	public double getY() {
		return y;
	}
	
	public int getShapeType() {
		return Shapefile.POINT;
	}
	
	public void getBounds(double[] bounds) {
		bounds[0] = bounds[2] = x;
		bounds[1] = bounds[3] = y;
	}

    public int getContentLength() {
        return 20;
    }
    
	public Shape toShape() {
		return null;
	}
}
