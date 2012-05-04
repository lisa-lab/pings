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

import com.jhlabs.map.*;
import com.jhlabs.map.proj.*;
import java.awt.*;
import java.awt.geom.*;
import java.text.*;
import java.text.ParseException;

public class RectangularCoordSys extends CoordinateSystem {
	
	protected Projection projection;
	protected Datum datum;
	protected Unit units;

	public static NumberFormat format;
	
	public static String RECT_CS_NAME = "Rectangular";

	static {
		format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(2);
		format.setGroupingUsed(false);
	}
	
	public RectangularCoordSys() {
		this(null, null);
	}
	
	public RectangularCoordSys(String name, Projection projection) {
		this.name = name;
		this.projection = projection;
		if (projection != null)
			projection.initialize();
		setShortName("X,Y");
	}
	
	public CoordinateSystem getBaseCoordinateSystem() {
		return null;
	}
	
	public Rectangle2D getExtent() {
		return null;	// Infinite extent
	}

	public String format(int which, double x, double y) {
		switch (which) {
		case X:
			return format.format(x);
		case Y:
			return format.format(y);
		default:
			return format(x, y, false);
		}
	}

	public String format(Point2D p) {
		return format(p.getX(), p.getY(), true);
	}
	
	public String format(double x, double y, boolean abbrev) {
		return format.format(x)+","+format.format(y);
	}

	public Point2D.Double parse(String s) throws ParseException {
		int index = s.indexOf(',');
		if (index != -1)
			return new Point2D.Double(format.parse(s.substring(0, index)).doubleValue(), format.parse(s.substring(index+1)).doubleValue());
		throw new ParseException("Missing comma", 0);
	}
	
	public Point2D.Double fromBase(Point2D.Double p) {
		if (projection != null)
			projection.transform(p, p);
		return p;
	}
	
	public Point2D.Double toBase(Point2D.Double p) {
		if (projection != null)
			projection.inverseTransform(p, p);
		return p;
	}

	public String getDefaultName() {
		return RECT_CS_NAME;
	}
}

