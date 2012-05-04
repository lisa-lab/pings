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

package com.jhlabs.map.layer;

import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import com.jhlabs.map.*;
import com.jhlabs.map.proj.*;

public class PointFeature extends Feature {

	private Point2D.Double point;
	private String text;

	public PointFeature() {
	}
	
	public PointFeature( double x, double y, String text, Style style ) {
		this( new Point2D.Double(x, y), text, style );
	}
	
	public PointFeature( Point2D.Double point, String text, Style style ) {
		this.point = point;
		this.text = text;
		this.style = style;
	}
	
	public void paint( MapGraphics g ) {
		if ( isVisible() ) {
			Projection projection = g.getProjection();
			Style style = this.style != null ? this.style : g.getStyle();
			if ( projection.inside( point.x, point.y ) ) {
				Graphics2D g2d = g.getGraphics2D();
				Point2D.Double p = new Point2D.Double( point.getX(), point.getY() );
				projection.transform( point, p );
				AffineTransform t = g2d.getTransform();
				g2d.translate( p.getX(), p.getY() );
				Symbol symbol = style.getSymbol();
				if ( symbol != null ) {
					Paint paint;
					paint = style.getSymbolPaint();
					if ( paint != null ) {
						g2d.setPaint( paint );
						if ( !style.getScaleSymbol() ) {
							double scale = style.getSymbolSize() / t.getScaleX() / symbol.getSymbolHeight();
							g2d.scale(scale, scale);
							symbol.paintSymbol( g2d, 0, 0 );
							g2d.setTransform( t );
							g2d.translate( p.getX(), p.getY() );
						} else
							symbol.paintSymbol( g2d, 0, 0 );
					}
				}
				if ( text != null ) {
					Paint paint = style.getTextPaint();
					if ( paint != null ) {
						g2d.scale( 1, -1 );
						g2d.setPaint( paint );
						g2d.setFont( style.getFont() );
						if ( !style.getScaleText() ) {
							FontMetrics fm = g2d.getFontMetrics();
							double scale = style.getTextSize() / t.getScaleX() / (fm.getAscent()+fm.getDescent());
							g2d.scale(scale, scale);
							g2d.drawString( text, 0, 0 );
							g2d.setTransform( t );
						} else
							g2d.drawString( text, 0, 0 );
					}
				}
				g2d.setTransform( t );
			}
		}
	}

}
