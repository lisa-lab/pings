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

import java.awt.geom.*;
import com.jhlabs.map.*;

/**
 * An interface for any object which can appear in a map.
 */
public class MapObject {
	protected Style style;
	protected AffineTransform transform;
	protected Layer layer;
	protected boolean visible = true;

	public void setStyle( Style style ) {
		this.style = style;
	}
	
	public Style getStyle() {
		return style;
	}

	public void setTransform( AffineTransform transform ) {
		this.transform = transform;
	}
	
	public AffineTransform getTransform() {
		return transform;
	}

	public void setLayer( Layer layer ) {
		this.layer = layer;
	}
	
	public Layer getLayer() {
		return layer;
	}
	
	public Layer getMap() {
		return layer.getMap();
	}

	public void setVisible( boolean visible ) {
		this.visible = visible;
	}
	
	public boolean isVisible() {
		return visible;
	}

}
