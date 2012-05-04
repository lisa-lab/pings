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

/**
 * Class implementing a geographic coordinate syste. That is, a rectangular coordinate
 * system one based on latitude/longitude.
 */
public class GeoCoordinateSystem extends CoordinateSystem {

	protected Projection projection;
	protected Datum datum;

	public static NumberFormat format;
	
	static {
		format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(3);
		format.setGroupingUsed(false);
	}
	
	public GeoCoordinateSystem() {
		setShortName("X,Y");
	}
	
	public GeoCoordinateSystem(String name, Projection projection) {
		this(name, projection, Units.METRES);
		projection.initialize();
	}
	
	public GeoCoordinateSystem(String name, Projection projection, Unit units) {
		this.name = name;
		this.projection = projection;
		this.units = units;
		projection.initialize();
	}
	
	public void setProjection(Projection projection) {
		this.projection = projection;
	}

	public Projection getProjection() {
		return projection;
	}

	public CoordinateSystem getBaseCoordinateSystem() {
		return CoordinateSystem.LAT_LONG;
	}
	
	public Rectangle2D getExtent() {
		return null;	// Infinite extent
	}

	public String format(Point2D p) {
		return format(p.getX(), p.getY(), true);
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
//FIXME		if (projection != null)
//			projection.transform(p, p, units);
		return p;
	}
	
	public Point2D.Double toBase(Point2D.Double p) {
//FIXME		if (projection != null)
//			projection.inverseTransform(p, p, units);
		return p;
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof GeoCoordinateSystem))
			return false;
		GeoCoordinateSystem c = (GeoCoordinateSystem)o;
		return super.equals(o) && (projection == c.projection || projection.equals(c.projection)) &&
			(datum == c.datum || datum.equals(c.datum));
	}
	
	public String getDefaultName() {
		return "Geographic Coordinate System";
	}
}

