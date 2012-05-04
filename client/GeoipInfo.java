import org.json.simple.JSONObject;

/** Representation of the geoip information for a given IP address, created
    from a JSON representation of the information returned by the Pings server.
    Includes the longitude and latitude, and the city and country.
 */
public class GeoipInfo {

    final public String city;
    final public String country;

    final public double longitude;
    final public double latitude;

    /** Constructs a GeoipInfo object from JSON information returned by
        the Pings server. */
    public GeoipInfo(JSONObject json) {
        city = (String)json.get("city");
        country = (String)json.get("country_name");
        
        /*FIXME : jsonobject get cast into doubles which are cast into floats 
        and then cast back into double*/
        longitude = ((Double)json.get("longitude")).floatValue();
        latitude = ((Double)json.get("latitude")).floatValue();
    }
    
    /*
     * An alternative constructor for testing purpose
     */
    //TODO : remove it
    public GeoipInfo(String city,String country,double longitude,double latitude) {
    	this.city = city;
    	this.country = country;
    	this.longitude = longitude;
    	this.latitude = latitude;
    }
}

