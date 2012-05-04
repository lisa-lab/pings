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
public class BackgroundLayer extends Layer {

	public void paintLayer( MapGraphics g ) {
		Graphics2D g2d = g.getGraphics2D();
		Rectangle r = g.getRenderRectangle();
//		g2d.setPaint( new GradientPaint( 0, r.y, Color.black, 0, r.y+r.height, Color.darkGray ) );
		g2d.setPaint( Color.black );
		g2d.fill( g2d.getClipBounds() );
	}
	
	public String toString() {
		return "Background";
	}
}
