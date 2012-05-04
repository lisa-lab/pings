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

public class LatLongCoordinateSystem extends CoordinateSystem {

	protected AngleFormat latFormat, longFormat;
	
	public LatLongCoordinateSystem() {
		latFormat = new AngleFormat(AngleFormat.ddmmssLatPattern, true);
		longFormat = new AngleFormat(AngleFormat.ddmmssLongPattern, true);
		setShortName("Lat/Long");
	}

	public CoordinateSystem getBaseCoordinateSystem() {
		return this;
	}
	
	public Rectangle2D getExtent() {
		return MapMath.WORLD_BOUNDS;
	}
	
	public String format(int which, double x, double y) {
		StringBuffer buf = new StringBuffer();
		switch (which) {
		case X:
			longFormat.format(x, buf, null);
			break;
		case Y:
			latFormat.format(y, buf, null);
			break;
		case GRID_REF:
			return format(x, y, false);
		}
		return buf.toString();
	}

	public String format(double x, double y, boolean abbrev) {
		StringBuffer buf = new StringBuffer();
		latFormat.format(y, buf, null);
		buf.append(' ');
		longFormat.format(x, buf, null);
		return buf.toString();
	}

	public Point2D.Double parse(String s) throws ParseException {
		int index = s.indexOf(',');
		if (index == -1)
			index = s.indexOf(' ');
		if (index != -1)
			return new Point2D.Double(
				longFormat.parse(s.substring(index+1)).doubleValue(),
				latFormat.parse(s.substring(0, index)).doubleValue()
			);
		throw new ParseException("Missing comma", 0);
	}
	
	public Point2D.Double parse(String x, String y) throws ParseException {
		return new Point2D.Double(longFormat.parse(x).doubleValue(), latFormat.parse(y).doubleValue());
	}

	public Point2D.Double fromBase(Point2D.Double p) {
		return p;
	}
	
	public Point2D.Double toBase(Point2D.Double p) {
		return p;
	}

	public boolean isReversedXY() {
		return true;
	}

	public String getLabel(int c) {
		switch (c) {
		case X:
			return "Longitude:";
		default:
			return "Latitude:";
		}
	}
	
	public String getDefaultName() {
		return "Latitude/Longitude";
	}	
}

