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

import java.util.*;
import java.util.List;
import java.io.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.print.*;
import java.beans.*;
import javax.swing.*;
import javax.imageio.*;
import com.jhlabs.map.*;
import com.jhlabs.map.proj.*;
import com.jhlabs.map.util.*;
import com.jhlabs.map.shapefile.*;
import com.jhlabs.map.layer.*;
import com.jhlabs.dbf.*;

/**
 * A component which displays a globe.
 */
public class Globe extends JComponent {

	protected Graphics2D g2;
	private float globeRadius = 275;
	protected Projection projection;
	private boolean showSea = true;
	private boolean showWorld = true;
	private boolean showGraticule = true;
	private boolean showNight = false;
	private boolean showTest = false;
	private boolean showTissot = false;
	private ProjectionMouseListener mouseListener;
	private PanZoomMouseListener zoomListener;
	private AffineTransform transform = new AffineTransform();
	private BufferedImage image;

	private Layer map;
	private Layer mapLayer;
	private Layer worldLayer;
	private Layer graticuleLayer;
	private Layer seaLayer;
	private TissotLayer tissotLayer;
	private Layer linkLayer;
	private Layer selectedLayer;
	private Style style = new Style( Color.black, Color.green );
//	private float hue = 0.3f;
//	private Style[] styles = {
//		new Style( Color.black, Color.getHSBColor( hue, 0.5f, 1.0f ) ),
//		new Style( Color.black, Color.getHSBColor( hue+0.05f, 0.5f, 1.0f ) ),
//		new Style( Color.black, Color.getHSBColor( hue+0.1f, 0.5f, 1.0f ) ),
//		new Style( Color.black, Color.getHSBColor( hue+0.15f, 0.5f, 1.0f ) )
//	};
//	private Style[] styles = {
//			new Style( null, Color.getHSBColor( hue, 0.5f, 1.0f ) ),
//			new Style( null, Color.getHSBColor( hue+0.3f, 0.5f, 1.0f ) ),
//			new Style( null, Color.getHSBColor( hue+0.6f, 0.5f, 1.0f ) ),
//			new Style( null, Color.getHSBColor( hue+0.9f, 0.5f, 1.0f ) )
//	};
	private Style[] styles = {
			new Style( Color.white,new Color( 105f/255f, 143f/255f, 183f/255f, 1.0f ) ),
			new Style( Color.white,new Color( 161f/255f, 193f/255f, 226f/255f, 1.0f ) ),
			new Style( Color.white,new Color( 27f/255f, 84f/255f, 143f/255f, 1.0f ) ),
			new Style( Color.white,new Color( 36f/255f, 56f/255f, 91f/255f, 1.0f ) ),
			};

	public Globe() {
		// Create the map projection
		projection = new OrthographicAzimuthalProjection();
		// The easiest way to scale the projection is to change the Earth's radius. We could also use an AffineTransform.
		projection.setEllipsoid( new Ellipsoid("", globeRadius, globeRadius, 0, "") );
		projection.initialize();

		map = new Layer();
		map.setProjection( projection );
		map.setTransform( transform );
		map.addLayer( new BackgroundLayer() );
		map.addLayer( mapLayer = new Layer() );
		mapLayer.addLayer( seaLayer = new SeaLayer() );
		try {
//			Layer shadowLayer = new ShadowLayer();
//			Layer lightLayer = new EffectLayer( new com.jhlabs.image.LightFilter() );
//			map.addLayer( shadowLayer );
//			shadowLayer.addLayer( lightLayer );
//			map.addLayer( worldLayer = new ShapefileLayer( getClass().getResource("world.shp"), style ) );
			SeaLayer seaLayer2;
			mapLayer.addLayer( seaLayer2 = new SeaLayer( null ) );
			seaLayer2.addLayer( worldLayer = new ShapefileLayer( getClass().getResource("globe/world.shp"), getClass().getResource("globe/world.dbf"), style ) );
//			mapLayer.addLayer( worldLayer = new ShapefileLayer( new File("out.shp").toURL(), new Style( Color.black, null ) ) );
		}
		catch ( IOException e ) {
			e.printStackTrace();
		}
		mapLayer.addLayer( graticuleLayer = new GraticuleLayer() );
		mapLayer.addLayer( tissotLayer = new TissotLayer() );
		tissotLayer.setComposite( AlphaComposite.getInstance( AlphaComposite.SRC_OVER, 0.5f) );
		linkLayer = new LinkLayer( worldLayer );
//		createInterruptedMap();
//		map.addLayer( linkLayer );
		AffineTransform t = new AffineTransform();
//		t.scale( 0.5f, 0.5f );
		t.translate( 100, 100 );
		linkLayer.setTransform( t );
		linkLayer.setStyle( new Style( Color.black, Color.orange ) );
		Projection lp = new OrthographicAzimuthalProjection();
		linkLayer.setProjection( lp );
		lp.setEllipsoid( new Ellipsoid("", globeRadius, globeRadius, 0, "") );
		lp.initialize();

		int index = 0;
		for ( Iterator it = worldLayer.getFeaturesIterator(); it.hasNext(); ) {
			Feature feature = (Feature)it.next();
			if ( !(feature instanceof PointFeature) )//FIXME
				feature.setStyle( styles[index++ % styles.length] );
		}

		// Add the virtual trackball
		addMouseListener( mouseListener = new ProjectionMouseListener( this, projection ) );
		addMouseListener( zoomListener = new PanZoomMouseListener( this, transform ) );
		selectLayer( map );
	}
	
