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

import java.io.*;
import java.util.*;
import java.awt.geom.*;
import com.jhlabs.map.*;

public class ProjectionFactory {

	private final static double SIXTH = .1666666666666666667; /* 1/6 */
	private final static double RA4 = .04722222222222222222; /* 17/360 */
	private final static double RA6 = .02215608465608465608; /* 67/3024 */
	private final static double RV4 = .06944444444444444444; /* 5/72 */
	private final static double RV6 = .04243827160493827160; /* 55/1296 */

	private static AngleFormat format = new AngleFormat( AngleFormat.ddmmssPattern, true );

	/**
	 * Return a projection initialized with a PROJ.4 argument list.
	 */
	static Projection fromPROJ4Specification( String[] args ) {
		Projection projection = null;
		Ellipsoid ellipsoid = null;
		double a = 0, b = 0, es = 0;

		Hashtable params = new Hashtable();
		for ( int i = 0; i < args.length; i++ ) {
			String arg = args[i];
			if ( arg.startsWith("+") ) {
				int index = arg.indexOf( '=' );
				if ( index != -1 ) {
					String key = arg.substring( 1, index );
					String value = arg.substring( index+1 );
					params.put( key, value );
				}
			}
		}

		String s;
		s = (String)params.get( "proj" );
		if ( s != null ) {
			projection = getNamedPROJ4Projection( s );
			if ( projection == null )
				throw new ProjectionException( "Unknown projection: "+s );
		}

		s = (String)params.get( "init" );
		if ( s != null ) {
			projection = getNamedPROJ4CoordinateSystem( s );
			if ( projection == null )
				throw new ProjectionException( "Unknown projection: "+s );
		}

		// Set the ellipsoid
		s = (String)params.get( "R" );
		if ( s != null ) 
			a = Double.parseDouble( s );
		else {
			s = (String)params.get( "ellps" );
			if ( s != null ) {
				Ellipsoid[] ellipsoids = Ellipsoid.ellipsoids;
				for ( int i = 0; i < ellipsoids.length; i++ ) {
					if ( ellipsoids[i].shortName.equals( s ) ) {
						ellipsoid = ellipsoids[i];
						break;
					}
				}
				if ( ellipsoid == null )
					throw new ProjectionException( "Unknown ellipsoid: "+s );
				es = ellipsoid.eccentricity2;
				a = ellipsoid.equatorRadius;
			} else {
				s = (String)params.get( "a" );
				if ( s != null ) 
					a = Double.parseDouble( s );
				s = (String)params.get( "es" );
				if ( s != null ) {
					es = Double.parseDouble( s );
				} else {
					s = (String)params.get( "rf" );
					if ( s != null ) {
						es = Double.parseDouble( s );
						es = es * (2. - es);
					} else {
						s = (String)params.get( "f" );
						if ( s != null ) {
							es = Double.parseDouble( s );
							es = 1.0 / es;
							es = es * (2. - es);
						} else {
							s = (String)params.get( "b" );
							if ( s != null ) {
								b = Double.parseDouble( s );
								es = 1. - (b * b) / (a * a);
							}
						}
					}
				}
				if ( b == 0 )
					b = a * Math.sqrt(1. - es);
			}

			s = (String)params.get( "R_A" );
			if ( s != null && Boolean.getBoolean( s ) ) {
				a *= 1. - es * (SIXTH + es * (RA4 + es * RA6));
			} else {
				s = (String)params.get( "R_V" );
				if ( s != null && Boolean.getBoolean( s ) ) {
					a *= 1. - es * (SIXTH + es * (RV4 + es * RV6));
				} else {
					s = (String)params.get( "R_a" );
					if ( s != null && Boolean.getBoolean( s ) ) {
						a = .5 * (a + b);
					} else {
						s = (String)params.get( "R_g" );
						if ( s != null && Boolean.getBoolean( s ) ) {
							a = Math.sqrt(a * b);
						} else {
							s = (String)params.get( "R_h" );
							if ( s != null && Boolean.getBoolean( s ) ) {
								a = 2. * a * b / (a + b);
								es = 0.;
							} else {
								s = (String)params.get( "R_lat_a" );
								if ( s != null ) {
									double tmp = Math.sin( parseAngle( s ) );
									if ( Math.abs(tmp) > MapMath.HALFPI )
										throw new ProjectionException( "-11" );
									tmp = 1. - es * tmp * tmp;
									a *= .5 * (1. - es + tmp) / ( tmp * Math.sqrt(tmp));
									es = 0.;
								} else {
									s = (String)params.get( "R_lat_g" );
									if ( s != null ) {
										double tmp = Math.sin( parseAngle( s ) );
										if ( Math.abs(tmp) > MapMath.HALFPI )
											throw new ProjectionException( "-11" );
										tmp = 1. - es * tmp * tmp;
										a *= Math.sqrt(1. - es) / tmp;
										es = 0.;
									}
								}
							}
						}
					}
				}
			}
		}
		projection.setEllipsoid( new Ellipsoid( "", a, es, "" ) );
		
		// Other arguments
		s = (String)params.get( "lat_0" );
		if ( s != null ) 
			projection.setProjectionLatitudeDegrees( parseAngle( s ) );
		s = (String)params.get( "lon_0" );
		if ( s != null ) 
			projection.setProjectionLongitudeDegrees( parseAngle( s ) );
		s = (String)params.get( "lat_ts" );
		if ( s != null ) 
			projection.setTrueScaleLatitudeDegrees( parseAngle( s ) );
		s = (String)params.get( "x_0" );
		if ( s != null ) 
			projection.setFalseEasting( Double.parseDouble( s ) );
		s = (String)params.get( "y_0" );
		if ( s != null ) 
			projection.setFalseNorthing( Double.parseDouble( s ) );

		s = (String)params.get( "k_0" );
		if ( s == null ) 
			s = (String)params.get( "k" );
		if ( s != null ) 
			projection.setScaleFactor( Double.parseDouble( s ) );

		s = (String)params.get( "units" );
		if ( s != null ) {
			Unit unit = Units.findUnits( s );
			if ( unit != null )
				projection.setFromMetres( unit.value );
		}
		s = (String)params.get( "to_meter" );
		if ( s != null ) 
			projection.setFromMetres( 1.0/Double.parseDouble( s ) );

//zone
//towgs84
//alpha
//datum
//lat_ts
//azi
//lonc
//rf
//pm

		projection.initialize();

		return projection;
	}

