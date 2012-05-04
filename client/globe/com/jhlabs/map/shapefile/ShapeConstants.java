/*
 * Copyright (C) Jerry Huxtable 1998-2001. All rights reserved.
 */
package com.jhlabs.map.shapefile;

/**
 * Constants assocated with Shapefiles
 */
public interface ShapeConstants {
    public static final int SHAPEFILE_ID = 9994;
    public static final int VERSION = 1000;
    
	public static final int NONE = 0;
	public static final int POINT = 1;
	public static final int ARC = 3;
	public static final int POLYGON = 5;
	public static final int MULTIPOINT = 8;
	public static final int UNDEFINED = -1;
}
