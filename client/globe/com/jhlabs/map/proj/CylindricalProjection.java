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

package com.jhlabs.map.proj;

import java.awt.*;
import java.awt.geom.*;

/**
 * The superclass for all cylindrical projections.
 */
public class CylindricalProjection extends Projection {
	
	public boolean isRectilinear() {
		return true;
	}

	public String toString() {
		return "Cylindrical";
	}

	/**
	 * Pre-process a shape so that it gets split into left and right halves where it crosses
	 * from one side of the map to the other. This method does not deal with multiple moveTos
	 * or with curves.
	 */
/*
	public void drawPath( Graphics2D g, Shape shape, Paint strokePaint, Paint fillPaint ) {
		float lon0 = (float)Math.toDegrees(getProjectionLongitude());
		float left = normalizeLongitude(-180+lon0+1e-4f);	// Small delta to avoid rounding errors
		float right = normalizeLongitude(180+lon0-1e-4f);	// Small delta to avoid rounding errors
		PathIterator e = shape.getPathIterator(null);
		float[] points = new float[6];
		float lastX = 0, lastY = 0;
		GeneralPath r1 = new GeneralPath();
		GeneralPath r2 = new GeneralPath();
		GeneralPath r = r1;
//System.out.println("lon0 "+lon0);
		boolean moved2 = false;
		float moveX = 0, moveY = 0;

		while (!e.isDone()) {
			int type = e.currentSegment(points);
			switch (type) {
			case PathIterator.SEG_MOVETO:
				moveX = lastX = points[0];
				moveY = lastY = points[1];
				r1.moveTo( lastX, lastY );
//System.out.println("r1.moveto "+lastX+" "+lastY);
				r = r1;
				lastX = normalizeLongitude( lastX-lon0 );
				break;

			case PathIterator.SEG_CLOSE:
			case PathIterator.SEG_LINETO:
				float x = points[0];
				float y = points[1];
if ( type == PathIterator.SEG_CLOSE ) {
	x = moveX;
	y = moveY;
}
				x = normalizeLongitude( x-lon0 );
//System.out.println(" "+lastX+" "+x);
				if ( wrapAround( x, lastX ) ) {
					float crossY = (lastY + y)/2;// FIXME - need to calculate proper crossing point
//System.out.println("wrap "+x+" "+lastX);
					if ( x < lastX ) {
						// Gone off right edge
						if ( r == r1 ) {
							r1.lineTo( right, crossY );
							if ( moved2 ) r2.lineTo( left, crossY ); else r2.moveTo( left, crossY ); moved2 = true;
							r2.lineTo( x+lon0, y );
//System.out.println("a r1.lineto right "+crossY);
//System.out.println("b r2.moveto left "+crossY);
							r = r2;
						} else {
							if ( moved2 ) r2.lineTo( right, crossY ); else r2.moveTo( right, crossY ); moved2 = true;
//							r2.lineTo( right, crossY );
//							r1.moveTo( left, crossY );
							r1.lineTo( x+lon0, y );
//System.out.println("c r1.lineto left "+crossY);
//System.out.println("d r2.moveto right "+crossY);
							r = r1;
						}
					} else {
						// Gone off left edge
						if ( r == r1 ) {
							// FIXME - need to calculate proper crossing point
							r1.lineTo( left, crossY );
							if ( moved2 ) r2.lineTo( right, crossY ); else r2.moveTo( right, crossY ); moved2 = true;
//							r2.moveTo( right, crossY );
							r2.lineTo( x+lon0, y );
//System.out.println("e r1.lineto left "+crossY);
//System.out.println("f r2.moveto right "+crossY);
							r = r2;
						} else {
							if ( moved2 ) r2.lineTo( left, crossY ); else r2.moveTo( left, crossY ); moved2 = true;
//							r2.lineTo( left, crossY );
							r1.lineTo( right, crossY );
							r1.lineTo( x+lon0, y );
//System.out.println("g r2.lineto right "+crossY);
//System.out.println("h r1.lineto right "+crossY);
//System.out.println("k r1.lineto "+(x+lon0)+" "+y);
							r = r1;
						}
					}
				} else
{
					r.lineTo( x+lon0, y );
//if ( r == r1 )
//	System.out.println("i r1.lineto "+(x+lon0)+" "+y);
//else
//	System.out.println("j r2.lineto "+(x+lon0)+" "+y);
}
				lastX = x;
				lastY = y;
				break;

			case PathIterator.SEG_CUBICTO:
			case PathIterator.SEG_QUADTO:
				throw new ProjectionException( "Shape must not contain curves" );
			}
			e.next();
		}

if ( false && moved2 ) {
	printPath(shape);
	printPath(r1);
	printPath(r2);
}
		super.drawPath( g, r1, strokePaint, Color.red );
		if ( moved2 )
			super.drawPath( g, r2, strokePaint, Color.green );
	}
*/
}
