/*
 * Copyright (C) Jerry Huxtable 1998-2006. All rights reserved.
 */
package com.jhlabs.map;

import java.awt.*;
import java.awt.geom.*;
import com.jhlabs.map.*;
import com.jhlabs.map.proj.*;

public class ProjectionPainter {

	protected Projection projection;
	
	public static ProjectionPainter getProjectionPainter( Projection projection ) {
		if ( projection instanceof AzimuthalProjection )
			return new Azimuthal( projection );
		return new ProjectionPainter( projection );
	}
	
	private ProjectionPainter( Projection projection ) {
		this.projection = projection;
	}
	
	/**
	 * Returns a projected shape defining the bounds of the projection. Use this to draw the map background.
	 */
	public Shape getBoundingShape() {
		float w = (float)Math.toDegrees( projection.getMinLongitude()+projection.getProjectionLongitude()+0.001f);
		float e = (float)Math.toDegrees( projection.getMaxLongitude()+projection.getProjectionLongitude()-0.001f);
		float s = (float)Math.toDegrees( projection.getMinLatitude() );
		float n = (float)Math.toDegrees( projection.getMaxLatitude() );
		Point2D.Double p = new Point2D.Double();
		GeneralPath path = new GeneralPath();
		PathBuilder pb = new PathBuilder( path );
		if ( projection.isRectilinear() ) {
			pb.moveTo( w, s );
			pb.lineTo( w, n );
			pb.lineTo( e, n );
			pb.lineTo( e, s );
			path.closePath();
		} else {
			int count = 72;
			int i;
			float x, y;

			// The small deltas are to avoid rounding errors which cause points to incorrectly wrap to the opposite side
			w += 0.0001f;
			e -= 0.0001f;
			pb.moveTo( w, s );
			for (i = 0; i < count+1; i++) {
				x = w + i * ((e-w)/count) - 0.00005f;
				pb.lineTo( x, s );
			}
			for (i = 0; i < count+1; i++) {
				y = s + i * ((n-s)/count);
				pb.lineTo( e, y );
			}
			for (i = count; i >= 0; i--) {
				x = w + i * ((e-w)/count) - 0.00005f;
				pb.lineTo( x, n );
			}
			for (i = count; i >= 0; i--) {
				y = s + i * ((n-s)/count);
				pb.lineTo( w, y );
			}
			path.closePath();
		}
		return path;
	}
	
	public double getWidth( double y ) {
		return Math.PI;
	}
	
	/**
	 * Projects and clips the given shape, optionally transformed by the transform t.
	 */
	public Shape projectPath(Shape path, AffineTransform t, boolean filled) {
		double EPS = 1e-4;
		double projectionLongitude = projection.getProjectionLongitude();
		double projectionLatitude = projection.getProjectionLatitude();
		double minLongitude = projection.getMinLongitude();
		double minLatitude = projection.getMinLatitude();
		double maxLongitude = projection.getMaxLongitude();
		double maxLatitude = projection.getMaxLatitude();
		Shape p1 = clipForProjection( path, (float)(Math.toDegrees(projectionLongitude+minLongitude)+EPS), (float)(Math.toDegrees(projectionLongitude+maxLongitude)-EPS), (float)Math.toDegrees(maxLatitude), (float)Math.toDegrees(minLatitude), false, 0 );
		Shape p2 = clipForProjection( path, (float)(Math.toDegrees(projectionLongitude+minLongitude)+EPS), (float)(Math.toDegrees(projectionLongitude+maxLongitude)-EPS), (float)Math.toDegrees(maxLatitude), (float)Math.toDegrees(minLatitude), false, projectionLongitude < 0 ? -360 : 360 );
		if ( p1 != null ) {
			if ( p2 != null )
				((GeneralPath)p1).append( p2, false );
			return p1;
		} else
			return p2;
	}