	private static double parseAngle( String s ) {
		return format.parse( s, null ).doubleValue();
	}
	
	static Hashtable registry;

	static void register( String name, Class cls, String description ) {
		registry.put( name, cls );
	}

	static Projection getNamedPROJ4Projection( String name ) {
		if ( registry == null )
			initialize();
		Class cls = (Class)registry.get( name );
		if ( cls != null ) {
			try {
				Projection projection = (Projection)cls.newInstance();
				if ( projection != null )
					projection.setName( name );
				return projection;
			}
			catch ( IllegalAccessException e ) {
			}
			catch ( InstantiationException e ) {
			}
		}
		return null;
	}
	
	static void initialize() {
		registry = new Hashtable();
		register( "aea", AlbersProjection.class, "Albers Equal Area" );
		register( "aeqd", EquidistantAzimuthalProjection.class, "Azimuthal Equidistant" );
		register( "airy", AiryProjection.class, "Airy" );
		register( "aitoff", AitoffProjection.class, "Aitoff" );
		register( "alsk", Projection.class, "Mod. Stereographics of Alaska" );
		register( "apian", Projection.class, "Apian Globular I" );
		register( "august", AugustProjection.class, "August Epicycloidal" );
		register( "bacon", Projection.class, "Bacon Globular" );
		register( "bipc", BipolarProjection.class, "Bipolar conic of western hemisphere" );
		register( "boggs", BoggsProjection.class, "Boggs Eumorphic" );
		register( "bonne", BonneProjection.class, "Bonne (Werner lat_1=90)" );
		register( "cass", CassiniProjection.class, "Cassini" );
		register( "cc", CentralCylindricalProjection.class, "Central Cylindrical" );
		register( "cea", Projection.class, "Equal Area Cylindrical" );
//		register( "chamb", Projection.class, "Chamberlin Trimetric" );
		register( "collg", CollignonProjection.class, "Collignon" );
		register( "crast", CrasterProjection.class, "Craster Parabolic (Putnins P4)" );
		register( "denoy", DenoyerProjection.class, "Denoyer Semi-Elliptical" );
		register( "eck1", Eckert1Projection.class, "Eckert I" );
		register( "eck2", Eckert2Projection.class, "Eckert II" );
//		register( "eck3", Eckert3Projection.class, "Eckert III" );
		register( "eck4", Eckert4Projection.class, "Eckert IV" );
		register( "eck5", Eckert5Projection.class, "Eckert V" );
//		register( "eck6", Eckert6Projection.class, "Eckert VI" );
		register( "eqc", PlateCarreeProjection.class, "Equidistant Cylindrical (Plate Caree)" );
		register( "eqdc", EquidistantConicProjection.class, "Equidistant Conic" );
		register( "euler", EulerProjection.class, "Euler" );
		register( "fahey", FaheyProjection.class, "Fahey" );
		register( "fouc", FoucautProjection.class, "Foucaut" );
		register( "fouc_s", FoucautSinusoidalProjection.class, "Foucaut Sinusoidal" );
		register( "gall", GallProjection.class, "Gall (Gall Stereographic)" );
//		register( "gins8", Projection.class, "Ginsburg VIII (TsNIIGAiK)" );
//		register( "gn_sinu", Projection.class, "General Sinusoidal Series" );
		register( "gnom", GnomonicAzimuthalProjection.class, "Gnomonic" );
		register( "goode", GoodeProjection.class, "Goode Homolosine" );
//		register( "gs48", Projection.class, "Mod. Stererographics of 48 U.S." );
//		register( "gs50", Projection.class, "Mod. Stererographics of 50 U.S." );
		register( "hammer", HammerProjection.class, "Hammer & Eckert-Greifendorff" );
		register( "hatano", HatanoProjection.class, "Hatano Asymmetrical Equal Area" );
//		register( "imw_p", Projection.class, "Internation Map of the World Polyconic" );
		register( "kav5", KavraiskyVProjection.class, "Kavraisky V" );
//		register( "kav7", Projection.class, "Kavraisky VII" );
//		register( "labrd", Projection.class, "Laborde" );
//		register( "laea", Projection.class, "Lambert Azimuthal Equal Area" );
		register( "lagrng", LagrangeProjection.class, "Lagrange" );
		register( "larr", LarriveeProjection.class, "Larrivee" );
		register( "lask", LaskowskiProjection.class, "Laskowski" );
		register( "lcc", LambertConformalConicProjection.class, "Lambert Conformal Conic" );
		register( "leac", LambertEqualAreaConicProjection.class, "Lambert Equal Area Conic" );
//		register( "lee_os", Projection.class, "Lee Oblated Stereographic" );
		register( "loxim", LoximuthalProjection.class, "Loximuthal" );
		register( "lsat", LandsatProjection.class, "Space oblique for LANDSAT" );
//		register( "mbt_s", Projection.class, "McBryde-Thomas Flat-Polar Sine" );
		register( "mbt_fps", MBTFPSProjection.class, "McBryde-Thomas Flat-Pole Sine (No. 2)" );
		register( "mbtfpp", MBTFPPProjection.class, "McBride-Thomas Flat-Polar Parabolic" );
		register( "mbtfpq", MBTFPQProjection.class, "McBryde-Thomas Flat-Polar Quartic" );
//		register( "mbtfps", Projection.class, "McBryde-Thomas Flat-Polar Sinusoidal" );
		register( "merc", MercatorProjection.class, "Mercator" );
//		register( "mil_os", Projection.class, "Miller Oblated Stereographic" );
		register( "mill", MillerProjection.class, "Miller Cylindrical" );
//		register( "mpoly", Projection.class, "Modified Polyconic" );
		register( "moll", MolleweideProjection.class, "Mollweide" );
		register( "murd1", Murdoch1Projection.class, "Murdoch I" );
		register( "murd2", Murdoch2Projection.class, "Murdoch II" );
		register( "murd3", Murdoch3Projection.class, "Murdoch III" );
		register( "nell", NellProjection.class, "Nell" );
//		register( "nell_h", Projection.class, "Nell-Hammer" );
		register( "nicol", NicolosiProjection.class, "Nicolosi Globular" );
		register( "nsper", PerspectiveProjection.class, "Near-sided perspective" );
//		register( "nzmg", Projection.class, "New Zealand Map Grid" );
//		register( "ob_tran", Projection.class, "General Oblique Transformation" );
//		register( "ocea", Projection.class, "Oblique Cylindrical Equal Area" );
//		register( "oea", Projection.class, "Oblated Equal Area" );
		register( "omerc", ObliqueMercatorProjection.class, "Oblique Mercator" );
//		register( "ortel", Projection.class, "Ortelius Oval" );
		register( "ortho", OrthographicAzimuthalProjection.class, "Orthographic" );
		register( "pconic", PerspectiveConicProjection.class, "Perspective Conic" );
		register( "poly", PolyconicProjection.class, "Polyconic (American)" );
//		register( "putp1", Projection.class, "Putnins P1" );
		register( "putp2", PutninsP2Projection.class, "Putnins P2" );
//		register( "putp3", Projection.class, "Putnins P3" );
//		register( "putp3p", Projection.class, "Putnins P3'" );
		register( "putp4p", PutninsP4Projection.class, "Putnins P4'" );
		register( "putp5", PutninsP5Projection.class, "Putnins P5" );
		register( "putp5p", PutninsP5PProjection.class, "Putnins P5'" );
//		register( "putp6", Projection.class, "Putnins P6" );
//		register( "putp6p", Projection.class, "Putnins P6'" );
		register( "qua_aut", QuarticAuthalicProjection.class, "Quartic Authalic" );
		register( "robin", RobinsonProjection.class, "Robinson" );
		register( "rpoly", RectangularPolyconicProjection.class, "Rectangular Polyconic" );
		register( "sinu", SinusoidalProjection.class, "Sinusoidal (Sanson-Flamsteed)" );
//		register( "somerc", Projection.class, "Swiss. Obl. Mercator" );
		register( "stere", StereographicAzimuthalProjection.class, "Stereographic" );
		register( "tcc", TCCProjection.class, "Transverse Central Cylindrical" );
		register( "tcea", TCEAProjection.class, "Transverse Cylindrical Equal Area" );
//		register( "tissot", TissotProjection.class, "Tissot Conic" );
		register( "tmerc", TransverseMercatorProjection.class, "Transverse Mercator" );
//		register( "tpeqd", Projection.class, "Two Point Equidistant" );
//		register( "tpers", Projection.class, "Tilted perspective" );
//		register( "ups", Projection.class, "Universal Polar Stereographic" );
//		register( "urm5", Projection.class, "Urmaev V" );
		register( "urmfps", URMFPSProjection.class, "Urmaev Flat-Polar Sinusoidal" );
		register( "utm", TransverseMercatorProjection.class, "Universal Transverse Mercator (UTM)" );
		register( "vandg", VanDerGrintenProjection.class, "van der Grinten (I)" );
//		register( "vandg2", Projection.class, "van der Grinten II" );
//		register( "vandg3", Projection.class, "van der Grinten III" );
//		register( "vandg4", Projection.class, "van der Grinten IV" );
		register( "vitk1", VitkovskyProjection.class, "Vitkovsky I" );
		register( "wag1", Wagner1Projection.class, "Wagner I (Kavraisky VI)" );
		register( "wag2", Wagner2Projection.class, "Wagner II" );
		register( "wag3", Wagner3Projection.class, "Wagner III" );
		register( "wag4", Wagner4Projection.class, "Wagner IV" );
		register( "wag5", Wagner5Projection.class, "Wagner V" );
//		register( "wag6", Projection.class, "Wagner VI" );
		register( "wag7", Wagner7Projection.class, "Wagner VII" );
		register( "weren", WerenskioldProjection.class, "Werenskiold I" );
//		register( "wink1", Projection.class, "Winkel I" );
//		register( "wink2", Projection.class, "Winkel II" );
		register( "wintri", WinkelTripelProjection.class, "Winkel Tripel" );
	}

