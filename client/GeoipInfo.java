import org.json.simple.JSONObject;

/** Representation of the geoip information for a given IP address, created
    from a JSON representation of the information returned by the Pings server.
    Includes the longitude and latitude, and the city and country.
 */
public class GeoipInfo {

    final public String city;
    final public String country;
    final public String region;

    final public double longitude;
    final public double latitude;

    /** Constructs a GeoipInfo object from JSON information returned by
        the Pings server. */
    public GeoipInfo(JSONObject json) {
        city = (String)json.get("city");
        region = (String)json.get("region_name");
        country = (String)json.get("country_name");
        
        longitude = ((Number)json.get("longitude")).doubleValue();
        latitude = ((Number)json.get("latitude")).doubleValue();
    }
    
    /**
     * An alternative constructor for testing purposes.
     */
    public GeoipInfo(String city, String region, String country,
                     double longitude, double latitude) {
    	this.city = city;
    	this.country = country;
	this.region = region;
    	this.longitude = longitude;
    	this.latitude = latitude;
    }
    
    public boolean equals(GeoipInfo o) {
    	if (o == null) return false;
    	return o.city.equals(this.city)
    	 && o.country.equals(this.country)
    	 && o.region.equals(this.region)
    	 && o.latitude == this.latitude
    	 && o.longitude == this.longitude;	
    }
}

