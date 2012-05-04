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
public class GraticuleLayer extends Layer {

	public void paintLayer( MapGraphics g ) {
		Graphics2D g2d = g.getGraphics2D();
		AffineTransform transform = g2d.getTransform();
		Projection projection = g.getProjection();
		// Draw the graticule
		Point2D.Float p = new Point2D.Float( 1, 0 );
		transform.deltaTransform( p, p );
		float rscale = 1.0f/(float)Math.sqrt( p.x*p.x + p.y*p.y );
		g2d.setStroke( new BasicStroke( 0.5f*rscale ) );
ProjectionPainter pp = ProjectionPainter.getProjectionPainter( projection );//FIXME
		pp.drawGraticule( g2d, -75, 75, 15, Color.cyan, Color.red, -180, 180, 15, Color.cyan );
	}

	public String toString() {
		return "Graticule";
	}
}