	/**
	 * Clip a path against a rectangle using the Liang-Barsky clipping algorithm.
	 */
	protected final Shape clipForProjection( Shape in, float left, float right, float top, float bottom, boolean closed, float xOffset ) {
		float dx, dy, xin, xout, yin, yout, txin, tyin, tin1, tin2, txout, tyout, tout1;
		GeneralPath out = new GeneralPath( GeneralPath.WIND_EVEN_ODD, 1000 );
		PathBuilder pb = new PathBuilder( out );
		boolean anyIn = false;
		PathIterator e = in.getPathIterator(null);
		float[] points = new float[6];

		float moveX = 0, moveY = 0; // The initial input moveto
		float thisX = 0, thisY = 0; // The current input point in radians
		float lastX = 0, lastY = 0; // The previous input point in radians
		boolean haveMoveTo = false;

		while (!e.isDone()) {
			int type = e.currentSegment(points);
			switch (type) {
			case PathIterator.SEG_MOVETO:
				moveX = lastX = points[0];
				moveY = lastY = points[1];
				haveMoveTo = false;
				pb.end();
				moveX += xOffset;
				lastX += xOffset;
				break;

			case PathIterator.SEG_CLOSE:
				// This is the same as a lineTo the last moveTo point
				points[0] = moveX-xOffset;
				points[1] = moveY;
				// Fall into...

			case PathIterator.SEG_LINETO:
				thisX = points[0];
				thisY = points[1];
				thisX += xOffset;
				// Wrap at +/-180 degrees
				// except for the nasty special case to deal with Antarctica when it's drawn with a seam to the South Pole
				if ( Math.abs( thisX-lastX ) > 180 && thisY != -90 ) {
					if ( thisX < 0 )
						thisX += 360;
					else
						thisX -= 360;
				}
				dx = thisX-lastX;
				dy = thisY-lastY;

				if (dx > 0 || dx == 0 && lastX > right) {
					xin  = left;
					xout = right;
				} else {
					xin  = right;
					xout = left;
				}
				if (dy > 0 || dy == 0 && lastY > top) {
					yin  = bottom;
					yout = top;
				} else {
					yin  = top;
					yout = bottom;
				}

				txin = (dx != 0) ? (xin-lastX)/dx : Float.NEGATIVE_INFINITY;
				tyin = (dy != 0) ? (yin-lastY)/dy : Float.NEGATIVE_INFINITY;

				if (txin < tyin) {
					tin1 = txin;
					tin2 = tyin;
				} else {
					tin1 = tyin;
					tin2 = txin;
				}

				if (tin1 <= 1) {
					if (tin1 > 0) {
						pb.lineTo( xin, yin );
						anyIn = true;
					}
					if (tin2 <= 1) {
						if (dx != 0)
							txout = (xout - lastX) / dx;
						else
							txout = ((left <= lastX) && (lastX <= right)) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;

						if (dy != 0)
							tyout = (yout - lastY) / dy;
						else
							tyout = ((bottom <= lastY) && (lastY <= top)) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;

						tout1 = (txout < tyout) ? txout : tyout;

						if (tin2 > 0 || tout1 > 0) {
							if (tin2 <= tout1) {
								if (tin2 > 0) {
									if (txin > tyin)
										pb.lineTo( xin, lastY + (txin*dy) );
									else
										pb.lineTo( lastX + (tyin*dx), yin );
								}
								if (tout1 < 1) {
									if (txout < tyout)
										pb.lineTo( xout, lastY +(txout*dy) );
									else
										pb.lineTo( lastX +(tyout*dx), yout );
								} else {
									pb.lineTo( thisX, thisY );
									anyIn = true;
								}
							} else {
								if (txin > tyin)
									pb.lineTo( xin, yout );
								else
									pb.lineTo( xout, yin );
							}
						}
					}
				}
				if ( type == PathIterator.SEG_CLOSE && haveMoveTo )
					out.closePath();
				lastX = thisX;
				lastY = thisY;
				break;

			case PathIterator.SEG_CUBICTO:
			case PathIterator.SEG_QUADTO:
				break;
			}
			e.next();
		}

		if ( haveMoveTo )
			out.closePath();
		
		if (!anyIn)
			return null;

		return out;
	}

