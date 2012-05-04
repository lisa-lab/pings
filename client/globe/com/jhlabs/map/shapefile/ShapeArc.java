/*
 * Copyright (C) Jerry Huxtable 1998-2001. All rights reserved.
 */
package com.jhlabs.map.shapefile;

import java.io.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * The class describing an Arc in a Shapefile
 */
public class ShapeArc extends ShapefileShape {

	protected double[] bounds = new double[4];
	protected int numParts;
	protected int[] parts;
	protected int numPoints;
	protected double[] points;

	public ShapeArc() {
	}
	
	public ShapeArc( double[] points ) {
		this.points = points;
		numPoints = points.length/2;
		numParts = 1;
		parts = new int[1];
		parts[0] = 0;
		bounds[0] = Double.MAX_VALUE;
		bounds[1] = Double.MAX_VALUE;
		bounds[2] = Double.MIN_VALUE;
		bounds[3] = Double.MIN_VALUE;
		for ( int i = 0; i < 2*numPoints; i += 2 ) {
			bounds[0] = Math.min( bounds[0], points[i] );
			bounds[1] = Math.min( bounds[1], points[i+1] );
			bounds[2] = Math.max( bounds[2], points[i] );
			bounds[3] = Math.max( bounds[3], points[i+1] );
		}
	}
	
	public void read( DataInput in ) throws ShapefileException, IOException {
		readHeader( in );
		int type = Shapefile.readLEInt( in );
		if ( type != Shapefile.ARC )
			throw new ShapefileException("Arc expected");
		readArc( in );
	}

	public void write( DataOutput out ) throws ShapefileException, IOException {
		Shapefile.writeLEInt( out, Shapefile.ARC );
		writeArc( out );
	}

	public void readArc( DataInput in ) throws ShapefileException, IOException {
		readBounds(in, bounds);

		numParts = Shapefile.readLEInt( in );
		numPoints = Shapefile.readLEInt( in );

		parts = new int[numParts];
		points = new double[2*numPoints];

		for (int i = 0; i < numParts;i++)
			 parts[i] = Shapefile.readLEInt( in );

		for (int i = 0; i < 2*numPoints; i++)
			points[i] = Shapefile.readLEDouble( in );
	}

	public void writeArc( DataOutput out ) throws ShapefileException, IOException {
		writeBounds( out, bounds );
		Shapefile.writeLEInt( out, numParts );
		Shapefile.writeLEInt( out, numPoints );
		for (int i = 0; i < numParts;i++)
            Shapefile.writeLEInt( out, parts[i] );

		for (int i = 0; i < 2*numPoints;i++)
            Shapefile.writeLEDouble( out, points[i] );
	}

	public int getNumParts() {
		return numParts;
	}
	
	public int[] getPartOffsets() {
		return parts;
	}

	public int getNumPoints() {
		return numPoints;
	}
	
	public double[] getPoints() {
		return points;
	}
	
	public double[] getBounds() {
		return bounds;
	}

	public int getShapeType() {
		return ARC;
	}

	public void getBounds(double[] bounds) {
		for ( int i = 0; i < 4; i ++ )
			bounds[i] = this.bounds[i];
	}

    public int getContentLength() {
        return 4 + 4*8 + 4 + 4 + numParts*4 + numPoints*16;
    }
    
	public Shape toShape() {	
		GeneralPath path = new GeneralPath( GeneralPath.WIND_EVEN_ODD, numPoints );
		int offsetIndex = 0;
		boolean first = true;
		for ( int j = 0; j < numPoints; j++ ) {
			float x = (float)points[2*j];
			float y = (float)points[2*j+1];
			if ( offsetIndex < parts.length && j == parts[offsetIndex] ) {
				if ( !first && getShapeType() == Shapefile.POLYGON)
					path.closePath();
				offsetIndex++;
				first = true;
			}
			if ( first )
				path.moveTo( x, y );
			else
				path.lineTo( x, y );
			first = false;
		}
		if ( !first && getShapeType() == Shapefile.POLYGON)
			path.closePath();
		return path;
	}

}
