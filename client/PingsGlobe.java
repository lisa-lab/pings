import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

import com.jhlabs.map.MapMath;
import com.jhlabs.map.ProjectionPainter;

/**
 * The globe component modified to show pings
 * <p>
 * This JComponent draw a globe and some visual effect for stored pings (up to
 * stored_pings_size =  { @value stored_ping_size} pings can be stored and 
 * displayed.
 */
public class PingsGlobe extends Globe {
    
    private static final long serialVersionUID = 1L;
    
    private GeoipInfo origin;
    private PingGUI[] stored_pings;
    private static int stored_pings_size;
    private static final float attenuation_offset_for_cities = 2f / 20f;
    
    //The last index of the array used to store a ping, as null case are handle
    //its value as no impact as long as it's in the bounds of the array
    private int last_ping = 0 ;
    
    //The color of the client.
    private static final Color origin_color = new Color(44f / 255f, 63f / 255f, 201f / 255f, 0.9f); //blue
    private static final float[] waiting_color = {227, 90, 0}; //orange
    private static final float[] timed_out_color = {200, 0, 0};//red
    private static final float[] connection_refused_color = {200, 0, 0};//red
    private static final float[] unknown_error_color = {200, 0, 0};//red
    private float prefered_font_size = 10f; //13.5f;
    private float circle_radius_scale=1.5f;
    private BasicStroke link_stroke = new BasicStroke(2.5f);
    
    private Graphics2D text_render;
    
    private static final float [] worked_color = {0, 255, 0}; //green
    
    
    public PingsGlobe(int stored_pings_size) {
        super();
        super.setShowGraticule(false);
        super.setShowTissot(false);
        super.setShowSea(true);
        super.setShowDay(false);
	this.stored_pings_size = stored_pings_size;
        
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
            if (value == -1) {color = waiting_color;}
            else if (value == -2) {color = timed_out_color;}
            else if (value == -3) {color = unknown_error_color;}
            else if (value == -4) {color = connection_refused_color;}
            else {color = worked_color;}
        }
        
        public GeoipInfo getGeoip() {
            return target;
        }
        
        public void setValue(double new_ping_value) {
            this.value = new_ping_value;
            UpdateColor();
            PingsGlobe.this.repaint();
        }
        
        public void connectionRefused () {
            this.value = -4;
            UpdateColor();
            PingsGlobe.this.repaint();
        }
        
        public void unknownError() {
            this.value = -3;
            UpdateColor();
            PingsGlobe.this.repaint();
        }
        
        public void timedOut() {
            this.value = -2;
            UpdateColor();
            PingsGlobe.this.repaint();
        }
        
        public void noResultsYet () {
            this.value = -1;
            UpdateColor();
            PingsGlobe.this.repaint();
        }
        
        private void add_target_circle(GeneralPath gc, float circle_radius) {
            ProjectionPainter.smallCircle(
                    (float) target.longitude,(float) target.latitude,
                    circle_radius, 15, gc, true);
            gc.closePath();
        }
        
        private void add_arc(GeneralPath gc) {
            //FIXME: changed just to see the arcs even with 'failed' pings
            if (origin== null /* || value < 0*/) return;
    
            g2.setStroke(link_stroke);
            ProjectionPainter.basicArc(
                    (float) origin.longitude,(float) origin.latitude,
                    (float) target.longitude,(float) target.latitude,
                    gc);
        }
        
        private void paint_description_text(Color peer_color, float circle_radius) {
            Point2D.Double target_geo = new Point2D.Double(target.longitude, target.latitude);
            
            projection.transform(target_geo,target_geo);
            
            String description;
            if (target.city != null && !target.city.equals("")) {
                description = target.city + ", " + target.country;
            }
            else if (target.country != null) {
                description = target.country;
            }
            else {
                description = "Unknow";
            }
            
            if (value > 0) {
                if (value < 10) {
                    long ms_value = Math.round(1000 * value);
                    description+= " : " + ms_value + " ms";
                }
                else {
                    long s_value = Math.round(value);
                    description+= " : " + s_value + " s";
                }
            }
            else if (value > 0 ) {
                description+= " : " + ((int) (value)) + " s";
            }
            else if (value == -1) {
                description+= " : Pinging";
            }
            else if (value < 0) {
                description+= " : Error";
            }
            
            text_render.setColor(peer_color);
            text_render.drawString( description , (int) target_geo.x + circle_radius,(int)- target_geo.y - (3 * circle_radius) );
        }
        
        
        
