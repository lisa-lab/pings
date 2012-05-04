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

package com.jhlabs.map.symbol;

import java.awt.*;
import java.awt.geom.*;
import com.jhlabs.map.*;

public class CrossSymbol extends PolygonSymbol {
	public CrossSymbol(int sides) {
		super(sides, false);
	}
	
	public GeneralPath crossPath(double centreX, double centreY, float radius1, float radius2, int count) {
		GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD, count*2);
		
		for (int j = 0; j < count; j++) {
			double angle = -(2.0 * Math.PI * j) / sides; 
			float s = (float)Math.sin(angle);
			float c = (float)Math.cos(angle);
			path.moveTo(radius1*s, radius1*c);
			path.lineTo(radius2*s, radius2*c);
		}
		return path;
	}

	public void paintSymbol( Graphics2D g, int x, int y ) {
		Graphics2D g2d = (Graphics2D)g;
		GeneralPath p = crossPath(0, 0, 4, 8, sides);
		g2d.translate(x, y);
		g2d.draw(p);
		g2d.translate(-x, -y);
	}
}
