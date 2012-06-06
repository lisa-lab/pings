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

		int numPoints = Math.min(20,(int)(angle /(4* distance)));
		if (numPoints > 0) {
			double fraction = 1./ ((double) numPoints);
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
	 * Adds the points of a circle centred on clat/clon. Act as smallcircle but 
	 * come back to the center between each point.
	 */
    public final static void fractionnedCircle( float clon, float clat, float radius, int numPoints, GeneralPath path, boolean addMoveTo ) {
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
			else {
				path.lineTo( (float)lon, (float)lat );
				path.lineTo(clon, clat);
				path.lineTo( (float)lon, (float)lat );}
		}
    }
    
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

		//FIXME : this function doesn't give a correct output
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
		 * Takes a longitude and latitude in degree and output the coordinate of
		 * the point in cartesian system
		 * @param longitude
		 * @param latitude
		 * @return {x, y, z} (in earth radius)
		 */
		public double[] longLatToCartesian(double longitude,double latitude) {
			double[] point =  {
					Math.cos(MapMath.DTR * latitude) * Math.cos(MapMath.DTR * longitude ),
					Math.cos(MapMath.DTR * latitude) * Math.sin(MapMath.DTR * longitude),
					Math.sin(MapMath.DTR * latitude)
				};
			return point;
		}
		
		/**
		 * Takes a cartesian description of a point and store the corresponding 
		 * longitude  and latitude (in this order) in 'out'.
		 * 
		 * @param x
		 * @param y
		 * @param z
		 * @param out {longitude, latitude} in degree
		 */
		public void cartesianToLongLat(double x, double y, double z, double[] out) {
			double latR, longR;
			latR = Math.asin(z);
			if (Math.cos(z) == 0) longR = 0;
			else {
				longR = Math.acos( x / Math.cos(z) );
				if (y < 0) longR = -longR;
			}
			out[0] = MapMath.RTD * longR;
			out[1] = MapMath.RTD * latR;
		}
		
		/** 
		 * Return the angle (in radians) between two unit cartesian vectors
		 * @param x1
		 * @param x2
		 * @param y1
		 * @param y2
		 * @param z1
		 * @param z2
		 * @return the angle in radians between (x1,y1,y2) and (x2,y2,z2)
		 */
		public double getAngle(double x1,double y1,double z1,double x2,double y2,double z2) {
			return Math.acos(x1*x2 + y1*y2 + z1*z2);
		}
		
		/**
		 * Return the angle (in radians) between two (longitude,latitude)
		 * couples.
		 */
		public double getAngle(double lon1, double lat1, double lon2, double lat2) {
			double[] p1, p2;
			p1 =  longLatToCartesian(lon1,lat1);
			p2 = longLatToCartesian(lon2,lat2);
			return getAngle(p1[0],p1[1],p1[2],p2[0],p2[1],p2[2]);
		}
		
		/**
		 * A alternative function for findCrossingPoint.
		 * 
		 * The crossing points (there can be multiple if any) are characterized
		 * by there location on the intersection of the the geodesic(s) linking
		 * the two end points -located in the plane(s) passing by (p1,p2,center 
		 * of the earth)- and of the horizon circle -which is itself the 
		 * intersection of a certain plane and the unit sphere-.
		 * <p>
		 * This consideration is described as a system of three equations where 
		 * variables x,y and z describes the position of the crossing point in
		 * the usual cartesian representation. The system is described by 
		 * equations denoted as (1), (2) and (3).
		 * <p>
		 * There can be zero, one, two or an infinity of crossing points :
		 * there is either a single geodesic (the intersection of a plane and 
		 * the sphere that contain a shortest path between the two end points)
		 * or an infinity of geodesics if the two end points are diametrically 
		 * opposite.
		 * 
		 * In the latest case the function raise Exception("Diametrically 
		 * opposite")
		 * 
		 * In the first case there is a single geodesic and then the possible
		 * points come from the possible intersection of a circle with a plane :
		 * 
		 *  _ null : the entire path between the point is invisible from the
		 *  	current perspective.
		 *  	raise Exception("No point");
		 * 
		 * _ one point : the geodesic is tangent to the horizon
		 * 		store this single point in 'out' and raise Exception("Tangent")
		 * 
		 * _ two points : the geodesic crosses the horizon in two points
		 * 		store the point on the shortest path between the two end points
		 * 		in 'out' and return normally
		 * 
		 * _ the horizon circle : the two end points themselves are on the
		 * 		horizon. There was probably no need to call this function are 
		 * 		both points are visible.
		 * 		raise Exception("Full horizon")
		 * 
		 * @param lon1 the longitude of the first end point (in degree)
		 * @param lat1 the latitude of the first end point (in degree)
		 * @param lon2 the longitude of the second end point (in degree)
		 * @param lat2 the latitude of the second end point (in degree)
		 * @param projection the perspective
		 * @param out the point to output the result, if it is null an new array
		 * 	will be created
		 * @throws Exception 
		 */
		private void findCrossingPoint(double lon1, double lat1, double lon2, double lat2, Projection projection,double[] out) throws Exception {
			
			if (out == null) {out = new double[2];}
			
			double[] p_1, p_2, projcetion_center;
			p_1 = longLatToCartesian(lon1,lat1);
			p_2 = longLatToCartesian(lon2,lat2);
			projcetion_center = longLatToCartesian(
					MapMath.RTD*projection.getProjectionLongitude(),
					MapMath.RTD*projection.getProjectionLatitude()
				);
			
			//The equation of the plane passing by p_1 p_2 and the center of the
			//earth (containing the shortest path between p_1 and p_2) is :
			// 'as x + bs y + cs z = 0' (1)
			double as, bs, cs;
			as = p_1[1]*p_2[2] - p_1[2]*p_2[1];
			bs = p_1[2]*p_2[0] - p_1[0]*p_2[2];
			cs = p_1[0]*p_2[1] - p_1[1]*p_2[0];
			
			//The equation of the plane defining the horizon is :
			// 'ah x + bh y + ch z + dh= 0' (2)
			//TODO : modify it for most general cases where mapRadiusR != Pi
			double ah, bh, ch, dh;
			ah = projcetion_center[0];
			bh = projcetion_center[1];
			ch = projcetion_center[2];
			dh = 0;
			
			//The third and last equation of the system expresses that the point
			//is on the sphere :
			// 'x^2 + y^2 +z^2 = 1' (3)
			
			
			if (as != 0) {
				//Then (1) can be rewritten as :
				// 'x = ps y + qs z' (1') 
				double ps, qs;
				ps = -bs / as;
				qs = -cs / as;
				
				//thus (2) yields : '(ah ps + bh ) y =  -(ah qs + ch) z - dh'
				
				if ( ah*ps + bh != 0) {
					//We can then rewrite (2) as :
					// 'y = rh z + sh' (2')
					double rh, sh;
					rh = - (ah*qs + ch) / (ah*ps + bh);
					sh = - dh /(ah*qs + ch);
					
					//(3) can then be rewrite as :
					// '(ps (rh z + sh) +  qs z)^2 + (rh z + sh)^2 + z^2'
					// or 'a z²  + b z + c = 0 ' (3') with
					double a, b, c;
					a = (ps*rh + qs)*(ps*rh + qs) + rh*rh + 1;
					b = 2*(ps*rh + qs)*(ps*sh) + 2*rh*sh;
					c = (ps*sh)*(ps*sh) + sh*sh - 1;
					
					//We can do a classical resolution of (3')
					double delta = b*b - 4*a*c;
					
					if (delta < 0) {
						//Then there is no real solution for the z-coordinate of
						//the system.
						//This means that the path between the two end points is
						//invisible from the current perspective.
						throw new Exception("No point");
					}
					else if (delta == 0) {
						//Then all the crossing points share the same 
						//z-coordinate.which is
						double z = (-b) / (2*a);
						
						//From (2') :
						double y = rh*z + sh;
						
						//From (1') :
						double x =  ps*y + qs*z;
						
						//As there is a single point, we will return it and 
						//throw the 'Tangent' exception
						cartesianToLongLat(x,y,z,out);
						
						throw new Exception("Tangent");
					}
					else { //delta > 0
						//Then there are only two distinct possible z-coordinate
						//for the crossing points :
						double z1, z2, y1, y2, x1, x2;

						z1 = (-b + Math.sqrt(delta)) / (2*a);
						z2 = (-b - Math.sqrt(delta)) / (2*a);
						
						//From (2') :
						y1 = rh*z1 + sh;
						y2 = rh*z2 + sh;
						
						//From (1') :
						x1 =  ps*y1 + qs*z1;
						x2 =  ps*y2 + qs*z2;
						
						//We need to choose a point that allows for the 
						//shortest path (they might be both suitable).
						//We will consider the sum of principal angles between 
						//the vectors defined by the end point and the crossings
						//points.
						
						double angle1, angle2;
						angle1 = getAngle(p_1[0],p_1[1],p_1[2],x1,y1,z1) +
							getAngle(p_2[0],p_2[1],p_2[2],x1,y1,z1);
						angle2 = getAngle(p_1[0],p_1[1],p_1[2],x2,y2,z2) +
							getAngle(p_2[0],p_2[1],p_2[2],x2,y2,z2);
						
						if (angle1 <= angle2) {
							cartesianToLongLat(x1,y1,z1,out);
						}
						else {
							cartesianToLongLat(x2,y2,z2,out);
						}
						
						return;
					}
				}
				else { //If ah ps + bh = 0
					//Then (2) yields : '(ah qs + ch) z = - dh'
					
					if (ah*qs + ch != 0) {
						//TODO : implement case
						throw new Exception("case not implemented yet");
					}
					else { //If ah ps + bh = 0 and ah*qs + ch
						//Then (1) and (2) represent the same system and the 
						//crossing point is the full horizon
						
						throw new Exception("Full horizon");
					}
				}
				
			}
			else { //If as = 0 
				if (bs != 0) {
					//TODO : implement case
					throw new Exception("case not implemented yet");
				}
				else {//If as = bs = 0
					if (cs!= 0) {
						// Then we have 'cs z = 0' (1), i.e. 'z = 0' (1")
						
						double z = 0;
						
						//And (2) yields :'ah x = - bh y - dh'
						
						if (ah != 0 ) { // and  as = bs = 0, cs <> 0
							//TODO : implement case
							throw new Exception("case not implemented yet");							
						}
						else { //If as = bs = 0, cs <> 0, ah = 0
							//Then (2) yields 'bh y = - dh'
							
							if (bh != 0) {
								double y =  -dh / bh;
								
								//Then (3) rewrite as 'x² = a' (3")
								
								double a = 1 - (y * y);
								
								if (a < 0) {
									//Then there is no real solution for the x-coordinate of
									//the system.
									//This means that the path between the two end points is
									//invisible from the current perspective.
									throw new Exception("No point");
									
								}
								else if (a == 0) {
									double x = 0;
									//As there is a single point, we will return it and 
									//throw the 'Tangent' exception
									cartesianToLongLat(x,y,z,out);
									
									throw new Exception("Tangent");
								}
								else {//a > 0
									double x1, x2;
									
									//From (3") :
									x1 =  -Math.sqrt(a);
									x2 =  +Math.sqrt(a);
									
									//We need to choose a point that allows for the 
									//shortest path (they might be both suitable).
									//We will consider the sum of principal angles between 
									//the vectors defined by the end point and the crossings
									//points.
									
									double angle1, angle2;
									angle1 = getAngle(p_1[0],p_1[1],p_1[2],x1,y,z) +
										getAngle(p_2[0],p_2[1],p_2[2],x1,y,z);
									angle2 = getAngle(p_1[0],p_1[1],p_1[2],x2,y,z) +
										getAngle(p_2[0],p_2[1],p_2[2],x2,y,z);
									
									if (angle1 <= angle2) {
										cartesianToLongLat(x1,y,z,out);
									}
									else {
										cartesianToLongLat(x2,y,z,out);
									}
									
									return;
								}
							}
							else { //If as = bs = 0, cs <> 0, ah = bh = 0
								
								if (ch != 0) {
									//Then (1") yields 'z=0' while (2) states
									// that 'z = -dh / ch'
									
									if (dh != 0) {
										//Then (1) and (2) don't have an intersection
										//and there is no crossing point
										
										throw new Exception("No point");
									}
									else {//If (2) rewrite as 'z = 0 ' = (1")
										// Then the full horizon (which is in plane
										// z = 0) is the intersection
										
										throw new Exception("Full horizon");
										
									}
								}
								else {//If as = bs = 0, cs <> 0, ah = bh = ch = 0
									//Then (1) and (2) don't have an intersection
									//and there is no crossing point
									
									throw new Exception("No point");
								}
							}
						}
					}
					else {//if as = bs = cs = 0
						throw new Exception("Diametrically opposite");
					}
				}
			}
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
					//distanceFromCentre = MapMath.greatCircleDistance( rlon, rlat, projection.getProjectionLongitude(), projection.getProjectionLatitude() );
					distanceFromCentre = getAngle(MapMath.RTD*rlon, MapMath.RTD*rlat,
							MapMath.RTD*projection.getProjectionLongitude(), MapMath.RTD*projection.getProjectionLatitude());
					isOutside = distanceFromCentre >= mapRadiusR;
					if (wasOutside != isOutside) {
						// This segment crosses the horizon
						try {
							findCrossingPoint(MapMath.RTD*lastX, MapMath.RTD*lastY, MapMath.RTD*rlon, MapMath.RTD*rlat, projection,points);
						} catch (Exception e1) {
							// FIXME
						}
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

