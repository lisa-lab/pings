import java.util.Observable;
import java.util.Random;


/**
 * This class defines a rather accurate simulation of PingsClient
 * 
 * @author RaphaelBonaque
 */
public class PingsClientSimulation extends Observable implements Runnable{
	//Simulation parameters 
	
	//let's say the signal is 3 times slower than light speed
	double approximate_speed = 100000000;
	//Some random time due to any kind of factor (routers ...)
	double average_random_time = 0.025;
	//Some additional residual time (added on every network transmission)
	double residual_time = 1.;
	
	//the fraction of the ping that randomly time out
	double timed_out_fraction = 0.05;
	double timed_out_delay = 5;
	
	//the fraction of ping that randomly refuse connection
	double connection_refused_fraction = 0.5;
	
	// Normal information for PingsClient
	private PingGlobe ping_globe ;
	private GeoipInfo client_geoip;
	
	
	//Additional information for examples
	
	GeoipInfo montreal = new GeoipInfo("Montreal","Canada",-73.55f,45.5f);
	GeoipInfo newyork = new GeoipInfo("New-York","United States",-70.93f, 40.65f);
	GeoipInfo paris = new GeoipInfo("Paris","France",2.35f, 48.85f);
	GeoipInfo tokyo = new GeoipInfo("Tokyo","Japan",139.68f, 35.68f);
	GeoipInfo zerozero = new GeoipInfo("Zero","Zero",0f, 0f);
	
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
		long real_time = (long) ((residual_time+ time) * 1000);
		try {
			Thread.sleep((long) real_time );
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
		double x = prg.nextDouble();
		//Times out :
		if (x <  timed_out_fraction){
			wait_for_network(timed_out_delay);
			return -2;
		}
		else {
			double ping_estimation =
				2 * (approximate_distance(client_geoip, target)/ approximate_speed)
				+ (2 * average_random_time * prg.nextDouble());
			wait_for_network(ping_estimation);

			//Connection refused :
			if (x > 1 - connection_refused_fraction) {
				return -1;
			}
			else{
				return ping_estimation;
			}
		}
	}
	
	/**
	 * Emulate the use of the next address in the list received from the server
	 * 
	 * @return a  random GeoipInfo 
	 */
	private GeoipInfo take_next_adress_from_list() {
		int index = prg.nextInt(worldCapital.length);
		return worldCapital[index];
	}
	
	private void talk_with_server() {
		ping(montreal);
	}
	
