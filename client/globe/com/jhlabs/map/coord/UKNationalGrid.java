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

public class UKNationalGrid extends GeoCoordinateSystem {

	public int digits = 3;
	
	public UKNationalGrid() {
		projection = new TransverseMercatorProjection();
		projection.setEllipsoid( Ellipsoid.AIRY );
		projection.setProjectionLatitude(Math.toRadians(49.0));
		projection.setProjectionLongitude(Math.toRadians(-2.0));
		projection.setScaleFactor( 0.9996012717 );
		projection.setFalseEasting( 400000 );
		projection.setFalseNorthing( -100000 );
		projection.initialize();
		setShortName("UK");
	}

	public Rectangle2D getExtent() {
		return new Rectangle2D.Double(0, 0, 2500000, 2500000);
	}
	
	private String osFormat = "XYxxxyyy";
	private int[] powers = { 10000, 1000, 100, 10, 1 };
	
	public String format(double x, double y, boolean abbrev) {
		if (x < 0 || y < 0)
			return x+","+y;
		// Find 500000m grid square for first letter
		//FIXME-why the +2 & +1? Need better documentation on OS grid
		int gx = (int)(x / 500000) + 2;
		int gy = (int)(y / 500000) + 1;
		if (0 <= gx && gx < 5 && 0 <= gy && gy < 5) {
			// Find 100000m grid square for first letter
			char c1 = (char)('A' + (4-gy)*5 + gx);
			if (c1 >= 'I')
				c1++;
			gx = (int)(x / 100000) % 5;
			gy = (int)(y / 100000) % 5;
			char c2 = (char)('A' + (4-gy)*5 + gx);
			if (c2 >= 'I')
				c2++;
			StringBuffer s = new StringBuffer();
			int length = osFormat.length();
			int xDigit = 0;
			int yDigit = 0;
			int ix = (int)Math.round(x);
			int iy = (int)Math.round(y);
			for (int i = 0; i < length; i++) {
				char c = osFormat.charAt(i);
				switch (c) {
				case 'X':
					s.append(c1);
					break;
				case 'Y':
					s.append(c2);
					break;
				case 'x':
					if (xDigit < 5)
						s.append((char)('0' + ((int)ix / powers[xDigit++]) % 10));
					break;
				case 'y':
					if (yDigit < 5)
						s.append((char)('0' + ((int)iy / powers[yDigit++]) % 10));
					break;
				default:
					s.append(c);
					break;
				}
			}
			return s.toString();
		}
		return x+","+y;
	}

	public Point2D.Double parse(String s) throws ParseException {
		s = s.trim();

		int length = s.length();
		if (length > 0 && Character.isDigit(s.charAt(0)))
			return super.parse(s);
		
		if (s.length() < 6)
			throw new ParseException("Invalid grid Reference", 0);
		char c1 = Character.toUpperCase(s.charAt(0));
		char c2 = Character.toUpperCase(s.charAt(1));

		if (c1 < 'A' || c1 > 'Z' || c1 == 'I' || c2 < 'A' || c2 > 'Z' || c2 == 'I')
			throw new ParseException("Invalid grid Reference: bad 2-letter code", 0);

		if (c1 >= 'J')
			c1--;
		c1 -= 'A';
		if (c2 >= 'J')
			c2--;
		c2 -= 'A';

		int x = 500000 * ((c1 % 5) - 2);
		int y = 500000 * ((4-(c1 / 5)) - 1);
		x += 100000 * (c2 % 5);
		y += 100000 * (4-(c2 / 5));

		int index = 2;
		while (index < length && s.charAt(index) == ' ')
			index++;
		length = length-index;
		if (length % 2 != 0)
			throw new ParseException("Invalid grid Reference: odd number of digits", 2);
		length /= 2;
		x += Double.valueOf(s.substring(index, index+length)).doubleValue() * powers[length-1];
		y += Double.valueOf(s.substring(index+length)).doubleValue() * powers[length-1];
		return new Point2D.Double(x, y);
	}
	
	public boolean isSingleGridReference() {
		return true;
	}

	public String getDefaultName() {
		return "UK National Grid";
	}	
}

