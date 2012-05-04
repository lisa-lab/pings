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
import java.text.*;
import java.awt.geom.*;

public class PolarCoordinateSystem extends CoordinateSystem {
	
	protected Unit units;

	public PolarCoordinateSystem() {
		setShortName("Polar");
	}

	public CoordinateSystem getBaseCoordinateSystem() {
		return null;
	}
	
	public Rectangle2D getExtent() {
		return null;	// Infinite extent
	}

	public String format(Point2D p) {
		return format(p.getX(), p.getY(), true);
	}
	
	public String format(double x, double y, boolean abbrev) {
		return x+","+y;
	}

	public Point2D.Double parse(String s) throws ParseException {
		int index = s.indexOf(',');
		if (index != -1)
			return new Point2D.Double(Double.valueOf(s.substring(0, index)).doubleValue(), Double.valueOf(s.substring(index+1)).doubleValue());
		throw new ParseException("Missing comma", 0);
	}
	
	public Point2D.Double fromBase(Point2D.Double p) {
		return p;
	}
	
	public Point2D.Double toBase(Point2D.Double p) {
		return p;
	}

	public String getDefaultName() {
		return "Polar Coordinate System";
	}
}

