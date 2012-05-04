package com.jhlabs.map.util;

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

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

public class PanZoomMouseListener implements MouseListener, MouseMotionListener {

	private Component component;
	private AffineTransform transform;
	private AffineTransform saveTransform;
	private int lastX = 0;
	private int lastY = 0;
	private double startAngle;

	public PanZoomMouseListener( Component component, AffineTransform transform ) {
		this.component = component;
		this.transform = transform;
	}
	
	public void setTransform( AffineTransform transform ) {
		this.transform = transform;
	}
	
	public AffineTransform getTransform() {
		return transform;
	}
	
	public void mousePressed( MouseEvent e ) {
		if ( e.isShiftDown() || e.isAltDown() || e.isMetaDown() ) {
			lastX = e.getX();
			lastY = e.getY();
			startAngle = Math.atan2( component.getWidth()/2-lastX, lastY-component.getHeight()/2 );
			saveTransform = new AffineTransform( transform );
			component.addMouseMotionListener( this );
		}
        component.requestFocus();
	}
	
	public void mouseReleased( MouseEvent e ) {
		component.removeMouseMotionListener( this );
	}
	
	public void mouseClicked( MouseEvent e ) {
	}
	
	public void mouseEntered( MouseEvent e ) {
	}
	
	public void mouseExited( MouseEvent e ) {
	}

	public void mouseDragged( MouseEvent e ) {
		int x = e.getX();
		int y = e.getY();
		Point2D.Float p = new Point2D.Float( 1, 0 );
		transform.deltaTransform( p, p );
		float rscale = 1.0f/(float)Math.sqrt( p.x*p.x + p.y*p.y );
		double s = 0.001/**rscale*/;
		transform.setTransform( saveTransform );
		if ( e.isShiftDown() )
			transform.translate( rscale*(x-lastX), -rscale*(y-lastY) );
		else if ( e.isAltDown() )
			transform.scale( 1+(y-lastY)*s, 1+(y-lastY)*s );
		else if ( e.isMetaDown() )
			transform.rotate( Math.atan2( component.getWidth()/2-y, x-component.getHeight()/2 )-startAngle );
		component.repaint();
	}
	
	public void mouseMoved( MouseEvent e ) {
	}
	
}