	public static Projection readProjectionFile( String file, String name ) throws IOException {
		BufferedReader reader = new BufferedReader( new InputStreamReader( ProjectionFactory.class.getResourceAsStream( "/nad/"+file ) ) );
		StreamTokenizer t = new StreamTokenizer( reader );
		t.commentChar( '#' );
		t.ordinaryChars( '0', '9' );
		t.ordinaryChars( '.', '.' );
		t.ordinaryChars( '-', '-' );
		t.ordinaryChars( '+', '+' );
		t.wordChars( '0', '9' );
		t.wordChars( '\'', '\'' );
		t.wordChars( '"', '"' );
		t.wordChars( '_', '_' );
		t.wordChars( '.', '.' );
		t.wordChars( '-', '-' );
		t.wordChars( '+', '+' );
		t.wordChars( ',', ',' );
		t.nextToken();
		while ( t.ttype == '<' ) {
			t.nextToken();
			if ( t.ttype != StreamTokenizer.TT_WORD )	
				throw new IOException( t.lineno()+": Word expected after '<'" );
			String cname = t.sval;
			t.nextToken();
			if ( t.ttype != '>' )
				throw new IOException( t.lineno()+": '>' expected" );
			t.nextToken();
			Vector v = new Vector();
			String values = "";
			while ( t.ttype != '<' ) {
				if ( t.ttype == '+' )
					t.nextToken();
				if ( t.ttype != StreamTokenizer.TT_WORD )	
					throw new IOException( t.lineno()+": Word expected after '+'" );
				String key = t.sval;
				t.nextToken();
				if ( t.ttype == '=' ) {
					t.nextToken();
					if ( t.ttype != StreamTokenizer.TT_WORD )	
						throw new IOException( t.lineno()+": Value expected after '='" );
					String value = t.sval;
					t.nextToken();
					if ( key.startsWith("+") )
						v.add( key+"="+value );
					else
						v.add( "+"+key+"="+value );
				}
			}
			t.nextToken();
			if ( t.ttype != '>' )
				throw new IOException( t.lineno()+": '<>' expected" );
			t.nextToken();
			if ( cname.equals( name ) ) {
				String[] args = new String[v.size()];
				v.copyInto( args );
				reader.close();
				return fromPROJ4Specification( args );
			}
		}
		reader.close();
		return null;
	}
	