	public static float normalizeLongitude(float angle) {
		if ( Double.isInfinite(angle) || Double.isNaN(angle) )
			throw new IllegalArgumentException("Infinite longitude");
		while (angle > 180)
			angle -= 360;
		while (angle < -180)
			angle += 360;
		return angle;
	}

	public static double normalizeLongitudeRadians( double angle ) {
		if ( Double.isInfinite(angle) || Double.isNaN(angle) )
			throw new IllegalArgumentException("Infinite longitude");
		while (angle > Math.PI)
			angle -= MapMath.TWOPI;
		while (angle < -Math.PI)
			angle += MapMath.TWOPI;
		return angle;
	}

	class PathBuilder {
		private float lastX, lastY;
		private Point2D.Double p = new Point2D.Double();
		private GeneralPath path;
		private boolean haveMoveTo = false;

		public PathBuilder( GeneralPath path ) {
			this.path = path;
		}
		
		public void end() {
			haveMoveTo = false;
		}
		
		public void moveTo( float x, float y ) {
			p.x = lastX = x;
			p.y = lastY = y;
			projection.transform( p, p );
			x = (float)p.x;
			y = (float)p.y;

			haveMoveTo = true;
			path.moveTo( x, y );
		}
		
		public void lineTo( float x, float y ) {
			p.x = lastX = x;
			p.y = lastY = y;
			projection.transform( p, p );
			x = lastX = (float)p.x;
			y = lastY = (float)p.y;

			if ( !haveMoveTo ) {
				haveMoveTo = true;
				path.moveTo( x, y );
			} else
				path.lineTo( x, y );
		}
		
		public void closePath() {
			path.closePath();
			haveMoveTo = false;
		}

/*
		public void greatCircleTo( float x, float y ) {
			greatCircle( lastX, lastY, x, y, CIRCLE_TOLERANCE, !haveMoveTo );
			lastX = x;
			lastY = y;
		}

		public final void greatCircle( double lon1, double lat1, double lon2, double lat2, double distance, boolean addMoveTo ) {
			double x, y, c;
			x = Math.toRadians(lon1);
			y = Math.toRadians(lat1);
			c = Math.cos(y);
			double x1 = c * Math.cos(x);
			double y1 = c * Math.sin(x);
			double z1 = Math.sin(y);

			if ( addMoveTo )
				moveTo((float)lon1, (float)lat1);

			x = Math.toRadians(lon2);
			y = Math.toRadians(lat2);
			c = Math.cos(y);
			double x2 = c * Math.cos(x);
			double y2 = c * Math.sin(x);
			double z2 = Math.sin(y);

			double angle = MapMath.acos(x1*x2 + y1*y2 + z1*z2);

			if (angle == Math.PI)
				return;

			angle = Math.toDegrees(angle);

			int numPoints = (int)(angle / distance);
			if (numPoints > 0) {
				double fraction = distance / angle;
				for (int i = 1; i < numPoints; i++) {
					c = i * fraction;
					double d = 1 - c;
					double x3 = x1 * d + x2 * c;
					double y3 = y1 * d + y2 * c;
					double z3 = z1 * d + z2 * c;
					double length = Math.sqrt(x3*x3 + y3*y3 + z3*z3);
					if (length != 0.0) {
						length = 1.0 / length;
						x3 *= length;
						y3 *= length;
						z3 *= length;
					}
					y = Math.toDegrees(MapMath.asin(z3));
					x = Math.toDegrees(MapMath.atan2(y3, x3));
					lineTo((float)x, (float)y);
				}
			}

			lineTo((float)lon2, (float)lat2);
		}
*/
	}

	public static double CIRCLE_TOLERANCE = 1.0;

