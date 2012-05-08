import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jhlabs.map.proj.Projection;
import com.jhlabs.map.ProjectionPainter;
import com.jhlabs.map.layer.MapGraphics;

/**
 * The globe component modified to show pings
 * <p>
 * This JComponent draw a globe and some visual effect for stored pings (up to
 * stored_pings_size =  { @value stored_ping_size} pings can be stored and 
 * displayed.
 */
public class PingGlobe extends Globe {
	
	private static final long serialVersionUID = 1L;
	
	private GeoipInfo origin;
	private PingGUI[] stored_pings;
	private static final int stored_pings_size = 75;
	
	//The last index of the array used to store a ping, as null case are handle
	//its value as no impact as long as it's in the bounds of the array
	private int last_ping = 0 ;
	
	private static final Color origin_color = new Color(32f / 255f, 207f / 255f, 14f / 255f,	0.9f);
	
	private static final float[] waiting_color = { 69, 178, 110};	
	private static final float[] no_result_color = {131, 13, 44};	
	private BasicStroke link_stroke = new BasicStroke(3.5f);
	
	private static final float [][] color_scale = {
		{0,255,0,			0.025f },
		{73,255,67,			0.050f },
		{106,255,97,		0.075f },
		{134,243,97,		0.100f },
		{246,242,35,		0.150f },
		{246,215,35,		0.200f },
		{222,161,62,		0.250f },
		{192,111,60,		0.300f },
		{168,21,42,			0.400f },
		{137,18,28,			0.500f },
		{92,32,40,			0.600f },
		{78,57,58,			0.700f },
		{76,76,76,			0.850f },
		{31,29,29,			1.000f },
			
	};
	
	
	public PingGlobe() {
		super();
		
		//The globe currently shows only the landmasses
		super.setShowGraticule(false);
		super.setShowTissot(false);
		super.setShowSea(false);
		// The night could be added using
		super.setShowNight(true);
		// although this method currently add a fixed black circle which wouldn't
		// show much. 
		
		stored_pings = new PingGUI[stored_pings_size];
	}
	
	class PingGUI {
		
		private GeoipInfo target;
		private double value = -1;
		float[] color = waiting_color;
		
		public PingGUI(GeoipInfo target) {
			this.target = target;
		}
		
		private void UpdateColor() {
			if (value < 0) {color = no_result_color;}
			else {
				int i = 0;
				while (color_scale[i][3] < value && i < color_scale.length)
					{i++;}
				color = color_scale[i];
			}
		}
		
		public void SetValue(double new_ping_value) {
			this.value = new_ping_value;
			UpdateColor();
			PingGlobe.this.repaint();
		}
		
		/**
		 * Draw a circle at the end point and an arc joining it with the origin.
		 */
		private void paint(Graphics2D g2, float color_attenuation) {
			if (target == null) return;
			
			//Set the parameter for this drawing
			Color peer_color = new Color(
					color[0]/255f,
					color[1]/255f,
					color[2]/255f,
					color_attenuation);	
			
			//Draw the circle
			GeneralPath gc = new GeneralPath();
			ProjectionPainter.smallCircle(
					(float) target.longitude,(float) target.latitude,
					1.5f, 20, gc, true);
			gc.closePath();
			ProjectionPainter  pp = ProjectionPainter.getProjectionPainter(projection);
			pp.drawPath(g2, gc, null, peer_color);
			
			//Draw the arc
			if (origin== null || value < 0) return;

			g2.setStroke(link_stroke);
			 gc = new GeneralPath();
			ProjectionPainter.basicArc(
					(float) origin.longitude,(float) origin.latitude,
					(float) target.longitude,(float) target.latitude,
					gc);
			pp = ProjectionPainter.getProjectionPainter(projection);
			pp.drawPath(g2, gc, peer_color, null);
			
			
		}
		
	}
	
	/**
	 * Select the origin on the globe : this will add a circle around it and 
	 * tell where to draw the arcs from. Additionally it centers the view on it.
	 * 
	 * @see #paintOrigin(Graphics2D, GeoipInfo)
	 * @see #paintLink(Graphics2D, GeoipInfo, GeoipInfo)
	 * 
	 * @param origin the client geoip to set origin on
	 */
	public void setOrigin(GeoipInfo origin) {
		this.origin = origin;
		centerView(origin);
	}
	
	/**
	 * Add a ping to be drawn on the globe. For the viewer convenience only 
	 * stored_ping_size = { @value stored_ping_size} pings can be stored and 
	 * displayed.
	 * 
	 * @param pinginfo the information on the new ping to add
	 */
	public PingGUI addPing(GeoipInfo pinginfo) {
		PingGUI newping = new PingGUI (pinginfo);
		last_ping = (last_ping + 1 ) % stored_pings_size;
		stored_pings[last_ping] = newping;
		this.repaint();
		return newping;
	}


	public void centerView(GeoipInfo new_center) {
		projection.setProjectionLatitude(Math.PI / 180 * new_center.latitude);

		projection.setProjectionLongitude(Math.PI / 180 * new_center.longitude);
		projection.initialize();
		this.repaint();
	}

	private void paintOrigin(Graphics2D g2, GeoipInfo origin) {
		if (origin == null ) return;
		GeneralPath gc = new GeneralPath();
		ProjectionPainter.smallCircle(
				(float)origin.longitude, (float) origin.latitude,
				2.5f,20, gc, true);
		gc.closePath();
		ProjectionPainter pp = ProjectionPainter.getProjectionPainter(projection);
		pp.drawPath(g2, gc, null, origin_color);
	}
	
	/**
	 * The methods that draw the component.
	 * <p>
	 * @see Globe
	 * @see #paintOrigin(Graphics2D, GeoipInfo)
	 * @see #paintLink(Graphics2D, GeoipInfo, GeoipInfo)
	 */
	public void paint(Graphics g) {
		super.paint(g);
		
		paintOrigin(g2, origin);
		
		int actual_index = last_ping;
		for (int i = 0;i < stored_pings_size ;i++) {
			PingGUI current_ping = stored_pings[actual_index];
			if (current_ping != null) {
				float color_attenuation =
					((float) (stored_pings_size - i))
					/
					((float) stored_pings_size);
				current_ping.paint(g2,color_attenuation);
			}
			actual_index--;
			if (actual_index == -1) actual_index = stored_pings_size -1;
		}
	
	}
}