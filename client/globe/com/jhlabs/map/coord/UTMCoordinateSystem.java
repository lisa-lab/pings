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

public class UTMCoordinateSystem extends GeoCoordinateSystem {

	public int digits = 5;
	public int zone = 0;
	public boolean south = false;

	public UTMCoordinateSystem() {
		this(0, false);
		setShortName("UTM");
	}
	
	public UTMCoordinateSystem(int zone, boolean south) {
		this.south = south;
		projection = new TransverseMercatorProjection();
		projection.setFalseEasting( 500000 );
		projection.setFalseNorthing( south ? 10000000.0 : 0.0 );
		setUTMZone(zone);
		setShortName("UTM Zone "+zone+(south ? "S" : "N"));
		setName("UTM Zone "+zone+(south ? "S" : "N"));
	}

	public void setUTMZone(int zone) {
		this.zone = zone;
		((TransverseMercatorProjection)projection).setUTMZone(zone);
	}

	public int getUTMZone() {
		return zone;
	}

	public Rectangle2D getExtent() {
		return MapMath.WORLD_BOUNDS;//FIXME-in UTM coords
	}
	
	public String format(Point2D p) {
		return format(p.getX(), p.getY(), true);
	}
	
	public String format(double x, double y, boolean abbrev) {
		TransverseMercatorProjection t = (TransverseMercatorProjection)projection;
		int zone = t.getZoneFromNearestMeridian(x);
		int row = t.getRowFromNearestParallel(y);
		t.setUTMZone(zone);

		if (row != 0) {
			StringBuffer s = new StringBuffer();
			s.append(zone);
			s.append((char)('C'+row-3));
			s.append(' ');
			s.append('?');//FIXME-need info on UTM two-letter codes
			s.append('?');
			s.append(' ');
			formatGridReference((int)(x % 100000), (int)(y % 100000), s, 5, 5-digits);
			return s.toString();
		}
		return "None: "+x+","+y;
	}

	public Point2D.Double parse(String s) throws ParseException {
		throw new ParseException("not implemented yet", 0);
	}
		
	public boolean equals(Object o) {
		if (!(o instanceof UTMCoordinateSystem))
			return false;
		UTMCoordinateSystem c = (UTMCoordinateSystem)o;
		return super.equals(o) && zone == c.zone && south == c.south && digits == c.digits;
	}
	
	public String getDefaultName() {
		return "Universal Transverse Mercator";
	}	
}

