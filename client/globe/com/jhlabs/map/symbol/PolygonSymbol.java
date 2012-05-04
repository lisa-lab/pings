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

public class PolygonSymbol implements Symbol {

	protected int sides;
	protected boolean fill;

	public PolygonSymbol(int sides, boolean fill) {
		this.sides = sides;
		this.fill = fill;
	}
	
	public void paintSymbol( Graphics2D g, int x, int y ) {
		Graphics2D g2d = (Graphics2D)g;
		GeneralPath p = new GeneralPath(GeneralPath.WIND_EVEN_ODD, sides+1);
		int radius = 8;
		p.moveTo(0, -radius);
		p = spinPath(0, 0, p, sides);
		g2d.translate(x, y);
		if (fill)
			g2d.fill(p);
		else
			g2d.draw(p);
		g2d.translate(-x, -y);
	}
	
	public GeneralPath spinPath(double centreX, double centreY, Shape s, int count) {
		GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD, count+1);

		for (int j = 0; j < count; j++) {
			double angle = -(2.0 * Math.PI * j) / count; 
			AffineTransform t = AffineTransform.getRotateInstance(angle, centreX, centreY);
			path.append(s.getPathIterator(t), true);
		}
		path.closePath();
		return path;
	}

	public int getSymbolWidth() {
		return 16;
	}
	
	public int getSymbolHeight() {
		return 16;
	}

	public int getSymbolXOrigin() {
		return -8;
	}
	
	public int getSymbolYOrigin() {
		return -8;
	}
}