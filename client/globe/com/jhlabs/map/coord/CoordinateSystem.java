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

package com.jhlabs.map.coord;

import java.io.*;
import java.text.*;
import java.awt.geom.*;
import com.jhlabs.map.*;
import com.jhlabs.map.proj.*;

/**
 * A coordinate system - the combination of a projection with an origin, scale and units.
 */
public abstract class CoordinateSystem implements Cloneable {

	public final static int X = 0;
	public final static int Y = 1;
	public final static int GRID_REF = 2;

	public final static CoordinateSystem LAT_LONG = new LatLongCoordinateSystem();
	public final static CoordinateSystem RECTANGULAR = new RectangularCoordSys();
	
	protected String name = null;
	protected String shortName = null;
	protected Unit units = Units.METRES;

	public Object clone() {
		try {
			CoordinateSystem cs = (CoordinateSystem)super.clone();
			return cs;
		}
		catch (CloneNotSupportedException e) {
			throw new InternalError();
		}
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name != null ? name : getDefaultName();
	}
	
	public void setShortName(String shortName) {
		this.shortName = shortName;
	}
	
	public String getShortName() {
		return shortName != null ? shortName : getDefaultName();
	}
	
	public String getDefaultName() {
		return "Coordinate System";
	}

	public String toString() {
		return getName();
	}	

	public void setUnits(Unit units) {
		this.units = units;
	}

	public Unit getUnits() {
		return units;
	}

	public String format(int which, double x, double y) {
		switch (which) {
		case X:
			return Double.toString(x);
		case Y:
			return Double.toString(y);
		case GRID_REF:
			return format(x, y, false);
		}
		return "?";
	}

	// Format a number with a specified number of digits. Used for formatting grid references
	public static void formatNumber(int n, StringBuffer s, int digits, int digitsWanted) {
		if (digits > 0)
			formatNumber(n/10, s, digits-1, digitsWanted);
		if (digits >= digitsWanted)
			s.append((char)('0' + n%10));
	}

	public static void formatGridReference(int x, int y, StringBuffer s, int digits, int digitsWanted) {
		formatNumber(x, s, digits, digitsWanted);
		formatNumber(y, s, digits, digitsWanted);
	}

	public abstract CoordinateSystem getBaseCoordinateSystem();
	public abstract Rectangle2D getExtent();
	public abstract String format(double x, double y, boolean abbrev);
	public abstract Point2D.Double parse(String s) throws ParseException;
	public abstract Point2D.Double fromBase(Point2D.Double p);
	public abstract Point2D.Double toBase(Point2D.Double p);

	public String format(Point2D.Double p) {
		return format(p.x, p.y, true);
	}
	
	public Point2D.Double parse(String x, String y) throws ParseException {
		//FIXME-this is tacky....
		return parse(x+','+y);
	}

	public boolean equals(Object o) {
		if (!(o instanceof CoordinateSystem))
			return false;
		CoordinateSystem c = (CoordinateSystem)o;
		return equalStrings(name, c.name) &&
			equalStrings(shortName, c.shortName) &&
			units.equals(c.units);
	}
	
	protected boolean equalStrings(String s1, String s2) {
		if (s1 == null)
			return s2 == null;
		return s1.equals(s2);
	}
	
	/**
	 * Returns true if this CS should be input and displayed as a grid reference rather than
	 * as separate X/Y components.
	 */
	public boolean isSingleGridReference() {
		return false;
	}
	
	/**
	 * Returns true if this CS should reverse the order of X and Y (for lat/long).
	 */
	public boolean isReversedXY() {
		return false;
	}
	
	public String getLabel(int c) {
		switch (c) {
		case X:
			return "X:";
		case Y:
			return "Y:";
		default:
			return "Grid Reference:";
		}
	}
	
	public static Point2D.Double convert(Point2D.Double p, CoordinateSystem from, CoordinateSystem to) {
		if (from == to)
			return p;
//		if (!from.getBaseCoordinateSystem().equals(to.getBaseCoordinateSystem()))
//			throw new IllegalArgumentException("Incompatible coordinate systems");
		return to.fromBase(from.toBase(p));
	}
	
/*FIXME
	public static CoordinateSystem findCoordinateSystem(String name) {
		if (name != null) {
			java.util.Vector v = MapApplication.getMapInstance().getCoordinateSystems();
			for (int i = 0; i < v.size(); i++) {
				CoordinateSystem cs = (CoordinateSystem)v.elementAt(i);
				if (name.equals(cs.toString()))
					return cs;
			}
		}
		return null;
	}
*/
}
