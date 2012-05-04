/*
 * Copyright (C) Jerry Huxtable 1998-2001. All rights reserved.
 */
package com.jhlabs.map.shapefile;

import java.io.*;
import java.awt.*;

/**
 * The abstract base class for Shapefile objects.
 */
public abstract class ShapefileShape implements ShapeConstants {
	public void readHeader( DataInput in ) throws IOException {
		int recordNumber = in.readInt();
		int contentLength = in.readInt();
	}
	
	public void read( DataInput in ) throws ShapefileException, IOException {
	}
	
	public void readBounds( DataInput in, double[] bounds) throws IOException {
		for (int i = 0; i < 4; i++)
			bounds[i] = Shapefile.readLEDouble( in );
	}
	
	public void write( DataOutput out ) throws ShapefileException, IOException {
	}

	public void writeBounds( DataOutput out, double[] bounds) throws ShapefileException, IOException {
		for (int i = 0; i < 4; i++)
            Shapefile.writeLEDouble( out, bounds[i] );
	}

	public int getShapeType() {
		return UNDEFINED;
	}
	
	public abstract void getBounds( double[] bounds );
    public abstract int getContentLength();
	public abstract Shape toShape();
}
