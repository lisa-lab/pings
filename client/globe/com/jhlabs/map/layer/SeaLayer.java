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
public class SeaLayer extends Layer {

	private Paint paint;
	
	public SeaLayer() {
		this( new Color(0xff4060cc) );
	}
	
	public SeaLayer( Paint paint ) {
		this.paint = paint;
	}
	
	public void paintContents( MapGraphics g ) {
		Graphics2D g2d = g.getGraphics2D();
		Projection projection = g.getProjection();
ProjectionPainter pp = ProjectionPainter.getProjectionPainter( projection );//FIXME
		Shape shape = pp.getBoundingShape();
		if ( paint != null ) {
			g2d.setPaint( paint );
			g2d.fill( shape );
		}
		Shape saveClip = g2d.getClip();
		g2d.clip( shape );
		paintLayers( g );
		paintFeatures( g );
		paintLayer( g );
		g2d.setClip( saveClip );
	}

	public String toString() {
		return "Sea";
	}
	
}
