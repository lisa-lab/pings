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

public class UPSCoordinateSystem extends GeoCoordinateSystem {

	public int digits = 3;

	public UPSCoordinateSystem() {
		StereographicAzimuthalProjection sap = new StereographicAzimuthalProjection();
		sap.setupUPS( StereographicAzimuthalProjection.NORTH_POLE );
		setProjection(sap);
		setShortName("UPS");
	}

	public Rectangle2D getExtent() {
		return MapMath.WORLD_BOUNDS;//FIXME-in UPSG coords
	}
	
	public String format(Point2D p) {
		return format(p.getX(), p.getY(), true);
	}
	
	public String format(double x, double y, boolean abbrev) {
		StringBuffer s = new StringBuffer();
		formatGridReference((int)(x % 100000), (int)(y % 100000), s, 5, 5-digits);
		return s.toString();
	}

	public Point2D.Double parse(String s) throws ParseException {
		throw new ParseException("not implemented yet", 0);
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof UTMCoordinateSystem))
			return false;
		UTMCoordinateSystem c = (UTMCoordinateSystem)o;
		return super.equals(o) && digits == c.digits;
	}
	
	public String getDefaultName() {
		return "Universal Stereographic Polar";
	}	
}

