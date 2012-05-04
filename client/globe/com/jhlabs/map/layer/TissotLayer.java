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
import java.util.*;
import com.jhlabs.map.*;
import com.jhlabs.map.proj.*;

/**
 * The interface defining a map layer
 */
public class TissotLayer extends Layer {

	public void paintLayer( MapGraphics g ) {
		Graphics2D g2d = g.getGraphics2D();
		AffineTransform transform = g2d.getTransform();
		Projection projection = g.getProjection();

		float latStep = 30;
		float lonStep = 30;
		float latMin = (float)projection.getMinLatitudeDegrees();
		float latMax = (float)projection.getMaxLatitudeDegrees();
		float lonMin = (float)projection.getMinLongitudeDegrees()+latStep/2;
		float lonMax = (float)projection.getMaxLongitudeDegrees()-latStep/2;

ProjectionPainter pp = ProjectionPainter.getProjectionPainter( projection );
		g2d.setPaint( Color.red );
		for ( float lat = latMin; lat <= latMax; lat += latStep ) {
			for ( float lon = lonMin; lon <= lonMax; lon += lonStep ) {
				GeneralPath gc = new GeneralPath();
				ProjectionPainter.smallCircle( lon, lat, 5, 180, gc, true );
				gc.closePath();
				pp.drawPath( g2d, gc, null, Color.red );
			}
		}
	}
	
	public String toString() {
		return "Tissot Indicatrix";
	}
}
