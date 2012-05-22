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
import java.io.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.*;
import java.awt.image.*;
import java.awt.print.*;
import javax.swing.*;
import com.jhlabs.map.*;
import com.jhlabs.map.proj.*;
import com.jhlabs.map.util.*;
import com.jhlabs.map.layer.*;

/**
 * A component which displays a globe.
 */
public class Globe extends JComponent {
	
	protected static long quality_paint_delay = 100;
	private BufferedImage buffered_image;
	private boolean quality_image_buffered = false;
	private long last_time_update = 0;
	private Color day_color = new Color(1f, 0, 0, 0.10f);
	private double max_zoom = 20;
	private double min_zoom = 0.6;
	
	protected Graphics2D g2;
	private float globeRadius = 275;
	protected Projection projection;
	private boolean showSea = true;
	private boolean showWorld = true;
	private boolean showGraticule = true;
	private boolean showDay = false;
	private boolean showTest = false;
	private boolean showTissot = false;
	private ProjectionMouseListener mouseListener;
	private PanZoomMouseListener zoomListener;
	protected AffineTransform transform = new AffineTransform();
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
	private Projection last_projection;
	private AffineTransform last_transform;

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
			seaLayer2.addLayer( worldLayer = new ShapefileLayer( getClass().getResource("world.shp"), getClass().getResource("world.dbf"), style ) );
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
		
		//Add the mousewheel listener
		addMouseWheelListener(
			new MouseWheelListener() {
				public void mouseWheelMoved(MouseWheelEvent e) {
					int steps = e.getWheelRotation();
					mouseZoom(-steps);
				}
		    }
		);
		//selectLayer( map );
	}
	
	public void mouseZoom(int steps) {
		double zoom_factor = Math.pow(1.05, steps);
		zoom_factor = Math.min(max_zoom / transform.getScaleX(), zoom_factor);
		zoom_factor = Math.max(min_zoom/  transform.getScaleX(), zoom_factor);
		transform.scale(zoom_factor,zoom_factor);
		this.repaint();
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
	
	public void setShowDay( boolean showDay ) {
		this.showDay = showDay;
	}
	
	public boolean getShowDay() {
		return showDay;
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
	
	/**
	 * Tell if the specified point (given by longitude, latitude coordinates) is
	 * visible or not.(Does not consider the scale factor that can make a visible
	 * point render out of the screen.
	 * 
	 * @param longitude
	 * @param latitude
	 * @return if the point is visible
	 */
	public boolean isVisible(double longitude, double latitude) {
		double mapRadiusR, distanceFromCentre;
		
		mapRadiusR= MapMath.DTR * ((AzimuthalProjection)projection).getMapRadius();
		distanceFromCentre = MapMath.greatCircleDistance(
				MapMath.DTR *longitude,  MapMath.DTR * latitude,
				projection.getProjectionLongitude(), projection.getProjectionLatitude() );
		boolean is_visible = distanceFromCentre <= mapRadiusR;
		
		return is_visible;
	}
	
	public boolean compareProjection (Projection p1, Projection p2) {
		return (p1.getPROJ4Description().equals(p2.getPROJ4Description()));
	}
	
	public void checkChanges () {
		if (!transform.equals(last_transform) || !(compareProjection(projection,last_projection))) {
			last_transform = (AffineTransform) transform.clone(); 
			last_projection = (Projection) projection.clone();
			last_time_update  = System.currentTimeMillis();
			buffered_image = null;
			quality_image_buffered = false;
		}
	}
	
	public boolean readyToQualityPaint() {
		long time_passed = System.currentTimeMillis() - last_time_update;
		return (time_passed > quality_paint_delay);
	}
	
	protected void create_g2 (Graphics g) {
		g2 = (Graphics2D)g;
		// Put the origin at bottom left
		g2.translate( 0, getHeight() );
		g2.scale( 1, -1 );

		// Put the globe in the middle
		g2.translate( getWidth()/2, getHeight()/2 );
		
		Point2D.Float p = new Point2D.Float( 1, 0 );
		transform.deltaTransform( p, p );
		float rscale = 1.0f/(float)Math.sqrt( p.x*p.x + p.y*p.y );
		g2.setStroke( new BasicStroke( rscale*0.5f ) );
		g2.transform( transform );
		
	}
	
	protected void setFastGraphics (Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
		g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
	}
	
	protected void setQualityGraphics (Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON  );
		g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}
	
	private void paintDay (int numPoints) {
		
		Calendar cal = Calendar.getInstance();
		cal.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		int current_day = cal.get(Calendar.DAY_OF_YEAR);
		double current_time = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE)/60;

		double fractional_year = 2 * Math.PI * (current_day + current_time/24) /365.25;
		double sun_declination =
			0.396372-22.91327*Math.cos(fractional_year)+ 4.02543*Math.sin(fractional_year)
			-0.387205*Math.cos(2*fractional_year)+0.051967*Math.sin(2*fractional_year)
			-0.154527*Math.cos(3*fractional_year) + 0.084798*Math.sin(3*fractional_year);
		
		double sun_longitude =  - (360/24)*(current_time -12); 
		
		GeneralPath gc = new GeneralPath();
		ProjectionPainter.smallCircle((float) sun_longitude,(float)  sun_declination, 87, numPoints, gc, true );
		gc.closePath();
		ProjectionPainter pp = ProjectionPainter.getProjectionPainter( projection );
		pp.drawPath( g2, gc, null, day_color );
	}
	
	private void paint_fast( Graphics g ) {
		create_g2(g);
		setFastGraphics(g2);
		
		MapGraphics mg = MapGraphics.getGraphics( g2, new Rectangle( getSize() ) );
		seaLayer.setVisible( showSea );
		tissotLayer.setVisible( false );
		worldLayer.setVisible( showWorld );
		graticuleLayer.setVisible( false );
		map.paint( mg );
		
		if ( showDay ) paintDay(10);
	}
	
	private void paint_quality( Graphics g ) {
		create_g2(g);
		setQualityGraphics (g2);
		
		MapGraphics mg = MapGraphics.getGraphics( g2, new Rectangle( getSize() ) );
		seaLayer.setVisible( showSea );
		tissotLayer.setVisible( showTissot );
		worldLayer.setVisible( showWorld );
		graticuleLayer.setVisible( showGraticule );
		map.paint( mg );
		
		if ( showDay ) paintDay(100);
	}
	
	public void paint( Graphics g ) {
		
		checkChanges();
		
		if (!quality_image_buffered) {
			if (buffered_image == null) {
				//Initialize the bufferdImage
				Dimension size = this.getSize();
//				GraphicsConfiguration gc = GraphicsEnvironment
//					.getLocalGraphicsEnvironment()
//				 	.getDefaultScreenDevice()
//				 	.getDefaultConfiguration();
//				buffered_image = gc.createCompatibleImage(size.width, size.height,Transparency.TRANSLUCENT);
				
				buffered_image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
				//Paint the map fast and store it
				Graphics2D ng = buffered_image.createGraphics();
				paint_fast(ng);
				ng.dispose();
			}
			else if (readyToQualityPaint()) {
				//Initialize the bufferdImage
				Dimension size = this.getSize();
				buffered_image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
				//Paint the map fast and store it
				Graphics2D ng = buffered_image.createGraphics();
				paint_quality(ng);
				ng.dispose();
				quality_image_buffered = true;
			}
		}
		g.drawImage(buffered_image, 0, 0, this);
		create_g2(g);
	}
	
	public Dimension getPreferredSize() {
		return new Dimension( 1000, 700 );
	}

}

