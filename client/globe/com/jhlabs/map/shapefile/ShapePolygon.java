/*
 * Copyright (C) Jerry Huxtable 1998-2001. All rights reserved.
 */
package com.jhlabs.map.shapefile;

import java.io.*;

/**
 * The class describing a Shapefile point
 */
public class ShapePolygon extends ShapeArc {

	public ShapePolygon() {
	}
	
	public ShapePolygon( double[] points ) {
		super( points );
	}
	
	public void read( DataInput in ) throws ShapefileException, IOException {
		readHeader( in );
		int type = Shapefile.readLEInt( in );
		if ( type != Shapefile.POLYGON )
			throw new ShapefileException("Polygon expected");
		readArc( in );
	}

	public void write( DataOutput out ) throws ShapefileException, IOException {
		Shapefile.writeLEInt( out, Shapefile.POLYGON );
		writeArc( out );
	}

	public int getShapeType() {
		return Shapefile.POLYGON;
	}
}
