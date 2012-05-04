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
public class Layer extends MapObject {

	private Projection projection;
	private Composite composite;
	private ArrayList layers = new ArrayList();
	private ArrayList features = new ArrayList();

	public Layer() {
	}
	
	public Layer( Style style ) {
		this.style = style;
	}
	
	public void paint( MapGraphics g ) {
		if ( isVisible() ) {
			Graphics2D g2d = g.getGraphics2D();
			AffineTransform saveTransform = g2d.getTransform();
			Composite saveComposite = g2d.getComposite();
			Projection saveProjection = g.getProjection();
			Style saveStyle = g.getStyle();
			if ( composite != null )
				g2d.setComposite( composite );
			if ( transform != null )
				g2d.transform( transform );
			if ( style != null )
				g.setStyle( style );
			if ( projection != null )
				g.setProjection( projection );
			paintContents( g );
			g.setStyle( saveStyle );
			g.setProjection( saveProjection );
			g2d.setComposite( saveComposite );
			g2d.setTransform( saveTransform );
		}
	}

	public void paintContents( MapGraphics g ) {
		paintLayers( g );
		paintFeatures( g );
		paintLayer( g );
	}
	
	public void paintLayers( MapGraphics g ) {
		for ( Iterator it = getLayersIterator(); it.hasNext(); ) {
			Layer l = (Layer)it.next();
			l.paint( g );
		}
	}
	
	public void paintFeatures( MapGraphics g ) {
		for ( Iterator it = getFeaturesIterator(); it.hasNext(); ) {
			Feature f = (Feature)it.next();
			f.paint( g );
		}
	}
	
	public void paintLayer( MapGraphics g ) {
	}
	
	public void setLayers( ArrayList layers ) {
		this.layers = layers;
	}

	public ArrayList getLayers() {
		return layers;
	}

	public Iterator getLayersIterator() {
		return layers.iterator();
	}
	
	public void addLayer( Layer layer ) {
		layers.add( layer );
		layer.setLayer( this );
	}
	
	public void removeLayer( Layer layer ) {
		layers.remove( layer );
		layer.setLayer( null );
	}
	
	public void setFeatures( ArrayList features ) {
		this.features = features;
	}

	public ArrayList getFeatures() {
		return features;
	}

	public Iterator getFeaturesIterator() {
		return features.iterator();
	}

	public void addFeature( Feature feature ) {
		features.add( feature );
		feature.setLayer( this );
	}
	
	public void removeLayer( Feature feature ) {
		features.remove( feature );
		feature.setLayer( null );
	}
	
	public void setProjection( Projection projection ) {
		this.projection = projection;
	}

	public Projection getProjection() {
		return projection;
	}

	public void setComposite( Composite composite ) {
		this.composite = composite;
	}

	public Composite getComposite() {
		return composite;
	}

	public String toString() {
		return "Layer";
	}
}