	/**
	 * Adds points along a great circle from lat/lon1 to lat/lon2. All angles are in degrees.
	 */
	public final static void greatCircle( double lon1, double lat1, double lon2, double lat2, GeneralPath path, double distance, boolean addMoveTo ) {
		double x, y, c;
		x = Math.toRadians(lon1);
		y = Math.toRadians(lat1);
		c = Math.cos(y);
		double x1 = c * Math.cos(x);
		double y1 = c * Math.sin(x);
		double z1 = Math.sin(y);

		if ( addMoveTo )
			path.moveTo((float)lon1, (float)lat1);

		x = Math.toRadians(lon2);
		y = Math.toRadians(lat2);
		c = Math.cos(y);
		double x2 = c * Math.cos(x);
		double y2 = c * Math.sin(x);
		double z2 = Math.sin(y);

		double angle = MapMath.acos(x1*x2 + y1*y2 + z1*z2);

		if (angle == Math.PI)
			return;

		angle = Math.toDegrees(angle);

		int numPoints = (int)(angle / distance);
		if (numPoints > 0) {
			double fraction = distance / angle;
			for (int i = 1; i < numPoints; i++) {
				c = i * fraction;
				double d = 1 - c;
				double x3 = x1 * d + x2 * c;
				double y3 = y1 * d + y2 * c;
				double z3 = z1 * d + z2 * c;
				double length = Math.sqrt(x3*x3 + y3*y3 + z3*z3);
				if (length != 0.0) {
					length = 1.0 / length;
					x3 *= length;
					y3 *= length;
					z3 *= length;
				}
				y = Math.toDegrees(MapMath.asin(z3));
				x = Math.toDegrees(MapMath.atan2(y3, x3));
				path.lineTo((float)x, (float)y);
			}
		}

		path.lineTo((float)lon2, (float)lat2);
	}

	/**
	 * Adds the points of a small circle centred on clat/clon. All angles are in degrees.
	 */
    public final static void smallCircle( float clon, float clat, float radius, int numPoints, GeneralPath path, boolean addMoveTo ) {
		clon *= MapMath.DTR;
		clat *= MapMath.DTR;
		radius *= MapMath.DTR;

		// This code is actually for the more general case of a centre and point on circumference, so....
		double plon = clon;
		double plat = clat+radius;
		double hclat = MapMath.HALFPI - clat;
		double hclon = MapMath.HALFPI - clon;
		double hplat = MapMath.HALFPI - plat;
		double cdelta = Math.cos( hclat ) * Math.cos( hplat ) + Math.sin( hclat ) * Math.sin( hplat ) * Math.cos( clon - plon );
		double delta = Math.acos( cdelta );
		double dd = MapMath.HALFPI - delta;
		double ct = Math.cos( hclat );
		double st = Math.sin( hclat );
		double cb = Math.cos( hclon );
		double sb = Math.sin( hclon );

		double increment = Math.PI*2/numPoints;
		double angle = 0;

		for ( int i = 0; i < numPoints; i++ ) {
			angle += increment;
			double x = Math.cos( dd )*Math.cos( angle );
			double y = Math.cos( dd )*Math.sin( angle );
			double z = Math.sin( dd );
			double xl = x*cb + y*sb*ct + z*sb*st;
			double yl = -x*sb + y*cb*ct + z*cb*st;
			double zl = -y*st + z*ct;
			double r = Math.sqrt( xl*xl + yl*yl );
			double lon = Math.atan2( yl, xl ) * MapMath.RTD;
			double lat = Math.atan2( zl, r ) * MapMath.RTD;
			if ( i == 0 && addMoveTo )
				path.moveTo( (float)lon, (float)lat );
			else
				path.lineTo( (float)lon, (float)lat );
		}
    }
    
    /**
	 * Draw a simple arc between (clon1,clat1) and (clon2,clat2)
	 */
    public final static void basicArc( float clon1, float clat1, float clon2, float clat2, GeneralPath path ) {
		path.moveTo(clon1, clat1 );
		path.lineTo(clon2, clat2 );
    }
	