        /**
         * Draw a circle at the end point and an arc joining it with the origin.
         */
        private void paint(Graphics2D g2, float circle_radius) {
            if (target == null) return;
            
            //Set the parameter for this drawing
	    float color_attenuation = 1;
	    if (value > 0)
		color_attenuation -= (float)value/1000f;
            Color peer_color = new Color(
                    color[0]/255f,
                    color[1]/255f,
                    color[2]/255f,
                    color_attenuation);
            
            //Draw the circle around the target
            GeneralPath gc = new GeneralPath();
            ProjectionPainter pp = ProjectionPainter.getProjectionPainter(projection);
            
            add_target_circle(gc, circle_radius);
            pp.drawPath(g2, gc, null,peer_color);
            
            gc = new GeneralPath();
            add_arc(gc);
            pp.drawPath(g2, gc, peer_color,null);
            
            
            //Draw the description above the target
            if ((color_attenuation > attenuation_offset_for_cities) &&
                    (isVisible(target.longitude,target.latitude))) {
                paint_description_text(peer_color, circle_radius);
            }
            
        }

        public void updatePingGUIValue(String value) {
	    // Format of value : ICMP ip nb_try nb_worked total_timems[ first_timems]*[;...]
	    // We can't use the total time as on linux it isn't the sum of all the pings.
	    // So we will display the first time.
            //final String regex = "\\S+\\s\\S+\\s(\\d+)\\s(\\d+)\\s(\\d+)\\s(\\d+).+";
            try {

                String[] groups = value.split(";")[0].split(" |ms", 8);
                int nb_try = Integer.parseInt(groups[2]);
                int nb_worked = Integer.parseInt(groups[3]);
                //float totaltime = Float.parseFloat(groups[4]) / 1000f;

                if (nb_worked == 0) {
                    this.connectionRefused();
                }
                else if (nb_worked < nb_try -1)//If more then 1 ICMP ping failed
                {
                    this.unknownError();
                }
                else 
                {
		    float firsttime = Float.parseFloat(groups[6]) / 1000f;
                    this.setValue(firsttime);
                }
            }
            catch (Exception e) {
		//There is a parsing error. This probably mean their was an error during the ping.
                this.unknownError();
            }
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

    /**
     * Center the projection to have a good view on the given point.
     * <p>
     * This function doesn't actually center the view exactly on the point 
     * itself but on a point 0.7 rad W to it.
     * 
     * @param new_center the point to look at
     */
    public void centerView(GeoipInfo new_center) {
        projection.setProjectionLatitude(MapMath.DTR * new_center.latitude);
        projection.setProjectionLongitude(MapMath.DTR * new_center.longitude - 0.7);
        projection.initialize();
        this.repaint();
    }
    
    /**
     * Paint the origin (that is the client geoip) as a circle on the globe
     * using the Graphics g2.
     * @param g2 the Graphics2D to draw on
     * @param origin the GeoipInfo of the origin
     */
    private void paintOrigin(Graphics2D g2, GeoipInfo origin, float circle_radius) {
        if (origin == null ) return;
        GeneralPath gc = new GeneralPath();
        ProjectionPainter.smallCircle(
                (float)origin.longitude, (float) origin.latitude,
                circle_radius,20, gc, true);
        gc.closePath();
        ProjectionPainter pp = ProjectionPainter.getProjectionPainter(projection);
        pp.drawPath(g2, gc, null, origin_color);
    }
    
    /**
     * The methods that draw the globe.
     * <p>
     * @see Globe
     * @see #paintOrigin(Graphics2D, GeoipInfo)
     * @see #paintLink(Graphics2D, GeoipInfo, GeoipInfo)
     */
    public void paint(Graphics g) {
        
        //Paint the globe
        super.paint(g);
        
        //set_fast_graphics(g2);
        
        //Paint the targets
        
        //Set up the text rendering
        //Create a new graphics for the text to be able to select a different 
        //transformation and a different font
        
        //The new transformation is used to draw the text upward
        text_render = (Graphics2D) g2.create();
        AffineTransform uptransform = new AffineTransform();
        uptransform.translate(getWidth()/2,getHeight()/2);
        uptransform.concatenate(transform);
        text_render.setTransform(uptransform);
        
        //Calculate a new font according to the current zoom
        int screenRes = Toolkit.getDefaultToolkit().getScreenResolution();
        int fontSize = (int)Math.round(prefered_font_size  * screenRes / 72.0 / uptransform.getScaleX());
        Font font = new Font("Arial", Font.PLAIN, fontSize);
        text_render.setFont(font);
        
        //Calculate a new circle radius according to the current zoom
        float circle_radius = (float) (circle_radius_scale / uptransform.getScaleX());
        link_stroke = new BasicStroke(1.25f * circle_radius );
        
        //We paint the currently stored pings
        int actual_index = last_ping;
        for (int i = 0;i < stored_pings_size ;i++) {
            PingGUI current_ping = stored_pings[actual_index];
            if (current_ping != null) {
                current_ping.paint(g2,circle_radius);
            }
            actual_index--;
            if (actual_index == -1) actual_index = stored_pings_size -1;
        }
        
        //Paint the origin (the client position)
        paintOrigin(g2, origin, 1.75f * circle_radius);
    
    }
}
