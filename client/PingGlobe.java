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

//TODO : add comments

/**
 * The globe component modified to show the pings
 */
public class PingGlobe extends Globe {

	
	private static final long serialVersionUID = 1L;

	private GeoipInfo origin;
	private List<GeoipInfo> ping_list;

	private Color origin_color = new Color(32f / 255f, 207f / 255f, 14f / 255f,	0.9f);
	private Color end_point_color = new Color(131f / 255f, 13f / 255f, 44f / 255f, 0.8f);
	private BasicStroke link_stroke = new BasicStroke(4);

	public PingGlobe() {

		super();
		super.setShowGraticule(false);
		super.setShowTissot(false);
		super.setShowSea(false);
		// super.setShowNight(true);
		ping_list = new ArrayList();
	}

	public void setOrigin(GeoipInfo origin) {
		this.origin = origin;
		centerView(origin);
	}

	public void addPing(GeoipInfo destination) {
		ping_list.add(destination);
	}

	public void centerView(GeoipInfo new_center) {
		projection.setProjectionLatitude(Math.PI / 180 * new_center.latitude);

		projection.setProjectionLongitude(Math.PI / 180 * new_center.longitude);
		projection.initialize();
		this.repaint();
	}

	private void paintOrigin(Graphics2D g2, GeoipInfo origin) {
		GeneralPath gc = new GeneralPath();
		ProjectionPainter.smallCircle(
				(float)origin.longitude, (float) origin.latitude,
				2.5f,20, gc, true);
		gc.closePath();
		ProjectionPainter pp = ProjectionPainter.getProjectionPainter(projection);
		pp.drawPath(g2, gc, null, origin_color);
	}

	/**
	 * Draw a circle at the end point and an arc joining it with the origin.
	 */
	private void paintLink(Graphics2D g2, GeoipInfo origin, GeoipInfo end_point) {
		
		if (end_point == null) return;
		
		g2.setStroke(link_stroke);

		/*
		GradientPaint gp = new GradientPaint(0, 0, origin_color, 4, 4,
		end_point_color, true);
		 */
		
		//Draw the arc
		GeneralPath gc = new GeneralPath();
		ProjectionPainter.basicArc(
				(float) origin.longitude,(float) origin.latitude,
				(float) end_point.longitude,(float) end_point.latitude,
				gc);
		ProjectionPainter pp = ProjectionPainter.getProjectionPainter(projection);
		pp.drawPath(g2, gc, end_point_color, null);

		//Draw the circle
		gc = new GeneralPath();
		ProjectionPainter.smallCircle(
				(float) end_point.longitude,(float) end_point.latitude,
				1.5f, 20, gc, true);
		gc.closePath();
		pp = ProjectionPainter.getProjectionPainter(projection);
		pp.drawPath(g2, gc, null, end_point_color);
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

		Iterator<GeoipInfo> iterator = ping_list.iterator();
		while (iterator.hasNext()) {
			paintLink(g2, origin, iterator.next());
		}

	}
}