	/**
	 * Project and draw a given Shape using the given stroke and fill paints. Pass a null Paint
	 * if you don't want the shape to be stroked or filled.
	 */
	public void drawPath( Graphics2D g, Shape shape, Paint strokePaint, Paint fillPaint ) {
		Shape p = projectPath( shape, null, true );
		if ( fillPaint != null ) {
			if ( p != null ) {
				g.setPaint( fillPaint );
				g.fill( p );
			}
		}
		if ( strokePaint != null ) {
//			p = projectPath( shape, null, false );
			if ( p != null ) {
				g.setPaint( strokePaint );
				g.draw( p );
			}
		}
	}

	public void drawGraticule( Graphics2D g, float latMin, float latMax, float latStep, Paint latPaint, Paint equatorPaint, float longMin, float longMax, float longStep, Paint longPaint ) {
		drawLatitudeLines( g, latMin, latMax, latStep, latPaint, equatorPaint );
		drawLongitudeLines( g, longMin, longMax, longStep, longPaint );
	}
	
	public void drawLatitudeLines( Graphics2D g, float latMin, float latMax, float latStep, Paint latPaint, Paint equatorPaint ) {
		for ( float lat = latMin; lat <= latMax; lat += latStep ) {
			GeneralPath line = new GeneralPath();
			line.moveTo( -180, lat );
			for ( float lon = -180; lon <= 180; lon += 15 )
				line.lineTo( lon, lat );
			Shape p = projectPath( line, null, false );
			if ( p != null ) {
				g.setPaint( lat == 0 ? equatorPaint : latPaint );
				g.draw( p );
			}
		}
	}
	
	public void drawLongitudeLines( Graphics2D g, float longMin, float longMax, float longStep, Paint longPaint ) {
		g.setPaint( longPaint );
		for ( float lon = longMin; lon < longMax; lon += longStep ) {
			GeneralPath line = new GeneralPath();
			float step;
			if ( projection.isRectilinear() )
				step = 90;
			else
				step = 5;
			float min = (float)(MapMath.RTD*projection.getMinLatitude());
			float max = (float)(MapMath.RTD*projection.getMaxLatitude());
			line.moveTo( lon, min+0.0001f );
			line.lineTo( lon, min+0.0001f );
			for ( float lat = min+step; lat < max; lat += step )
				line.lineTo( lon, lat );
			line.lineTo( lon, max );
			Shape p = projectPath( line, null, false );
			if ( p != null )
				g.draw( p );
		}
	}

	// Debugging method
	public static void printPath( Shape s ) {
		PathIterator e = s.getPathIterator(null);
		float[] points = new float[6];
		while (!e.isDone()) {
			int type = e.currentSegment(points);
			switch (type) {
			case PathIterator.SEG_MOVETO:
				System.out.println( "m "+points[0]+" "+points[1] );
				break;

			case PathIterator.SEG_CLOSE:
				System.out.println( "c" );
				break;

			case PathIterator.SEG_LINETO:
				System.out.println( "l "+points[0]+" "+points[1] );
				break;

			case PathIterator.SEG_CUBICTO:
				break;

			case PathIterator.SEG_QUADTO:
				break;
			}
			e.next();
		}
		System.out.println();
	}

	private static class Azimuthal extends ProjectionPainter {

		private Azimuthal( Projection projection ) {
			super( projection );
		}
		
		public Shape getBoundingShape() {
			double a = projection.getEquatorRadius();
            // FIXME - this should be a method of AzimuthalProjection, but it'll have to wait until the next PROJ release
            if ( projection instanceof EqualAreaAzimuthalProjection )
                a *= Math.sqrt( 2.0 );
            else if ( projection instanceof EquidistantAzimuthalProjection )
                a *= MapMath.HALFPI;
            else if ( projection instanceof StereographicAzimuthalProjection )
                a *= 2 * Math.cos(projection.getProjectionLatitude());
			return new Ellipse2D.Double( -a, -a, 2*a, 2*a );
		}

		// Be very careful with which things are in degrees and which in radians here!

		private boolean outside(double lon, double lat, double mapRadiusR) {
			return MapMath.greatCircleDistance(lon, lat, projection.getProjectionLongitude(), projection.getProjectionLatitude()) >= mapRadiusR;
		}

