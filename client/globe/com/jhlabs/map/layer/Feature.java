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

public class Feature extends MapObject {

	private Shape shape;

	public Feature() {
	}
	
	public Feature( Shape shape, Style style ) {
		this.shape = shape;
		this.style = style;
	}
	
	public void setGeometry( Shape shape) {
		this.shape = shape;
	}
	
	public Shape getGeometry() {
		return shape;
	}

	public void paint( MapGraphics g ) {
		if ( isVisible() ) {
			Projection projection = g.getProjection();
			Style style = this.style != null ? this.style : g.getStyle();
ProjectionPainter pp = ProjectionPainter.getProjectionPainter( projection );//FIXME
			pp.drawPath( g.getGraphics2D(), shape, style.getStrokePaint(), style.getFillPaint() );
		}
	}

	public void setAttributes( Object attributes ) {
	}
	
	public Object getAttributes() {
		return null;
	}

	/**
	 * Get an individual attribute.
	 * @param feature the feature to get the attribute from
	 * @param the attribute name
	 * @return the attribute value
	 */
	public Object getAttribute(String name) {
		return null;
	}
	
	/**
	 * Set an individual attribute.
	 * @param feature the feature to get the attribute from
	 * @param the attribute name
	 * @param the attribute value
	 */
	public void setAttribute(String name, Object value) {
	}

}
