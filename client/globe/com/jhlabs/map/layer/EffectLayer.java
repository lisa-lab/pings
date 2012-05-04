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
import java.awt.image.*;
import com.jhlabs.map.*;

/**
 * A layer which applies an image-processing effect to its children.
 */
public class EffectLayer extends Layer {

	private BufferedImageOp filter;
	
	public EffectLayer() {
	}
	
	public EffectLayer( BufferedImageOp filter ) {
		this.filter = filter;
	}
	
	public void paintLayers( MapGraphics g ) {
		Graphics2D g2d = g.getGraphics2D();
		Rectangle r = g.getRenderRectangle();
		BufferedImage image = new BufferedImage( r.width, r.height, BufferedImage.TYPE_INT_ARGB );
		Graphics2D ig2d = image.createGraphics();
		ig2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON  );
		g.setGraphics2D( ig2d );
		AffineTransform transform = g2d.getTransform();
		ig2d.setTransform( transform );
		ig2d.setStroke( g2d.getStroke() );
		super.paintLayers( g );
		ig2d.dispose();
		g2d.setTransform( new AffineTransform() );//FIXME-original transform
		g2d.drawImage( image, filter, 0, 0 );
		g.setGraphics2D( g2d );
	}

	public String toString() {
		return "Effect: "+filter;
	}
}
