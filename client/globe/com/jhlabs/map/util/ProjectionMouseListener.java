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

package com.jhlabs.map.util;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;

import com.jhlabs.map.*;
import com.jhlabs.map.proj.*;

/**
 * A utility class which provides a virtual trackball for map projections.
 */
public class ProjectionMouseListener implements MouseListener, MouseMotionListener {

	private Component component;
	private Projection projection;
	private AffineTransform transform;
	private int lastX = 0;
	private int lastY = 0;

	public ProjectionMouseListener( Component component, Projection projection, AffineTransform transform ) {
		this.component = component;
		this.projection = projection;
		this.transform = transform;
	}
	
	public void setProjection( Projection projection ) {
		this.projection = projection;
	}
	
	public Projection getProjection() {
		return projection;
	}
	
	public void mousePressed( MouseEvent e ) {
		if ( e.isShiftDown() || e.isAltDown() || e.isMetaDown() )
			return;
		lastX = e.getX();
		lastY = e.getY();
		component.addMouseMotionListener( this );
	}
	
	public void mouseReleased( MouseEvent e ) {
		component.removeMouseMotionListener( this );
	}
	
	public void mouseClicked( MouseEvent e ) {
/*
		java.awt.geom.Point2D.Double in = new java.awt.geom.Point2D.Double( e.getX()-component.getWidth()/2, -(e.getY()-component.getHeight()/2) );
		java.awt.geom.Point2D.Double out = new java.awt.geom.Point2D.Double();
		try {
			projection.inverseTransform( in, out );
		}
		catch ( ProjectionException e ) {
		}
*/
	}
	
	public void mouseEntered( MouseEvent e ) {
	}
	
	public void mouseExited( MouseEvent e ) {
	}

	public void mouseDragged( MouseEvent e ) {
		int x = e.getX();
		int y = e.getY();
		double s = 0.01 / transform.getScaleX() ;
		projection.setProjectionLatitude( projection.getProjectionLatitude()+(y-lastY)*s );
		if ( Math.cos( projection.getProjectionLatitude() ) < 0 )
			s = -s;
		projection.setProjectionLongitude( MapMath.normalizeLongitude( projection.getProjectionLongitude()-(x-lastX)*s ) );
		projection.initialize();
		component.repaint();
		lastX = x;
		lastY = y;
	}
	
	public void mouseMoved( MouseEvent e) {
	}
	
}
