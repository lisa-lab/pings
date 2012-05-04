/*
 * Copyright (C) Jerry Huxtable 1998-2001. All rights reserved.
 */
package com.jhlabs.map.shapefile;

import java.io.*;
import java.util.*;
import com.jhlabs.dbf.*;

/**
 * The class describing an ESRI Shapefile
 */
public class Shapefile implements ShapeConstants {

	private int fileLength;
	private int version;
	private int shapeType;
	private double[] bounds = new double[4];
	private RandomAccessFile file;
	private DBFFile dbf;
	private DataInput in;
	private DataOutput out;
	private int[] index;
	private int numRecords;
	private int currentRecord;

	/**
	 * Use this constructor wherever possible as it will take account of ancillary files such as
	 * Shapefile index and attribute files.
	 */
	public Shapefile( File file ) throws ShapefileException, IOException {
		String filename = file.getPath();
		int index = filename.lastIndexOf( '.' );
		if ( index != -1 )
			filename = filename.substring( 0, index );

		DataInputStream in = new DataInputStream( new BufferedInputStream( new FileInputStream( file ) ) );
		try {
			readIndex( new DataInputStream( new BufferedInputStream( new FileInputStream( filename+".shx" ) ) ) );
		}
		catch ( IOException e ) {
			// Doesn't matter if there's no index
		}
		try {
			dbf = new DBFFile( file, "r" );
		}
		catch ( IOException e ) {
			// Doesn't matter if there's no attribute data
		}
		initialize( in, null );
	}
	
	/**
	 * Read a simple Shapefile with no index or attributes.
	 */
	public Shapefile( InputStream is ) throws ShapefileException, IOException {
		this( is, null, null );
	}
	
	/**
	 * Read a simple Shapefile with index and attributes (either may be null). Only sequential access is allowed.
	 */
	public Shapefile( InputStream is, InputStream isIndex, InputStream isDBF ) throws ShapefileException, IOException {
		if ( !(is instanceof BufferedInputStream ) )
			is = new BufferedInputStream( is );
		if ( !(is instanceof DataInputStream ) )
			is = new DataInputStream( is );
		if ( isIndex != null ) {
			if ( !(isIndex instanceof BufferedInputStream ) )
				isIndex = new BufferedInputStream( isIndex );
			if ( !(isIndex instanceof DataInputStream ) )
				isIndex = new DataInputStream( isIndex );
		}
		if ( isDBF != null )
			dbf = new DBFFile( isDBF );
		initialize( (DataInput)is, (DataInput)isIndex );
	}
	
	/**
	 * Creates a shapefile for writing.
	 */
	public Shapefile( File file, int shapeType ) throws ShapefileException, IOException {
		this.shapeType = shapeType;
		this.file = new RandomAccessFile( file, "rw" );
		this.file.setLength( 0 );
		out = new DataOutputStream( new BufferedOutputStream( new FileOutputStream( this.file.getFD() ) ) );
		writeHeader( out );
		currentRecord = 0;
	}
	
	private void initialize( DataInput in, DataInput inIndex ) throws ShapefileException, IOException {
		this.in = in;
		if ( inIndex != null )
			readIndex( inIndex );
		readHeader( in );
		currentRecord = 0;
	}
	
	private void readIndex( DataInput in ) throws ShapefileException, IOException {
		if ( in != null ) {
			// Read the index. Sort it so we never have to seek in the input
			readHeader( in );
			index = new int[2*numRecords];
			for ( int i = 0; i < numRecords; i++ ) {
				int i2 = i*2;
				for ( int j = 0; j < i; j++ ) {
					int j2 = j*2;
					if ( index[i2] < index[j2] ) {
						int t = index[i2];
						index[i2] = index[j2];
						index[j2] = t;
						i2++;
						j2++;
						t = index[i2];
						index[i2] = index[j2];
						index[j2] = t;
					}
				}
			}
		}
	}
	
	public ShapefileShape readShape() throws ShapefileException, IOException {
		return readShape( currentRecord++ );
	}
	
	public ShapefileShape readShape( int record ) throws ShapefileException, IOException {
		if ( index != null ) {
			int offset = index[ 2*record ];
			offset = offset * 2 - 100;
//FIXME			in.skipBytes( offset-in.getBytesRead() );
		}

		ShapefileShape shape = null;
		try {
			switch (shapeType) {
			case POINT:
				shape = new ShapePoint();
				shape.read( in );
				break;
			case ARC:
				shape = new ShapeArc();
				shape.read( in );
				break;
			case POLYGON:
				shape = new ShapePolygon();
				shape.read( in );
				break;
			}
        }
		catch ( EOFException e ) {
			// This is normal - we've read the last shape
			shape = null;
        }
		return shape;
	}
	
