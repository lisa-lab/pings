/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.jhlabs.dbf;

import java.io. *;

/**
 * A class for eading DBF files. This is not a full implementation, but is enough for reading most Shapefile data.
 */
public class DBFFile {
	public final static int UNKNOWN = 0;
	public final static int BINARY = 'B';
	public final static int CHARACTER = 'C';
	public final static int DATE = 'D';
	public final static int NUMERIC = 'N';
	public final static int FLOAT = 'F';
	public final static int LOGICAL = 'L';
	public final static int MEMO = 'M';
	public final static int GENERAL = 'G';

	private final static int FOX_PRO = 0xF5;
	private final static int FOX_PRO_3 = 0x83;

	private int type;
	private byte lastUpdateYear;
	private byte lastUpdateMonth;
	private byte lastUpdateDay;
	private int numRows;
	private int numColumns;
	private DBFColumn columns[];
	private int dataPosition;
	private int dataLength;
	private DataInput input;
	private RandomAccessFile dbfFile;
	private RandomAccessFile memoFile;
	private int nextFreeMemo;
	private int memoBlockSize;
	private int currentPosition;

	public DBFFile( File file ) throws IOException {
		this( file, "r" );
	}
	
	/**
	 * Create a DBFFile from an InputStream. This allows only sequential access to records and memo fields
	 * will not be read.
	 */
	public DBFFile( InputStream is ) throws IOException {
		if ( !(is instanceof BufferedInputStream ) )
			is = new BufferedInputStream( is );
		if ( !(is instanceof DataInputStream ) )
			is = new DataInputStream( is );
		input = (DataInputStream)is;
		readHeader();
	}
	
	/**
	 * Create a DBFFile from a File. This allows only random access to records.
	 */
	public DBFFile( File file, String mode ) throws IOException {
		String filename = file.getPath();
		int index = filename.lastIndexOf( '.' );
		if ( index != -1 )
			filename = filename.substring( 0, index );
System.out.println(filename);

		try {
			dbfFile = new RandomAccessFile(filename+".dbf", mode);
		}
		catch (FileNotFoundException e) {
			dbfFile = new RandomAccessFile(filename+".DBF", mode);
		}
		input = dbfFile;

		readHeader();
		
		switch ( type ) {
		case FOX_PRO_3:
			try {
				memoFile = new RandomAccessFile(filename+".dbt", mode);
			}
			catch (FileNotFoundException e) {
				memoFile = new RandomAccessFile(filename+".DBT", mode);
			}
			nextFreeMemo = readLEInt( memoFile );
			memoBlockSize = 512;
			break;
		case FOX_PRO:
			try {
				memoFile = new RandomAccessFile(filename+".fbt", mode);
			}
			catch (FileNotFoundException e) {
				memoFile = new RandomAccessFile(filename+".FBT", mode);
			}
			nextFreeMemo = memoFile.readInt();
			memoFile.skipBytes(2);
			memoBlockSize = memoFile.readUnsignedShort();
			break;
		}
	}

	public void close() throws IOException {
		if ( dbfFile != null )
			dbfFile.close();
		if ( memoFile != null )
			memoFile.close();
	}

	private void readHeader() throws IOException {
		type = input.readUnsignedByte();
		lastUpdateYear = (byte)input.readUnsignedByte();
		lastUpdateMonth = (byte)input.readUnsignedByte();
		lastUpdateDay = (byte)input.readUnsignedByte();
		numRows = readLEInt( input );
		dataPosition = readLEUnsignedShort( input );
		dataLength = readLEUnsignedShort( input );
		numColumns = (dataPosition-33)/32;
//		dbfFile.seek(32);
		input.skipBytes( 20 );

		columns = new DBFColumn[numColumns];
		int offset = 1;

		byte name[] = new byte[11];
		for ( int i = 0; i < numColumns; i++ ) {
			columns[i] = new DBFColumn();
			input.readFully( name );
			columns[i].name = new String( name );
			columns[i].type = (char)input.readUnsignedByte();
			input.skipBytes(4);
			columns[i].offset = offset;
			columns[i].length = input.readUnsignedByte();
			offset += columns[i].length;
			columns[i].decimal = input.readByte();
			input.skipBytes(14);
		}
		currentPosition = 32 + numColumns*32;
	}

	public int getNumRows() {
		return numRows;
	}

	public int getNumColumns() {
		return numColumns;
	}

	public DBFColumn getColumn( int column ) {
		return columns[column];
	}

	public String getColumnName( int column ) {
		return columns[column].name;
	}

	public Object getField( int record, int field ) throws IOException {
		Object value;
		int position = dataPosition+(record*dataLength)+columns[field].offset;
		int length = columns[field].length;
		if ( dbfFile != null )
			dbfFile.seek( position );
		else if ( position >= currentPosition ) {
			input.skipBytes( position - currentPosition );
			currentPosition = position + length;
		} else
			throw new IOException( "DBFFile: Only sequential access is allowed to records." );
		switch ( columns[field].type ) {
		case NUMERIC:
		case FLOAT:
			value = readNumericField( length );
			break;
		case MEMO:
			value = readMemoField( length );
			break;
		case CHARACTER:
		case GENERAL:
		case DATE:
		case BINARY:
		case LOGICAL:
		default:
			value = readCharacterField( length );
			break;
		}
		return value;
	}

	private String readCharacterField( int len ) throws IOException {
		byte bytes[] = new byte[len];
		input.readFully( bytes );
		return new String(bytes);
	}

	private String readNumericField( int len ) throws IOException {
		byte bytes[] = new byte[len];
		input.readFully( bytes );
		return new String(bytes).trim();
	}

	private String readMemoField( int len ) throws IOException {
		byte bytes[] = new byte[len];
		String value;
		int	offset;
		int	length;
		int	memoType;

		input.readFully( bytes );
		try {
			offset = Integer.parseInt( (new String(bytes)).trim() );
		}
		catch ( NumberFormatException e ) {
			offset = 0;
		}
		if ( offset >  0) {
			switch (type) {
			case FOX_PRO_3:
				memoFile.seek(memoBlockSize*offset);
				StringBuffer sb = new StringBuffer();
				int c;
				while ((c = memoFile.read()) != -1 && c != 26 && c != 0)
					sb.append((char)c);
				value = sb.toString();
				break;
			case FOX_PRO:
				memoFile.seek(512);
				memoType = memoFile.readInt();
				length = memoFile.readInt();
				bytes = new byte[length];
				memoFile.read(bytes);
				value = new String(bytes);
				break;
			default:
				throw new IOException("DBFFile: Unsupported memo file type");
			}
		} else
			value = "";
		return value;
	}

	private final static short readLEShort( DataInput in ) throws IOException {
		int ch2 = in.readUnsignedByte();
		int ch1 = in.readUnsignedByte();
		return (short)((ch1 << 8) + (ch2 << 0));
	}
 
	private final static int readLEUnsignedShort( DataInput in ) throws IOException {
		int ch1 = in.readUnsignedByte();
		int ch2 = in.readUnsignedByte();
		return (ch2 << 8) + (ch1 << 0);
	}

	private final static int readLEInt( DataInput in ) throws IOException {
		int ch1 = in.readUnsignedByte();
		int ch2 = in.readUnsignedByte();
		int ch3 = in.readUnsignedByte();
		int ch4 = in.readUnsignedByte();
		return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0));
	}
}

