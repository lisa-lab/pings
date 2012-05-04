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
import com.jhlabs.image.*;
import com.jhlabs.map.proj.*;

/**
 * The interface defining a map layer
 */
public class ImageLayer extends Layer {

	private BufferedImage image;
	private double[] matrix;
	
	public ImageLayer() {
	}
	
	public ImageLayer( BufferedImage image ) {
		this.image = image;
	}
	
	public void paintLayer( MapGraphics g ) {
		Graphics2D g2d = g.getGraphics2D();
		Projection projection = g.getProjection();
		AffineTransform transform = g2d.getTransform();
ProjectionPainter pp = ProjectionPainter.getProjectionPainter( projection );//FIXME
		Shape framePath = pp.getBoundingShape();
//		Rectangle2D r = framePath.getBounds();
		Rectangle r = new Rectangle( -180, -90, 360, 180 );
		Rectangle2D bounds = projection.transform( r );
		BufferedImage projectedImage = new ProjectionFilter( projection, transform, r, bounds ).filter( image, null );
		Shape saveClip = g2d.getClip();
		g2d.setClip( framePath );
		g2d.drawImage( projectedImage, (int)r.getMinX(), (int)r.getMinY(), (int)r.getWidth(), (int)r.getHeight(), null );
		g2d.setClip( saveClip );
	}
	
	public String toString() {
		return "Image";
	}
}

class ProjectionFilter extends TransformFilter {

	private AffineTransform transform;
	private Projection projection;
	private Rectangle2D projectedBounds;
	private Rectangle bounds;
	private Point2D.Double src = new Point2D.Double();
	private Point2D.Double dst = new Point2D.Double();

	public ProjectionFilter( Projection projection, AffineTransform transform, Rectangle bounds, Rectangle2D projectedBounds ) {
		this.projection = projection;
		this.transform = transform;
		this.bounds = bounds;
		this.projectedBounds = projectedBounds;
	}

	protected void transformSpace(Rectangle rect) {
		rect.setFrame( projectedBounds );
	}

	protected void transformInverse(int x, int y, float[] out) {
		try {
			src.x = x;
			src.y = y;
			transform.inverseTransform( src, src );
			projection.inverseTransform( src, dst );
			if (bounds.contains(dst.x, dst.y)) {
				out[0] = (float)(originalSpace.width * (dst.x-bounds.x)/bounds.width);
				out[1] = (float)(originalSpace.height * (bounds.height-dst.y+bounds.y)/bounds.height);
			} else
				out[0] = out[1] = -1f;
		}
		catch (Exception e) {
			out[0] = out[1] = -1f;
		}
	}

}