	private void readHeader( DataInput in ) throws ShapefileException, IOException {
		int fileCode = in.readInt();
		if ( fileCode != Shapefile.SHAPEFILE_ID )
			throw new IOException( "File is not a Shapefile" );

		in.skipBytes(20);
		fileLength = in.readInt();
		version = readLEInt( in );
		if ( version > VERSION )
			throw new ShapefileException( "The version of this Shapefile is too new" );

		shapeType = readLEInt( in );
		switch (shapeType) {
		case POINT:
		case ARC:
		case POLYGON:
			break;
		default:
			throw new ShapefileException( "Shape type "+shapeType+" not implemented") ;
		}

		numRecords = (fileLength - 100) / 8;
		
		for (int i = 0; i < 4; i++)
			bounds[i] = readLEDouble( in );
		
		in.skipBytes(32);
	}
	
	public void writeShape( ShapefileShape shape ) throws ShapefileException, IOException {
		int length = shape.getContentLength() / 2; // File length is measured in shorts
		out.writeInt( ++currentRecord );
		out.writeInt( length );
		shape.write( out );
		fileLength += 4 + length;
		if ( currentRecord == 1 ) {
			shape.getBounds( bounds );
		} else {
			double[] b = new double[4];
			shape.getBounds( b );
			bounds[0] = Math.min( b[0], bounds[0] );
			bounds[1] = Math.min( b[1], bounds[1] );
			bounds[2] = Math.max( b[2], bounds[2] );
			bounds[3] = Math.max( b[3], bounds[3] );
		}
	}
	
	private void writeHeader( DataOutput out ) throws ShapefileException, IOException {
		out.writeInt( Shapefile.SHAPEFILE_ID );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( fileLength );
		writeLEInt( out, VERSION );
		writeLEInt( out, shapeType );

		switch ( shapeType ) {
		case POINT:
		case ARC:
		case POLYGON:
			break;
		default:
			throw new ShapefileException( "Shape type "+shapeType+" not implemented") ;
		}

		for ( int i = 0; i < 4; i++ )
			writeLEDouble( out, bounds[i] );
		
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		out.writeInt( 0 );
		fileLength = 50;
	}
	
	public void close() throws ShapefileException, IOException {
		if ( out != null ) {
			((OutputStream)out).flush();
			file.seek( 0 );
			writeHeader( out );
			((OutputStream)out).close();
			file.close();
		}
		if ( dbf != null )
			dbf.close();
	}

	public int getShapeType() {
		return shapeType;
	}
	
	public int getVersion() {
		return version;
	}
	
	public int getNumRecords() {
		return numRecords;
	}
	
	public double[] getBounds() {
		return bounds;
	}

	// Attributes
	public int getNumColumns() {
		return dbf != null ? dbf.getNumColumns() : 0;
	}

	public DBFColumn getColumn( int i ) {
		return dbf != null ? dbf.getColumn( i ) : null;
	}

	public String getColumnName( int i ) {
		return dbf != null ? dbf.getColumnName( i ) : null;
	}

	public Object getField( int record, int field ) throws IOException {
		return dbf != null ? dbf.getField( record, field ) : null;
	}

	// Package-private methods for reading and writing little-endian data
	final static int readLEInt( DataInput in ) throws IOException {
		int ch1 = in.readUnsignedByte();
		int ch2 = in.readUnsignedByte();
		int ch3 = in.readUnsignedByte();
		int ch4 = in.readUnsignedByte();
		return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0));
	}

	final static long readLELong( DataInput in ) throws IOException {
		int i1 = readLEInt( in );
		int i2 = readLEInt( in );
		return ((long)i2 << 32) | (i1 & 0xFFFFFFFFL);
	}

	final static double readLEDouble( DataInput in ) throws IOException {
		return Double.longBitsToDouble( readLELong( in ) );
	}

	final static void writeLEInt( DataOutput out, int n ) throws IOException {
        out.writeByte( n & 0xff );
        out.writeByte( (n >> 8) & 0xff );
        out.writeByte( (n >> 16) & 0xff );
        out.writeByte( (n >> 24) & 0xff );
	}

	final static void writeLELong( DataOutput out, long n ) throws IOException {
        writeLEInt( out, (int)(n & 0xffffffff) );
        writeLEInt( out, (int)((n >> 32) & 0xffffffff) );
	}

	final static void writeLEDouble( DataOutput out, double n ) throws IOException {
		writeLELong( out, Double.doubleToLongBits(n) );
	}
}

