import java.util.Random;


/**
 * This class defines a rather accurate simulation of PingsClient
 * 
 * @author RaphaelBonaque
 */
public class PingsClientSimulation  extends Thread{
	
	//Simulation parameters 
	
	//let's say the signal is 3 times slower than light speed
	double approximate_speed = 100000000;
	//Some random time due to any kind of factor (routers ...)
	double average_random_time = 0.05;
	//Some additional residual time
	double residual_time = 0.01 ;
	
	
	// Normal information for PingsClient
	private PingGlobe ping_globe ;
	private GeoipInfo client_geoip;
	
	
	//Additional information for examples
	
	GeoipInfo montreal = new GeoipInfo("Montreal","Canada",-73.55f,45.5f);
	GeoipInfo newyork = new GeoipInfo("New-York","United States",-70.93f, 40.65f);
	GeoipInfo paris = new GeoipInfo("Paris","France",2.35f, 48.85f);
	GeoipInfo tokyo = new GeoipInfo("Tokyo","Japan",139.68f, 35.68f);
	
	Random prg;
	
	public PingsClientSimulation(PingGlobe ping_globe) {
		this.ping_globe = ping_globe;
		prg = new Random();
	}

	/**
	 * A function simulating a wait from the network
	 * 
	 * @param time the time to wait in second
	 */
	public void wait_for_network(double time) {
		long wdf = (long) (time * 1000);
		try {
			Thread.sleep((long) wdf );
		} catch (InterruptedException e) {}
	}
	
	
	/**
	 * Return a great-circle approximation of the distance between to points
	 * 
	 * @return the approximate distance in meters
	 */
	private double approximate_distance(GeoipInfo x1, GeoipInfo x2) {
		double phi_1 = x1.latitude * Math.PI /180;
		double lambda_1 = x1.longitude * Math.PI /180;
		double phi_2 = x2.latitude * Math.PI /180;
		double lambda_2 = x2.longitude * Math.PI /180;
		
		double mb1 = Math.sin(phi_1) * Math.sin(phi_2);
		double mb2 = Math.cos(phi_1) * Math.cos(phi_2)*Math.cos(lambda_1 - lambda_2);
		
		double earth_radius = 6372800.;
		double approximate_dist = earth_radius * Math.acos(mb1 + mb2);
		return approximate_dist;
	}
	
	/**
	 * A function simulating a ping (we don't need the address of the 
	 * target only the position)
	 */
	private double ping(GeoipInfo target) {
		double ping_estimation = residual_time + 
			2 * (approximate_distance(client_geoip, target)/ approximate_speed)
			+ (2 * average_random_time * prg.nextDouble());
		wait_for_network(ping_estimation);
		return ping_estimation;
	}
	
	/**
	 * Emulate the use of the next address in the list received from the server
	 * 
	 * @return a  random GeoipInfo 
	 */
	private GeoipInfo take_next_adress_from_list() {
		double lon = 360 * (prg.nextDouble() -0.5);
		double lat = 180 * (prg.nextFloat() -0.5);
		return new GeoipInfo("", "", lon, lat);
	}
	
	private void talk_with_server() {
		ping(montreal);
	}
	
	/**
	 * Simulate the PingClient run thread
	 */
	public void run () {
		//Receive the client localization from the server
		this.client_geoip = montreal;
		//talk with server is before setting the geoip but don't mind this ...
		talk_with_server();
		ping_globe.setOrigin(client_geoip);
					
		while (true) {
			//Receive some addresses from the server
			talk_with_server();
			
			for (int i = 0 ; i < 100; i++) {
				//Take the next address in the list received from the server
				GeoipInfo remote_geoip = take_next_adress_from_list();
				
				//Add it to the GUI (and repaint)
				PingGlobe.PingGUI gui_effect = ping_globe.addPing(remote_geoip);
				
				//Do the actual ping and store the value
				double value = ping(remote_geoip);
				//some_storage[] = value;
				
				//Inform the GUI of the value(and repaint)
				gui_effect.SetValue(value);
				
			}
			
			//Send the results to the server
			talk_with_server();
			
		}
		
	}
}
