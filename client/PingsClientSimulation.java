import java.net.InetAddress;
import java.util.Random;



/**
 * This class defines a rather accurate simulation of PingsClient
 * 
 * @author RaphaelBonaque
 */
public class PingsClientSimulation extends PingsClient{
    //Simulation parameters 
    
    //let's say the signal is 3 times slower than light speed
    double approximate_speed = 100000000;
    //Some random time due to any kind of factor (routers ...)
    double average_random_time = 0.025;
    //Some additional residual time (added on every network transmission)
    double residual_time = 0.1;
    
    //the fraction of the ping that randomly time out
    double timed_out_fraction = 0.05;
    double timed_out_delay = 5;
    
    //the fraction of ping that randomly refuse connection
    double connection_refused_fraction = 0.3;
    
    private Random prg;
    private static GeoipInfo server_location = new GeoipInfo("Montreal","Québec","Canada",-73.55f,45.5f);
    private static GeoipInfo client_geoip = server_location;
    
    public PingsClientSimulation() {
        super();
        
        prg = new Random();
        if (client_geoip == server_location) {
            client_geoip = take_next_geoip_from_list();
        }
        
        subClients_pool = new subClient[subClient_number];
        subClients_threads_pool = new Thread[subClient_number];
        for (int i = 0; i < subClient_number; i++) {
            subClients_pool[i] = new subClientSimulation();
            subClients_threads_pool[i] = new Thread(subClients_pool[i]);
            subClients_threads_pool[i].setName("SubClient "+ i);
        }
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
     * A function simulating a ICMP ping (we don't need the address of the 
     * target only the position) 
     */
    private String ping(GeoipInfo target) {
        int nb_tries = 31;
        String fake_header = "ICMP 255.0.255.0 ";
        
        double ping_estimation =
            2 * (approximate_distance(client_geoip, target)/ approximate_speed)
            + (2 * average_random_time * prg.nextDouble());
        
        double total_time = 0;
        int nb_succes = 0;

        double x = prg.nextDouble();
        
        //Times out :
        if (x <  timed_out_fraction){
            total_time = nb_tries * timed_out_delay;
            nb_succes = 0;
        }
        //Connection refused :
        else if (x > 1 - connection_refused_fraction) {
            total_time = ping_estimation;
            nb_succes = 0 ;
        }
        //Success
        else{
            total_time = nb_tries * ping_estimation;
            nb_succes = nb_tries;
        }
        
        wait_for_network(total_time);
        return fake_header
            + nb_tries + " "
            + nb_succes    + " "
            +(int) (total_time * 1000) + "ms;";
    }
    
    /**
     * Emulate the use of the next address in the list received from the server
     * 
     * @return a  random GeoipInfo 
     */
    private GeoipInfo take_next_geoip_from_list() {
        int index = prg.nextInt(worldCapital.length);
        return worldCapital[index];
    }
    
    private InetAddress take_next_address_from_list() {
        byte[] bytes = new byte[4];
        prg.nextBytes(bytes);
        try{
            return InetAddress.getByAddress(bytes );
        }
        catch (Exception _) {
            return null;
        }
    }
    
    private void talk_with_server() {
        ping(server_location);
    }
    
    public void run () {
        
        m_source_geoip.set(client_geoip);
        
        for (int pings_index = 0; pings_index < subClient_number; pings_index++) {
            subClients_threads_pool[pings_index].start();
        }
    }
    
    /**
     * Simulate the subClient run thread
     */
    public class subClientSimulation extends subClient {
        public void run () {
            while (true) {
                //Take the next address in the list received from the server
                current_ping_dest =  take_next_address_from_list();
                current_dest_geoip =  take_next_geoip_from_list();
                current_ping_result = null;
                
                //After all the addresses are submitted send the result to the 
                //server
                if (prg.nextInt(15) == 0) talk_with_server();
                
                notifyObserversOfChange();
                
                //Ping this address
                current_ping_result = ping(current_dest_geoip);
                notifyObserversOfChange();
                
                //In case the thread is paused here
                if (!m_is_running.get()) {
                    while (!m_is_running.get()) {
                        synchronized(this) {
                            try {wait();} catch (InterruptedException e) {}
                        }
                    }
                }
            }
        }
    }
    
    public GeoipInfo[] worldCapital = {
        new GeoipInfo("Kabul", "Unknow", "Afghanistan", 69.11f, 34.28f), //E
        new GeoipInfo("Tirane", "Unknow", "Albania", 19.49f, 41.18f), //E
        new GeoipInfo("Algiers", "Unknow", "Algeria", 03.08f, 36.42f), //E
        new GeoipInfo("American Samoa", "Unknow", "Pago Pago",-14.16f, -170.43f), //w
        new GeoipInfo("Andorra", "Unknow", "Andorra la Vella", 01.32f, 42.31f), //E
        new GeoipInfo("Luanda", "Unknow", "Angola",-08.50f, 13.15f), //E
        new GeoipInfo("Antigua and Barbuda", "Unknow", "West Indies", -61.48f, 17.20f), //w
        new GeoipInfo("Argentina", "Unknow", "Buenos Aires",-36.30f, -60.00f), //w
        new GeoipInfo("Yerevan", "Unknow", "Armenia", 44.31f, 40.10f), //E
        new GeoipInfo("Oranjestad", "Unknow", "Aruba", -70.02f, 12.32f), //w
        new GeoipInfo("Canberra", "Unknow", "Australia",-35.15f, 149.08f), //E
        new GeoipInfo("Vienna", "Unknow", "Austria", 16.22f, 48.12f), //E
        new GeoipInfo("Baku", "Unknow", "Azerbaijan", 49.56f, 40.29f), //E
        new GeoipInfo("Nassau", "Unknow", "Bahamas", -77.20f, 25.05f), //w
        new GeoipInfo("Manama", "Unknow", "Bahrain", 50.30f, 26.10f), //E
        new GeoipInfo("Dhaka", "Unknow", "Bangladesh", 90.26f, 23.43f), //E
        new GeoipInfo("Bridgetown", "Unknow", "Barbados", -59.30f, 13.05f), //w
        new GeoipInfo("Minsk", "Unknow", "Belarus", 27.30f, 53.52f), //E
        new GeoipInfo("Brussels", "Unknow", "Belgium", 04.21f, 50.51f), //E
        new GeoipInfo("Belmopan", "Unknow", "Belize", -88.30f, 17.18f), //w
        new GeoipInfo("Benin", "Unknow", "Porto Novo (constitutional) / Cotonou (seat of government)", 02.42f, 06.23f), //E
        new GeoipInfo("Thimphu", "Unknow", "Bhutan", 89.45f, 27.31f), //E
        new GeoipInfo("Bolivia", "Unknow", "La Paz (administrative) / Sucre (legislative)",-16.20f, -68.10f), //w
        new GeoipInfo("Bosnia and Herzegovina", "Unknow", "Sarajevo", 18.26f, 43.52f), //E
        new GeoipInfo("Gaborone", "Unknow", "Botswana",-24.45f, 25.57f), //E
        new GeoipInfo("Brasilia", "Unknow", "Brazil",-15.47f, -47.55f), //w
        new GeoipInfo("British Virgin Islands", "Unknow", "Road Town", -64.37f, 18.27f), //w
        new GeoipInfo("Brunei Darussalam", "Unknow", "Bandar Seri Begawan", 115.00f, 04.52f), //E
        new GeoipInfo("Sofia", "Unknow", "Bulgaria", 23.20f, 42.45f), //E
        new GeoipInfo("Burkina Faso", "Unknow", "Ouagadougou", -01.30f, 12.15f), //w
        new GeoipInfo("Bujumbura", "Unknow", "Burundi",-03.16f, 29.18f), //E
        new GeoipInfo("Cambodia", "Unknow", "Phnom Penh", 104.55f, 11.33f), //E
        new GeoipInfo("Yaounde", "Unknow", "Cameroon", 11.35f, 03.50f), //E
        new GeoipInfo("Ottawa", "Unknow", "Canada", -75.42f, 45.27f), //w
        new GeoipInfo("Cape Verde", "Unknow", "Praia", -23.34f, 15.02f), //w
        new GeoipInfo("Cayman Islands", "Unknow", "George Town", -81.24f, 19.20f), //w
        new GeoipInfo("Central African Republic", "Unknow", "Bangui", 18.35f, 04.23f), //E
        new GeoipInfo("Chad", "Unknow", "N'Djamena", 14.59f, 12.10f), //E
        new GeoipInfo("Santiago", "Unknow", "Chile",-33.24f, -70.40f), //w
        new GeoipInfo("Beijing", "Unknow", "China", 116.20f, 39.55f), //E
        new GeoipInfo("Bogota", "Unknow", "Colombia", -74.00f, 04.34f), //w
        new GeoipInfo("Moroni", "Unknow", "Comros",-11.40f, 43.16f), //E
        new GeoipInfo("Brazzaville", "Unknow", "Congo",-04.09f, 15.12f), //E
        new GeoipInfo("Costa Rica", "Unknow", "San Jose", -84.02f, 09.55f), //w
        new GeoipInfo("Cote d'Ivoire", "Unknow", "Yamoussoukro", -05.17f, 06.49f), //w
        new GeoipInfo("Zagreb", "Unknow", "Croatia", 15.58f, 45.50f), //E
        new GeoipInfo("Havana", "Unknow", "Cuba", -82.22f, 23.08f), //w
        new GeoipInfo("Nicosia", "Unknow", "Cyprus", 33.25f, 35.10f), //E
        new GeoipInfo("Czech Republic", "Unknow", "Prague", 14.22f, 50.05f), //E
        new GeoipInfo("Democratic Republic of the Congo", "Unknow", "Kinshasa",-04.20f, 15.15f), //E
        new GeoipInfo("Copenhagen", "Unknow", "Denmark", 12.34f, 55.41f), //E
        new GeoipInfo("Djibouti", "Unknow", "Djibouti", 42.20f, 11.08f), //E
        new GeoipInfo("Roseau", "Unknow", "Dominica", -61.24f, 15.20f), //w
        new GeoipInfo("Dominica Republic", "Unknow", "Santo Domingo", -69.59f, 18.30f), //w
        new GeoipInfo("East Timor", "Unknow", "Dili",-08.29f, 125.34f), //E
        new GeoipInfo("Quito", "Unknow", "Ecuador",-00.15f, -78.35f), //w
        new GeoipInfo("Cairo", "Unknow", "Egypt", 31.14f, 30.01f), //E
        new GeoipInfo("El Salvador", "Unknow", "San Salvador", -89.10f, 13.40f), //w
        new GeoipInfo("Equatorial Guinea", "Unknow", "Malabo", 08.50f, 03.45f), //E
        new GeoipInfo("Asmara", "Unknow", "Eritrea", 38.55f, 15.19f), //E
        new GeoipInfo("Tallinn", "Unknow", "Estonia", 24.48f, 59.22f), //E
        new GeoipInfo("Ethiopia", "Unknow", "Addis Ababa", 38.42f, 09.02f), //E
        new GeoipInfo("Falkland Islands (Malvinas)", "Unknow", "Stanley",-51.40f, -59.51f), //w
        new GeoipInfo("Faroe Islands", "Unknow", "Torshavn", -06.56f, 62.05f), //w
        new GeoipInfo("Suva", "Unknow", "Fiji",-18.06f, 178.30f), //E
        new GeoipInfo("Helsinki", "Unknow", "Finland", 25.03f, 60.15f), //E
        new GeoipInfo("Paris", "Unknow", "France", 02.20f, 48.50f), //E
        new GeoipInfo("French Guiana", "Unknow", "Cayenne", -52.18f, 05.05f), //w
        new GeoipInfo("French Polynesia", "Unknow", "Papeete",-17.32f, -149.34f), //w
        new GeoipInfo("Libreville", "Unknow", "Gabon", 09.26f, 00.25f), //E
        new GeoipInfo("Banjul", "Unknow", "Gambia", -16.40f, 13.28f), //w
        new GeoipInfo("Georgia", "Unknow", "T'bilisi", 44.50f, 41.43f), //E
        new GeoipInfo("Berlin", "Unknow", "Germany", 13.25f, 52.30f), //E
        new GeoipInfo("Accra", "Unknow", "Ghana", -00.06f, 05.35f), //w
        new GeoipInfo("Athens", "Unknow", "Greece", 23.46f, 37.58f), //E
        new GeoipInfo("Nuuk", "Unknow", "Greenland", -51.35f, 64.10f), //w
        new GeoipInfo("Guadeloupe", "Unknow", "Basse-Terre", -61.44f, 16.00f), //w
        new GeoipInfo("Guatemala", "Unknow", "Guatemala", -90.22f, 14.40f), //w
        new GeoipInfo("Guernsey", "Unknow", "St. Peter Port", -02.33f, 49.26f), //w
        new GeoipInfo("Conakry", "Unknow", "Guinea", -13.49f, 09.29f), //w
        new GeoipInfo("Guinea-Bissau", "Unknow", "Bissau", -15.45f, 11.45f), //w
        new GeoipInfo("Georgetown", "Unknow", "Guyana", -58.12f, 06.50f), //w
        new GeoipInfo("Haiti", "Unknow", "Port-au-Prince", -72.20f, 18.40f), //w
        new GeoipInfo("Heard Island and McDonald Islands", "Unknow", " ",-53.00f, 74.00f), //E
        new GeoipInfo("Tegucigalpa", "Unknow", "Honduras", -87.14f, 14.05f), //w
        new GeoipInfo("Budapest", "Unknow", "Hungary", 19.05f, 47.29f), //E
        new GeoipInfo("Reykjavik", "Unknow", "Iceland", -21.57f, 64.10f), //w
        new GeoipInfo("India", "Unknow", "New Delhi", 77.13f, 28.37f), //E
        new GeoipInfo("Jakarta", "Unknow", "Indonesia",-06.09f, 106.49f), //E
        new GeoipInfo("Iran (Islamic Republic of)", "Unknow", "Tehran", 51.30f, 35.44f), //E
        new GeoipInfo("Baghdad", "Unknow", "Iraq", 44.30f, 33.20f), //E
        new GeoipInfo("Dublin", "Unknow", "Ireland", -06.15f, 53.21f), //w
        new GeoipInfo("Jerusalem", "Unknow", "Israel", -35.10f, 31.71f), //w
        new GeoipInfo("Rome", "Unknow", "Italy", 12.29f, 41.54f), //E
        new GeoipInfo("Kingston", "Unknow", "Jamaica", -76.50f, 18.00f), //w
        new GeoipInfo("Amman", "Unknow", "Jordan", 35.52f, 31.57f), //E
        new GeoipInfo("Astana", "Unknow", "Kazakhstan", 71.30f, 51.10f), //E
        new GeoipInfo("Nairobi", "Unknow", "Kenya",-01.17f, 36.48f), //E
        new GeoipInfo("Tarawa", "Unknow", "Kiribati", 173.00f, 01.30f), //E
        new GeoipInfo("Kuwait", "Unknow", "Kuwait", 48.00f, 29.30f), //E
        new GeoipInfo("Bishkek", "Unknow", "Kyrgyzstan", 74.46f, 42.54f), //E
        new GeoipInfo("Lao People's Democratic Republic", "Unknow", "Vientiane", 102.36f, 17.58f), //E
        new GeoipInfo("Riga", "Unknow", "Latvia", 24.08f, 56.53f), //E
        new GeoipInfo("Beirut", "Unknow", "Lebanon", 35.31f, 33.53f), //E
        new GeoipInfo("Maseru", "Unknow", "Lesotho",-29.18f, 27.30f), //E
        new GeoipInfo("Monrovia", "Unknow", "Liberia", -10.47f, 06.18f), //w
        new GeoipInfo("Libyan Arab Jamahiriya", "Unknow", "Tripoli", 13.07f, 32.49f), //E
        new GeoipInfo("Vaduz", "Unknow", "Liechtenstein", 09.31f, 47.08f), //E
        new GeoipInfo("Vilnius", "Unknow", "Lithuania", 25.19f, 54.38f), //E
        new GeoipInfo("Luxembourg", "Unknow", "Luxembourg", 06.09f, 49.37f), //E
        new GeoipInfo("Macao, China", "Unknow", "Macau", 113.33f, 22.12f), //E
        new GeoipInfo("Antananarivo", "Unknow", "Madagascar",-18.55f, 47.31f), //E
        new GeoipInfo("Macedonia (Former Yugoslav Republic)", "Unknow", "Skopje", 21.26f, 42.01f), //E
        new GeoipInfo("Lilongwe", "Unknow", "Malawi",-14.00f, 33.48f), //E
        new GeoipInfo("Malaysia", "Unknow", "Kuala Lumpur", 101.41f, 03.09f), //E
        new GeoipInfo("Male", "Unknow", "Maldives", 73.28f, 04.00f), //E
        new GeoipInfo("Bamako", "Unknow", "Mali", -07.55f, 12.34f), //w
        new GeoipInfo("Valletta", "Unknow", "Malta", 14.31f, 35.54f), //E
        new GeoipInfo("Martinique", "Unknow", "Fort-de-France", -61.02f, 14.36f), //w
        new GeoipInfo("Nouakchott", "Unknow", "Mauritania",-20.10f, 57.30f), //E
        new GeoipInfo("Mamoudzou", "Unknow", "Mayotte",-12.48f, 45.14f), //E
        new GeoipInfo("Mexico", "Unknow", "Mexico", -99.10f, 19.20f), //w
        new GeoipInfo("Micronesia (Federated States of)", "Unknow", "Palikir", 158.09f, 06.55f), //E
        new GeoipInfo("Moldova, Republic of", "Unknow", "Chisinau", 28.50f, 47.02f), //E
        new GeoipInfo("Maputo", "Unknow", "Mozambique",-25.58f, 32.32f), //E
        new GeoipInfo("Yangon", "Unknow", "Myanmar", 96.20f, 16.45f), //E
        new GeoipInfo("Windhoek", "Unknow", "Namibia",-22.35f, 17.04f), //E
        new GeoipInfo("Kathmandu", "Unknow", "Nepal", 85.20f, 27.45f), //E
        new GeoipInfo("Netherlands", "Unknow", "Amsterdam / The Hague (seat of Government)", 04.54f, 52.23f), //E
        new GeoipInfo("Netherlands Antilles", "Unknow", "Willemstad", -69.00f, 12.05f), //w
        new GeoipInfo("New Caledonia", "Unknow", "Noumea",-22.17f, 166.30f), //E
        new GeoipInfo("New Zealand", "Unknow", "Wellington",-41.19f, 174.46f), //E
        new GeoipInfo("Managua", "Unknow", "Nicaragua", -86.20f, 12.06f), //w
        new GeoipInfo("Niamey", "Unknow", "Niger", 02.06f, 13.27f), //E
        new GeoipInfo("Abuja", "Unknow", "Nigeria", 07.32f, 09.05f), //E
        new GeoipInfo("Norfolk Island", "Unknow", "Kingston",-45.20f, 168.43f), //E
        new GeoipInfo("North Korea", "Unknow", "Pyongyang", 125.30f, 39.09f), //E
        new GeoipInfo("Northern Mariana Islands", "Unknow", "Saipan", 145.45f, 15.12f), //E
        new GeoipInfo("Oslo", "Unknow", "Norway", 10.45f, 59.55f), //E
        new GeoipInfo("Masqat", "Unknow", "Oman", 58.36f, 23.37f), //E
        new GeoipInfo("Islamabad", "Unknow", "Pakistan", 73.10f, 33.40f), //E
        new GeoipInfo("Koror", "Unknow", "Palau", 134.28f, 07.20f), //E
        new GeoipInfo("Panama", "Unknow", "Panama", -79.25f, 09.00f), //w
        new GeoipInfo("Papua New Guinea", "Unknow", "Port Moresby",-09.24f, 147.08f), //E
        new GeoipInfo("Asuncion", "Unknow", "Paraguay",-25.10f, -57.30f), //w
        new GeoipInfo("Lima", "Unknow", "Peru",-12.00f, -77.00f), //w
        new GeoipInfo("Manila", "Unknow", "Philippines", 121.03f, 14.40f), //E
        new GeoipInfo("Warsaw", "Unknow", "Poland", 21.00f, 52.13f), //E
        new GeoipInfo("Lisbon", "Unknow", "Portugal", -09.10f, 38.42f), //w
        new GeoipInfo("Puerto Rico", "Unknow", "San Juan", -66.07f, 18.28f), //w
        new GeoipInfo("Doha", "Unknow", "Qatar", 51.35f, 25.15f), //E
        new GeoipInfo("Republic of Korea", "Unknow", "Seoul", 126.58f, 37.31f), //E
        new GeoipInfo("Bucuresti", "Unknow", "Romania", 26.10f, 44.27f), //E
        new GeoipInfo("Russian Federation", "Unknow", "Moskva", 37.35f, 55.45f), //E
        new GeoipInfo("Kigali", "Unknow", "Rawanda",-01.59f, 30.04f), //E
        new GeoipInfo("Saint Kitts and Nevis", "Unknow", "Basseterre", -62.43f, 17.17f), //w
        new GeoipInfo("Saint Lucia", "Unknow", "Castries", -60.58f, 14.02f), //w
        new GeoipInfo("Saint Pierre and Miquelon", "Unknow", "Saint-Pierre", -56.12f, 46.46f), //w
        new GeoipInfo("Saint Vincent and the Greenadines", "Unknow", "Kingstown", -61.10f, 13.10f), //w
        new GeoipInfo("Apia", "Unknow", "Samoa",-13.50f, -171.50f), //w
        new GeoipInfo("San Marino", "Unknow", "San Marino", 12.30f, 43.55f), //E
        new GeoipInfo("Sao Tome and Principe", "Unknow", "Sao Tome", 06.39f, 00.10f), //E
        new GeoipInfo("Saudi Arabia", "Unknow", "Riyadh", 46.42f, 24.41f), //E
        new GeoipInfo("Dakar", "Unknow", "Senegal", -17.29f, 14.34f), //w
        new GeoipInfo("Sierra Leone", "Unknow", "Freetown", -13.17f, 08.30f), //w
        new GeoipInfo("Bratislava", "Unknow", "Slovakia", 17.07f, 48.10f), //E
        new GeoipInfo("Ljubljana", "Unknow", "Slovenia", 14.33f, 46.04f), //E
        new GeoipInfo("Solomon Islands", "Unknow", "Honiara",-09.27f, 159.57f), //E
        new GeoipInfo("Mogadishu", "Unknow", "Somalia", 45.25f, 02.02f), //E
        new GeoipInfo("South Africa", "Unknow", "Pretoria (administrative) / Cape Town (legislative) / Bloemfontein (judicial)",-25.44f, 28.12f), //E
        new GeoipInfo("Madrid", "Unknow", "Spain", -03.45f, 40.25f), //w
        new GeoipInfo("Khartoum", "Unknow", "Sudan", 32.35f, 15.31f), //E
        new GeoipInfo("Paramaribo", "Unknow", "Suriname", -55.10f, 05.50f), //w
        new GeoipInfo("Swaziland", "Unknow", "Mbabane (administrative)",-26.18f, 31.06f), //E
        new GeoipInfo("Stockholm", "Unknow", "Sweden", 18.03f, 59.20f), //E
        new GeoipInfo("Bern", "Unknow", "Switzerland", 07.28f, 46.57f), //E
        new GeoipInfo("Syrian Arab Republic", "Unknow", "Damascus", 36.18f, 33.30f), //E
        new GeoipInfo("Dushanbe", "Unknow", "Tajikistan", 68.48f, 38.33f), //E
        new GeoipInfo("Bangkok", "Unknow", "Thailand", 100.35f, 13.45f), //E
        new GeoipInfo("Lome", "Unknow", "Togo", 01.20f, 06.09f), //E
        new GeoipInfo("Tonga", "Unknow", "Nuku'alofa",-21.10f, -174.00f), //w
        new GeoipInfo("Tunis", "Unknow", "Tunisia", 10.11f, 36.50f), //E
        new GeoipInfo("Ankara", "Unknow", "Turkey", 32.54f, 39.57f), //E
        new GeoipInfo("Ashgabat", "Unknow", "Turkmenistan", 57.50f, 38.00f), //E
        new GeoipInfo("Funafuti", "Unknow", "Tuvalu",-08.31f, 179.13f), //E
        new GeoipInfo("Kampala", "Unknow", "Uganda", 32.30f, 00.20f), //E
        new GeoipInfo("Ukraine", "Unknow", "Kiev (Russia)", 30.28f, 50.30f), //E
        new GeoipInfo("United Arab Emirates", "Unknow", "Abu Dhabi", 54.22f, 24.28f), //E
        new GeoipInfo("United Kingdom of Great Britain and Northern Ireland", "Unknow", "London", -00.05f, 51.36f), //w
        new GeoipInfo("United Republic of Tanzania", "Unknow", "Dodoma",-06.08f, 35.45f), //E
        new GeoipInfo("United States of America", "Unknow", "Washington DC", -77.02f, 39.91f), //w
        new GeoipInfo("United States of Virgin Islands", "Unknow", "Charlotte Amalie", -64.56f, 18.21f), //w
        new GeoipInfo("Montevideo", "Unknow", "Uruguay",-34.50f, -56.11f), //w
        new GeoipInfo("Tashkent", "Unknow", "Uzbekistan", 69.10f, 41.20f), //E
        new GeoipInfo("Vanuatu", "Unknow", "Port-Vila",-17.45f, 168.18f), //E
        new GeoipInfo("Caracas", "Unknow", "Venezuela", -66.55f, 10.30f), //w
        new GeoipInfo("Viet Nam", "Unknow", "Hanoi", 105.55f, 21.05f), //E
        new GeoipInfo("Belgrade", "Unknow", "Yugoslavia", 20.37f, 44.50f), //E
        new GeoipInfo("Lusaka", "Unknow", "Zambia",-15.28f, 28.16f), //E
        new GeoipInfo("Harare", "Unknow", "Zimbabwe",-17.43f, 31.02f), //E
    };
}