	/**
	 * Simulate the PingClient run thread
	 */
	public void run () {
		//Receive the client localization from the server
		this.client_geoip = paris;
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
	
	public GeoipInfo[] worldCapital = {
		new GeoipInfo("Kabul", "Afghanistan", 69.11f, 34.28f), //E
		new GeoipInfo("Tirane", "Albania", 19.49f, 41.18f), //E
		new GeoipInfo("Algiers", "Algeria", 03.08f, 36.42f), //E
		new GeoipInfo("American Samoa", "Pago Pago",-14.16f, -170.43f), //w
		new GeoipInfo("Andorra", "Andorra la Vella", 01.32f, 42.31f), //E
		new GeoipInfo("Luanda", "Angola",-08.50f, 13.15f), //E
		new GeoipInfo("Antigua and Barbuda", "West Indies", -61.48f, 17.20f), //w
		new GeoipInfo("Argentina", "Buenos Aires",-36.30f, -60.00f), //w
		new GeoipInfo("Yerevan", "Armenia", 44.31f, 40.10f), //E
		new GeoipInfo("Oranjestad", "Aruba", -70.02f, 12.32f), //w
		new GeoipInfo("Canberra", "Australia",-35.15f, 149.08f), //E
		new GeoipInfo("Vienna", "Austria", 16.22f, 48.12f), //E
		new GeoipInfo("Baku", "Azerbaijan", 49.56f, 40.29f), //E
		new GeoipInfo("Nassau", "Bahamas", -77.20f, 25.05f), //w
		new GeoipInfo("Manama", "Bahrain", 50.30f, 26.10f), //E
		new GeoipInfo("Dhaka", "Bangladesh", 90.26f, 23.43f), //E
		new GeoipInfo("Bridgetown", "Barbados", -59.30f, 13.05f), //w
		new GeoipInfo("Minsk", "Belarus", 27.30f, 53.52f), //E
		new GeoipInfo("Brussels", "Belgium", 04.21f, 50.51f), //E
		new GeoipInfo("Belmopan", "Belize", -88.30f, 17.18f), //w
		new GeoipInfo("Benin", "Porto Novo (constitutional) / Cotonou (seat of government)", 02.42f, 06.23f), //E
		new GeoipInfo("Thimphu", "Bhutan", 89.45f, 27.31f), //E
		new GeoipInfo("Bolivia", "La Paz (administrative) / Sucre (legislative)",-16.20f, -68.10f), //w
		new GeoipInfo("Bosnia and Herzegovina", "Sarajevo", 18.26f, 43.52f), //E
		new GeoipInfo("Gaborone", "Botswana",-24.45f, 25.57f), //E
		new GeoipInfo("Brasilia", "Brazil",-15.47f, -47.55f), //w
		new GeoipInfo("British Virgin Islands", "Road Town", -64.37f, 18.27f), //w
		new GeoipInfo("Brunei Darussalam", "Bandar Seri Begawan", 115.00f, 04.52f), //E
		new GeoipInfo("Sofia", "Bulgaria", 23.20f, 42.45f), //E
		new GeoipInfo("Burkina Faso", "Ouagadougou", -01.30f, 12.15f), //w
		new GeoipInfo("Bujumbura", "Burundi",-03.16f, 29.18f), //E
		new GeoipInfo("Cambodia", "Phnom Penh", 104.55f, 11.33f), //E
		new GeoipInfo("Yaounde", "Cameroon", 11.35f, 03.50f), //E
		new GeoipInfo("Ottawa", "Canada", -75.42f, 45.27f), //w
		new GeoipInfo("Cape Verde", "Praia", -23.34f, 15.02f), //w
		new GeoipInfo("Cayman Islands", "George Town", -81.24f, 19.20f), //w
		new GeoipInfo("Central African Republic", "Bangui", 18.35f, 04.23f), //E
		new GeoipInfo("Chad", "N'Djamena", 14.59f, 12.10f), //E
		new GeoipInfo("Santiago", "Chile",-33.24f, -70.40f), //w
		new GeoipInfo("Beijing", "China", 116.20f, 39.55f), //E
		new GeoipInfo("Bogota", "Colombia", -74.00f, 04.34f), //w
		new GeoipInfo("Moroni", "Comros",-11.40f, 43.16f), //E
		new GeoipInfo("Brazzaville", "Congo",-04.09f, 15.12f), //E
		new GeoipInfo("Costa Rica", "San Jose", -84.02f, 09.55f), //w
		new GeoipInfo("Cote d'Ivoire", "Yamoussoukro", -05.17f, 06.49f), //w
		new GeoipInfo("Zagreb", "Croatia", 15.58f, 45.50f), //E
		new GeoipInfo("Havana", "Cuba", -82.22f, 23.08f), //w
		new GeoipInfo("Nicosia", "Cyprus", 33.25f, 35.10f), //E
		new GeoipInfo("Czech Republic", "Prague", 14.22f, 50.05f), //E
		new GeoipInfo("Democratic Republic of the Congo", "Kinshasa",-04.20f, 15.15f), //E
		new GeoipInfo("Copenhagen", "Denmark", 12.34f, 55.41f), //E
		new GeoipInfo("Djibouti", "Djibouti", 42.20f, 11.08f), //E
		new GeoipInfo("Roseau", "Dominica", -61.24f, 15.20f), //w
		new GeoipInfo("Dominica Republic", "Santo Domingo", -69.59f, 18.30f), //w
		new GeoipInfo("East Timor", "Dili",-08.29f, 125.34f), //E
		new GeoipInfo("Quito", "Ecuador",-00.15f, -78.35f), //w
		new GeoipInfo("Cairo", "Egypt", 31.14f, 30.01f), //E
		new GeoipInfo("El Salvador", "San Salvador", -89.10f, 13.40f), //w
		new GeoipInfo("Equatorial Guinea", "Malabo", 08.50f, 03.45f), //E
		new GeoipInfo("Asmara", "Eritrea", 38.55f, 15.19f), //E
		new GeoipInfo("Tallinn", "Estonia", 24.48f, 59.22f), //E
		new GeoipInfo("Ethiopia", "Addis Ababa", 38.42f, 09.02f), //E
		new GeoipInfo("Falkland Islands (Malvinas)", "Stanley",-51.40f, -59.51f), //w
		new GeoipInfo("Faroe Islands", "Torshavn", -06.56f, 62.05f), //w
		new GeoipInfo("Suva", "Fiji",-18.06f, 178.30f), //E
		new GeoipInfo("Helsinki", "Finland", 25.03f, 60.15f), //E
		new GeoipInfo("Paris", "France", 02.20f, 48.50f), //E
		new GeoipInfo("French Guiana", "Cayenne", -52.18f, 05.05f), //w
		new GeoipInfo("French Polynesia", "Papeete",-17.32f, -149.34f), //w
		new GeoipInfo("Libreville", "Gabon", 09.26f, 00.25f), //E
		new GeoipInfo("Banjul", "Gambia", -16.40f, 13.28f), //w
		new GeoipInfo("Georgia", "T'bilisi", 44.50f, 41.43f), //E
		new GeoipInfo("Berlin", "Germany", 13.25f, 52.30f), //E
		new GeoipInfo("Accra", "Ghana", -00.06f, 05.35f), //w
		new GeoipInfo("Athens", "Greece", 23.46f, 37.58f), //E
		new GeoipInfo("Nuuk", "Greenland", -51.35f, 64.10f), //w
		new GeoipInfo("Guadeloupe", "Basse-Terre", -61.44f, 16.00f), //w
		new GeoipInfo("Guatemala", "Guatemala", -90.22f, 14.40f), //w
		new GeoipInfo("Guernsey", "St. Peter Port", -02.33f, 49.26f), //w
		new GeoipInfo("Conakry", "Guinea", -13.49f, 09.29f), //w
		new GeoipInfo("Guinea-Bissau", "Bissau", -15.45f, 11.45f), //w
		new GeoipInfo("Georgetown", "Guyana", -58.12f, 06.50f), //w
		new GeoipInfo("Haiti", "Port-au-Prince", -72.20f, 18.40f), //w
		new GeoipInfo("Heard Island and McDonald Islands", " ",-53.00f, 74.00f), //E
		new GeoipInfo("Tegucigalpa", "Honduras", -87.14f, 14.05f), //w
		new GeoipInfo("Budapest", "Hungary", 19.05f, 47.29f), //E
		new GeoipInfo("Reykjavik", "Iceland", -21.57f, 64.10f), //w
		new GeoipInfo("India", "New Delhi", 77.13f, 28.37f), //E
		new GeoipInfo("Jakarta", "Indonesia",-06.09f, 106.49f), //E
		new GeoipInfo("Iran (Islamic Republic of)", "Tehran", 51.30f, 35.44f), //E
		new GeoipInfo("Baghdad", "Iraq", 44.30f, 33.20f), //E
		new GeoipInfo("Dublin", "Ireland", -06.15f, 53.21f), //w
		new GeoipInfo("Jerusalem", "Israel", -35.10f, 31.71f), //w
		new GeoipInfo("Rome", "Italy", 12.29f, 41.54f), //E
		new GeoipInfo("Kingston", "Jamaica", -76.50f, 18.00f), //w
		new GeoipInfo("Amman", "Jordan", 35.52f, 31.57f), //E
		new GeoipInfo("Astana", "Kazakhstan", 71.30f, 51.10f), //E
		new GeoipInfo("Nairobi", "Kenya",-01.17f, 36.48f), //E
		new GeoipInfo("Tarawa", "Kiribati", 173.00f, 01.30f), //E
		new GeoipInfo("Kuwait", "Kuwait", 48.00f, 29.30f), //E
		new GeoipInfo("Bishkek", "Kyrgyzstan", 74.46f, 42.54f), //E
		new GeoipInfo("Lao People's Democratic Republic", "Vientiane", 102.36f, 17.58f), //E
		new GeoipInfo("Riga", "Latvia", 24.08f, 56.53f), //E
		new GeoipInfo("Beirut", "Lebanon", 35.31f, 33.53f), //E
		new GeoipInfo("Maseru", "Lesotho",-29.18f, 27.30f), //E
		new GeoipInfo("Monrovia", "Liberia", -10.47f, 06.18f), //w
		new GeoipInfo("Libyan Arab Jamahiriya", "Tripoli", 13.07f, 32.49f), //E
		new GeoipInfo("Vaduz", "Liechtenstein", 09.31f, 47.08f), //E
		new GeoipInfo("Vilnius", "Lithuania", 25.19f, 54.38f), //E
		new GeoipInfo("Luxembourg", "Luxembourg", 06.09f, 49.37f), //E
		new GeoipInfo("Macao, China", "Macau", 113.33f, 22.12f), //E
		new GeoipInfo("Antananarivo", "Madagascar",-18.55f, 47.31f), //E
		new GeoipInfo("Macedonia (Former Yugoslav Republic)", "Skopje", 21.26f, 42.01f), //E
		new GeoipInfo("Lilongwe", "Malawi",-14.00f, 33.48f), //E
		new GeoipInfo("Malaysia", "Kuala Lumpur", 101.41f, 03.09f), //E
		new GeoipInfo("Male", "Maldives", 73.28f, 04.00f), //E
		new GeoipInfo("Bamako", "Mali", -07.55f, 12.34f), //w
		new GeoipInfo("Valletta", "Malta", 14.31f, 35.54f), //E
		new GeoipInfo("Martinique", "Fort-de-France", -61.02f, 14.36f), //w
		new GeoipInfo("Nouakchott", "Mauritania",-20.10f, 57.30f), //E
		new GeoipInfo("Mamoudzou", "Mayotte",-12.48f, 45.14f), //E
		new GeoipInfo("Mexico", "Mexico", -99.10f, 19.20f), //w
		new GeoipInfo("Micronesia (Federated States of)", "Palikir", 158.09f, 06.55f), //E
		new GeoipInfo("Moldova, Republic of", "Chisinau", 28.50f, 47.02f), //E
		new GeoipInfo("Maputo", "Mozambique",-25.58f, 32.32f), //E
		new GeoipInfo("Yangon", "Myanmar", 96.20f, 16.45f), //E
		new GeoipInfo("Windhoek", "Namibia",-22.35f, 17.04f), //E
		new GeoipInfo("Kathmandu", "Nepal", 85.20f, 27.45f), //E
		new GeoipInfo("Netherlands", "Amsterdam / The Hague (seat of Government)", 04.54f, 52.23f), //E
		new GeoipInfo("Netherlands Antilles", "Willemstad", -69.00f, 12.05f), //w
		new GeoipInfo("New Caledonia", "Noumea",-22.17f, 166.30f), //E
		new GeoipInfo("New Zealand", "Wellington",-41.19f, 174.46f), //E
		new GeoipInfo("Managua", "Nicaragua", -86.20f, 12.06f), //w
		new GeoipInfo("Niamey", "Niger", 02.06f, 13.27f), //E
		new GeoipInfo("Abuja", "Nigeria", 07.32f, 09.05f), //E
		new GeoipInfo("Norfolk Island", "Kingston",-45.20f, 168.43f), //E
		new GeoipInfo("North Korea", "Pyongyang", 125.30f, 39.09f), //E
		new GeoipInfo("Northern Mariana Islands", "Saipan", 145.45f, 15.12f), //E
		new GeoipInfo("Oslo", "Norway", 10.45f, 59.55f), //E
		new GeoipInfo("Masqat", "Oman", 58.36f, 23.37f), //E
		new GeoipInfo("Islamabad", "Pakistan", 73.10f, 33.40f), //E
		new GeoipInfo("Koror", "Palau", 134.28f, 07.20f), //E
		new GeoipInfo("Panama", "Panama", -79.25f, 09.00f), //w
		new GeoipInfo("Papua New Guinea", "Port Moresby",-09.24f, 147.08f), //E
		new GeoipInfo("Asuncion", "Paraguay",-25.10f, -57.30f), //w
		new GeoipInfo("Lima", "Peru",-12.00f, -77.00f), //w
		new GeoipInfo("Manila", "Philippines", 121.03f, 14.40f), //E
		new GeoipInfo("Warsaw", "Poland", 21.00f, 52.13f), //E
		new GeoipInfo("Lisbon", "Portugal", -09.10f, 38.42f), //w
		new GeoipInfo("Puerto Rico", "San Juan", -66.07f, 18.28f), //w
		new GeoipInfo("Doha", "Qatar", 51.35f, 25.15f), //E
		new GeoipInfo("Republic of Korea", "Seoul", 126.58f, 37.31f), //E
		new GeoipInfo("Bucuresti", "Romania", 26.10f, 44.27f), //E
		new GeoipInfo("Russian Federation", "Moskva", 37.35f, 55.45f), //E
		new GeoipInfo("Kigali", "Rawanda",-01.59f, 30.04f), //E
		new GeoipInfo("Saint Kitts and Nevis", "Basseterre", -62.43f, 17.17f), //w
		new GeoipInfo("Saint Lucia", "Castries", -60.58f, 14.02f), //w
		new GeoipInfo("Saint Pierre and Miquelon", "Saint-Pierre", -56.12f, 46.46f), //w
		new GeoipInfo("Saint Vincent and the Greenadines", "Kingstown", -61.10f, 13.10f), //w
		new GeoipInfo("Apia", "Samoa",-13.50f, -171.50f), //w
		new GeoipInfo("San Marino", "San Marino", 12.30f, 43.55f), //E
		new GeoipInfo("Sao Tome and Principe", "Sao Tome", 06.39f, 00.10f), //E
		new GeoipInfo("Saudi Arabia", "Riyadh", 46.42f, 24.41f), //E
		new GeoipInfo("Dakar", "Senegal", -17.29f, 14.34f), //w
		new GeoipInfo("Sierra Leone", "Freetown", -13.17f, 08.30f), //w
		new GeoipInfo("Bratislava", "Slovakia", 17.07f, 48.10f), //E
		new GeoipInfo("Ljubljana", "Slovenia", 14.33f, 46.04f), //E
		new GeoipInfo("Solomon Islands", "Honiara",-09.27f, 159.57f), //E
		new GeoipInfo("Mogadishu", "Somalia", 45.25f, 02.02f), //E
		new GeoipInfo("South Africa", "Pretoria (administrative) / Cape Town (legislative) / Bloemfontein (judicial)",-25.44f, 28.12f), //E
		new GeoipInfo("Madrid", "Spain", -03.45f, 40.25f), //w
		new GeoipInfo("Khartoum", "Sudan", 32.35f, 15.31f), //E
		new GeoipInfo("Paramaribo", "Suriname", -55.10f, 05.50f), //w
		new GeoipInfo("Swaziland", "Mbabane (administrative)",-26.18f, 31.06f), //E
		new GeoipInfo("Stockholm", "Sweden", 18.03f, 59.20f), //E
		new GeoipInfo("Bern", "Switzerland", 07.28f, 46.57f), //E
		new GeoipInfo("Syrian Arab Republic", "Damascus", 36.18f, 33.30f), //E
		new GeoipInfo("Dushanbe", "Tajikistan", 68.48f, 38.33f), //E
		new GeoipInfo("Bangkok", "Thailand", 100.35f, 13.45f), //E
		new GeoipInfo("Lome", "Togo", 01.20f, 06.09f), //E
		new GeoipInfo("Tonga", "Nuku'alofa",-21.10f, -174.00f), //w
		new GeoipInfo("Tunis", "Tunisia", 10.11f, 36.50f), //E
		new GeoipInfo("Ankara", "Turkey", 32.54f, 39.57f), //E
		new GeoipInfo("Ashgabat", "Turkmenistan", 57.50f, 38.00f), //E
		new GeoipInfo("Funafuti", "Tuvalu",-08.31f, 179.13f), //E
		new GeoipInfo("Kampala", "Uganda", 32.30f, 00.20f), //E
		new GeoipInfo("Ukraine", "Kiev (Russia)", 30.28f, 50.30f), //E
		new GeoipInfo("United Arab Emirates", "Abu Dhabi", 54.22f, 24.28f), //E
		new GeoipInfo("United Kingdom of Great Britain and Northern Ireland", "London", -00.05f, 51.36f), //w
		new GeoipInfo("United Republic of Tanzania", "Dodoma",-06.08f, 35.45f), //E
		new GeoipInfo("United States of America", "Washington DC", -77.02f, 39.91f), //w
		new GeoipInfo("United States of Virgin Islands", "Charlotte Amalie", -64.56f, 18.21f), //w
		new GeoipInfo("Montevideo", "Uruguay",-34.50f, -56.11f), //w
		new GeoipInfo("Tashkent", "Uzbekistan", 69.10f, 41.20f), //E
		new GeoipInfo("Vanuatu", "Port-Vila",-17.45f, 168.18f), //E
		new GeoipInfo("Caracas", "Venezuela", -66.55f, 10.30f), //w
		new GeoipInfo("Viet Nam", "Hanoi", 105.55f, 21.05f), //E
		new GeoipInfo("Belgrade", "Yugoslavia", 20.37f, 44.50f), //E
		new GeoipInfo("Lusaka", "Zambia",-15.28f, 28.16f), //E
		new GeoipInfo("Harare", "Zimbabwe",-17.43f, 31.02f), //E
		};
	
}