	static Projection getNamedPROJ4CoordinateSystem( String name ) {
		String[] files = {
			"world",
			"nad83",
			"nad27",
			"esri",
			"epsg",
		};
		try {
			for ( int i = 0; i < files.length; i++ ) {
				Projection projection = readProjectionFile( files[i], name );
				if ( projection != null )
					return projection;
			}
		}
		catch ( IOException e ) {
			e.printStackTrace();
		}
		return null;
	}

	public static void main( String[] args ) {
		Projection projection = ProjectionFactory.fromPROJ4Specification( args );
		if ( projection != null ) {
			System.out.println( projection.getPROJ4Description() );
			for ( int i = 0; i < args.length; i++ ) {
				String arg = args[i];
				if ( !arg.startsWith("+") && !arg.startsWith("-") ) {
					try {
						BufferedReader reader = new BufferedReader( new FileReader( new File( args[i] ) ) );
						Point2D.Double p = new Point2D.Double();
						String line;
						while ( (line = reader.readLine()) != null ) {
							StringTokenizer t = new StringTokenizer( line, " " );
							String slon = t.nextToken();
							String slat = t.nextToken();
							p.x = format.parse( slon, null ).doubleValue();
							p.y = format.parse( slat, null ).doubleValue();
							projection.transform( p, p );
							System.out.println( p.x+" "+p.y );
						} 
					}
					catch ( IOException e ) {
						System.out.println( "IOException: "+args[i]+": "+e.getMessage() );
					}
				}
			}
		} else
			System.out.println( "Can't find projection "+args[0] );
	}
}
