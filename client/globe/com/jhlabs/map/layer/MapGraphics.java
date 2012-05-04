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
import com.jhlabs.map.proj.*;

/**
 * The interface defining a graphics context for drawing on maps.
 */
public abstract class MapGraphics {

	private int quality;

	public static final int BEST_QUALITY = 0;
	public static final int INTERACTIVE_QUALITY = 1;

	public abstract void setGraphics2D( Graphics2D graphics );
	public abstract Graphics2D getGraphics2D();

	public abstract void setProjection( Projection projection );
	public abstract Projection getProjection();

	public abstract void setStyle( Style style );
	public abstract Style getStyle();

	public void setQuality( int quality ) {
		this.quality = quality;
	}

	public int getQuality() {
		return quality;
	}

	Rectangle renderRectangle;
	public Rectangle getRenderRectangle() {
		return renderRectangle;
	}

	public static MapGraphics getGraphics( Graphics2D g, Rectangle renderRectangle ) {
		return new BasicMapGraphics( g, renderRectangle );
	}
}

class BasicMapGraphics extends MapGraphics {

	private Graphics2D graphics;
	private Projection projection;
	private Style style;

	public BasicMapGraphics( Graphics2D graphics, Rectangle renderRectangle ) {
		this.graphics = graphics;
		this.renderRectangle = renderRectangle;
	}

	public void setGraphics2D( Graphics2D graphics ) {
		this.graphics = graphics;
	}
	
	public Graphics2D getGraphics2D() {
		return graphics;
	}

	public void setProjection( Projection projection ) {
		this.projection = projection;
	}
	
	public Projection getProjection() {
		return projection;
	}

	public void setStyle( Style style ) {
		this.style = style;
	}
	
	public Style getStyle() {
		return style;
	}

}