	public void createInterruptedMap() {
		AffineTransform t;
		LinkLayer l1 = new LinkLayer( mapLayer );
		Point2D.Double in = new Point2D.Double();
		Point2D.Double out = new Point2D.Double();
		Point2D.Double out0 = new Point2D.Double();
		Projection p = new SinusoidalProjection();
		p.setEllipsoid( new Ellipsoid("", globeRadius, globeRadius, 0, "") );
		p.setMinLongitudeDegrees( -90 );
		p.setMaxLongitudeDegrees( 75 );
		p.setProjectionLongitudeDegrees( -90 );
		in.x = -20;
		in.y = 0;
		p.transform(in, out);
		p.setFalseEasting( out0.x-out.x );
		l1.setProjection( p );
		t = new AffineTransform();
		t.translate( 0, -150 );
		t.scale( 0.5, 0.5 );
		l1.setTransform( t );

		LinkLayer l2 = new LinkLayer( mapLayer );
		p = new SinusoidalProjection();
		p.setEllipsoid( new Ellipsoid("", globeRadius, globeRadius, 0, "") );
		p.setMinLongitudeDegrees( -85 );
		p.setMaxLongitudeDegrees( 120 );
		p.setProjectionLongitudeDegrees( 65 );
		in.x = -20;
		in.y = 0;
		p.transform(in, out);
		p.setFalseEasting( out0.x-out.x );
		l2.setProjection( p );
		l2.setTransform( t );

		map.addLayer( l1 );
		map.addLayer( l2 );
	}

	public Layer getMap() {
		return map;
	}
	
	public void selectLayer( Layer layer ) {
		selectedLayer = layer;
		if ( layer.getProjection() != null )
			mouseListener.setProjection( layer.getProjection() );
		if ( layer.getTransform() != null )
			zoomListener.setTransform( layer.getTransform() );
	}
	
	public void setShowWorld( boolean showWorld ) {
		this.showWorld = showWorld;
	}
	
	public boolean getShowWorld() {
		return showWorld;
	}
	
	public void setShowGraticule( boolean showGraticule ) {
		this.showGraticule = showGraticule;
	}
	
	public boolean getShowGraticule() {
		return showGraticule;
	}
	
	public void setShowNight( boolean showNight ) {
		this.showNight = showNight;
	}
	
	public boolean getShowNight() {
		return showNight;
	}
	
	public void setShowSea( boolean showSea ) {
		this.showSea = showSea;
	}
	
	public boolean getShowSea() {
		return showSea;
	}
	
	public void setShowTissot( boolean showTissot ) {
		this.showTissot = showTissot;
	}
	
	public boolean getShowTissot() {
		return showTissot;
	}
	
	public void setProjection( Projection projection ) {
		this.projection = projection;
		if ( projection != null ) {
			projection.setEllipsoid( new Ellipsoid("", globeRadius, globeRadius, 0, "") );
			projection.initialize();
		}
		if ( selectedLayer != null )
			selectedLayer.setProjection( projection );
		else
			map.setProjection( projection );
		mouseListener.setProjection( projection );
	}
	
	public Projection getProjection() {
		return projection;
	}
	
	public void print() {
		try {
			PrinterJob printJob = PrinterJob.getPrinterJob();
			printJob.setPrintable( new Printable() {
				public int print(Graphics g, PageFormat pageFormat, int pageIndex) {
					if (pageIndex > 0) {
						return(NO_SUCH_PAGE);
					} else {
						Graphics2D g2d = (Graphics2D)g;
						g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
						g2d.translate(200, 300);
						g2d.scale( 0.6f, 0.6f );
						// Turn off double buffering
						paint(g2d);
						// Turn double buffering back on
						return(PAGE_EXISTS);
					}
				}
			});
			if (printJob.printDialog())
				try { 
					printJob.print();
				} catch(PrinterException pe) {
					System.out.println("Error printing: " + pe);
				}
		}
		catch ( Exception e ) {
			e.printStackTrace();
		}
	}
	
	public void paint( Graphics g ) {
		g2 = (Graphics2D)g;

		// Turn on antialiasing - otherwise it looks horrible
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON  );

		// Put the origin at bottom left
		g2.translate( 0, getHeight() );
		g2.scale( 1, -1 );

		// Put the globe in the middle
		g2.translate( getWidth()/2, getHeight()/2 );
		
		Point2D.Float p = new Point2D.Float( 1, 0 );
		transform.deltaTransform( p, p );
		float rscale = 1.0f/(float)Math.sqrt( p.x*p.x + p.y*p.y );
		g2.setStroke( new BasicStroke( rscale*0.5f ) );
		
		MapGraphics mg = MapGraphics.getGraphics( g2, new Rectangle( getSize() ) );
		seaLayer.setVisible( showSea );
		tissotLayer.setVisible( showTissot );
		worldLayer.setVisible( showWorld );
		graticuleLayer.setVisible( showGraticule );
		map.paint( mg );

		if ( showNight ) {
			Color c = new Color(1f, 0, 0, 0.15f);
			GeneralPath gc = new GeneralPath();
			ProjectionPainter.smallCircle( 45, 5, 87, 180, gc, true );
			gc.closePath();
            ProjectionPainter pp = ProjectionPainter.getProjectionPainter( projection );//FIXME
			pp.drawPath( g2, gc, null, c );
		}

	}
	
	public Dimension getPreferredSize() {
		return new Dimension( 1000, 700 );
	}

}