		// Careful! Arguments are in radians, output in degrees!
		private void findCrossingPoint(double lon1, double lat1, double lon2, double lat2, double dist1, double dist2, double[] out) {
			double delta = dist2 - dist1;
			double eps = ( Math.abs(delta) < 1e-8) ? 0.0 : (MapMath.HALFPI - dist1) / delta;
			double rlon = lon2 - lon1;
			if (Math.abs(rlon) > Math.PI)
				rlon = MapMath.takeSign(MapMath.TWOPI - Math.abs(rlon), -rlon);
			out[0] = MapMath.RTD*(lon1 + rlon * eps);
			out[1] = MapMath.RTD*(lat1 + (lat2 - lat1) * eps);
		}

		/**
		 * Projects and clips the given shape, optionally transformed by the transform t.
		 */
		public Shape projectPath(Shape path, AffineTransform t, boolean filled) {
			Point2D.Double in = new Point2D.Double(0, 0);
			Point2D.Double out = new Point2D.Double(0, 0);
			GeneralPath gp = new GeneralPath();
			double[] points = new double[6];
			PathIterator e = path.getPathIterator(t);

			double mapRadiusR = Math.toRadians( ((AzimuthalProjection)projection).getMapRadius() );

			boolean haveMoveTo = false;
			boolean isOutside = false;
			boolean anyInside = false;
			boolean hasExited = false;
			boolean hasEntered = false;
			boolean startedOutside = false;
			
			GeneralPath path2 = new GeneralPath();

			float mx = 0, my = 0; // The last output moveto
			float lx = 0, ly = 0; // The last output point
			double moveX = 0, moveY = 0; // The initial input moveto
			double lastX = 0, lastY = 0; // The previous input point in radians
			double exitX = 0, exitY = 0; // The last exit point
			double enterX = 0, enterY = 0; // The last entry point
			double firstEnterX = 0, firstEnterY = 0; // The first entry point
			double distanceFromCentre = 0, lastDistanceFromCentre = 0;

			while (!e.isDone()) {
				int type = e.currentSegment(points);
				switch (type) {
				case PathIterator.SEG_MOVETO:
					haveMoveTo = false;
					moveX = points[0];
					moveY = points[1];
					lastX = MapMath.DTR*points[0];
					lastY = MapMath.DTR*points[1];
					lx = (float)moveX;
					ly = (float)moveY;
					distanceFromCentre = lastDistanceFromCentre = MapMath.greatCircleDistance( lastX, lastY, projection.getProjectionLongitude(), projection.getProjectionLatitude() );
					isOutside = startedOutside = distanceFromCentre >= mapRadiusR;
					anyInside |= !isOutside;
					if (isOutside) {
						lx = ly = mx = my = 0;
					} else {
						path2.moveTo(mx = lx, my = ly);
						haveMoveTo = true;
					}
					hasEntered = hasExited = false;
					break;

				case PathIterator.SEG_CLOSE:
					// This is the same as a lineTo the last moveTo point
					points[0] = moveX;
					points[1] = moveY;
					// Fall into...

				case PathIterator.SEG_LINETO:
					boolean wasOutside = isOutside;
					double thisX = points[0];
					double thisY = points[1];
					double rlon = MapMath.DTR*thisX;
					double rlat = MapMath.DTR*thisY;
					distanceFromCentre = MapMath.greatCircleDistance( rlon, rlat, projection.getProjectionLongitude(), projection.getProjectionLatitude() );
					isOutside = distanceFromCentre >= mapRadiusR;
					if (wasOutside != isOutside) {
						// This segment crosses the horizon
						findCrossingPoint(lastX, lastY, rlon, rlat, lastDistanceFromCentre, distanceFromCentre, points);
						if (isOutside) {
							// We've just exited the visible hemisphere - draw to the horizon crossing point
							hasExited = true;
							exitX = points[0];
							exitY = points[1];
							if ( !haveMoveTo )
								path2.moveTo(mx = lx, my = ly);
							greatCircle( lx, ly, exitX, exitY, path2, CIRCLE_TOLERANCE, false );
							haveMoveTo = true;
							lx = (float)exitX;
							ly = (float)exitY;
						} else {
							// We've just entered the visible hemisphere
							enterX = points[0];
							enterY = points[1];
							if ( !hasEntered ) {
								firstEnterX = enterX;
								firstEnterY = enterY;
							}
							if ( hasExited ) {
								if ( filled ) {
									// Add points along the horizon
									if ( !haveMoveTo )
										path2.moveTo(mx = (float)exitX, my = (float)exitY);
									greatCircle( exitX, exitY, enterX, enterY, path2, CIRCLE_TOLERANCE, false );
								} else {
									path2.moveTo(mx = (float)enterX, my = (float)enterY);
								}
								greatCircle( enterX, enterY, thisX, thisY, path2, CIRCLE_TOLERANCE, false);
								haveMoveTo = true;
								lx = (float)thisX;
								ly = (float)thisY;
							} else {
								if ( !haveMoveTo )
									path2.moveTo(mx = (float)points[0], my = (float)points[1]);
								greatCircle( mx, my, lx = (float)thisX, ly = (float)thisY, path2, CIRCLE_TOLERANCE, false );
								haveMoveTo = true;
							}
							hasEntered = true;
						}
						if (isOutside && !filled)
							haveMoveTo = false;
						anyInside = true;
					} else if (!isOutside) {
						// Simple case: the segment is totally visible
						anyInside = true;
						if ( !haveMoveTo )
							path2.moveTo( mx = lx = (float)moveX, my = ly = (float)moveY );
						haveMoveTo = true;
						greatCircle(lx, ly, thisX, thisY, path2, CIRCLE_TOLERANCE, false);
						lx = (float)thisX;
						ly = (float)thisY;
					}
					if ( isOutside && type == PathIterator.SEG_CLOSE ) {
						if ( filled && startedOutside && hasExited && hasEntered ) {
							// Special case for where both the first and last points are invisible - add in the horizon
							if ( haveMoveTo )
								greatCircle( exitX, exitY, firstEnterX, firstEnterY, path2, CIRCLE_TOLERANCE, false );
						}
					}
					if ( haveMoveTo && type == PathIterator.SEG_CLOSE )
						path2.closePath();
					
					lastX = rlon;
					lastY = rlat;
					lastDistanceFromCentre = distanceFromCentre;
					break;

				case PathIterator.SEG_CUBICTO:
				case PathIterator.SEG_QUADTO:
					throw new IllegalArgumentException("Curves are not allowed in paths");
				}
				e.next();
			}

			if (!anyInside)
				return null;

			// Now project the path
			e = path2.getPathIterator((AffineTransform)null);
			haveMoveTo = false;
			
			while (!e.isDone()) {
				int type = e.currentSegment(points);
				switch (type) {
				case PathIterator.SEG_MOVETO:
					in.x = points[0];
					in.y = points[1];
					try {
						projection.transform(in, out);
						gp.moveTo((float)out.x, (float)out.y);
						haveMoveTo = true;
					}
					catch (ProjectionException ex) {
						//System.out.println("ProjectionException m: "+in.x+","+in.y);
					}
					break;

				case PathIterator.SEG_CLOSE:
					if (haveMoveTo)
						gp.closePath();
					haveMoveTo = false;
					break;

				case PathIterator.SEG_LINETO:
					in.x = points[0];
					in.y = points[1];
					try {
						projection.transform(in, out);
						if (haveMoveTo) {
							gp.lineTo((float)out.x, (float)out.y);
						} else {
							gp.moveTo((float)out.x, (float)out.y);
							haveMoveTo = true;
						}
					}
					catch (ProjectionException ex) {
						//System.out.println("ProjectionException l: "+in.x+","+in.y);
					}
					break;

				case PathIterator.SEG_CUBICTO:
					break;

				case PathIterator.SEG_QUADTO:
					break;
				}
				e.next();
			}

			return gp;
		}
	}

}

