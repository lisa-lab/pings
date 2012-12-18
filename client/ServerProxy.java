import java.net.HttpURLConnection;
import java.net.URL;
import java.net.InetAddress;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

/** Interface to the Pings server. Allows retrieving a list
 *  of addresses to ping (together with their geoip information),
 *  and submitting back the results.
 *
 *  All connection problems are throws as subclasses of IOException
 *  and must be handled by the caller.
 *
 *  @author Christian Hudon <chrish@pianocktail.org>
 */
public class ServerProxy {
	
	/** Timeouts in milliseconds*/
	private int connection_timeout = 10000;
	private int socket_operation_timeout = 10000;

    /** Container class for the Pings data. Obtained from getPings().
        To use, fill in the results array with the corresponding Prober
        results for each address, and give to submitResults() method
        of ServerProxy. */
    public static class Pings {
        public InetAddress[] addresses;
        public GeoipInfo[] geoip_info;
        public String[] results;
        public String token;
	public long time_fetched;
	public long min_round_time;
    }

    /** Exception class for communication errors with the server. */
    public static class Exception extends IOException {
        public Exception(String error_msg) {
            super(error_msg);
        }
    }

    /** Constructs a new ServerProxy instance, with the server
        at the given hostname and default port (80). */
    public ServerProxy(String server_hostname) {
        this(server_hostname, 80);
    }

    /** Constructs a new ServerProxy instance, with the server
        at the given hostname and port. */
    public ServerProxy(String server_hostname, int server_port) {
        m_server_hostname = server_hostname;
        m_server_port = server_port;
    }

    /** Retrieves and returns a list of addresses to ping. Also updates
        the ClientInfo object with the geoip data for the client address. */
    public Pings getPings(ClientInfo client_info) throws IOException {
        // Send request to server. Returns a dict with the following keys
        // and values: "token" (a string), "pings" (a list of IP addresses),
        // "geoip" (a list of dicts, one per IP address).
        JSONObject json_result = (JSONObject)doJsonRequest("/get_pings", null);

        // Update client_info with new client-related information.
        JSONObject client_geoip = (JSONObject)json_result.get("client_geoip");
        if (client_geoip != null){
            client_info.setGeoipInfo(new GeoipInfo(client_geoip));
	}
        String client_ip = (String)json_result.get("client_ip");
	if (client_ip != null && !client_ip.equals("")){
            client_info.setExternalAddress(InetAddress.getByName(client_ip));
	}

        // Fill Pings instance from JSON results of our request.
        Pings pings = new Pings();
        pings.token = (String)json_result.get("token");
	pings.time_fetched = System.currentTimeMillis();
        JSONArray addresses = (JSONArray)json_result.get("pings");
        int num_addresses = addresses.size();
        pings.addresses = new InetAddress[num_addresses];
        for (int i = 0; i < num_addresses; i++) {
            pings.addresses[i] = InetAddress.getByName((String)addresses.get(i));
        }

	// Fill GEOIP data from JSON results
        JSONArray all_geoip_data = (JSONArray)json_result.get("geoip");
        pings.geoip_info = new GeoipInfo[num_addresses];
        if (all_geoip_data != null) {
            for (int i = 0; i < num_addresses; i++) {
                Object o = all_geoip_data.get(i);
                if (o != null) {
                    pings.geoip_info[i] = new GeoipInfo((JSONObject)o);
                }
                else
                    pings.geoip_info[i] = null;
            }
        }

        pings.results = new String[num_addresses];

	// Fill min_round_time from JSON results
	pings.min_round_time = ((java.lang.Long)json_result.get("min_round_time")).longValue();
        return pings;
    }

    /** Submits the ping results back to the server. */
    public void submitResults(ClientInfo client_info, Pings pings) throws IOException {
        // Build JSON request, a dict with the following keys and values:
        // "token" (a string... the same as return by getPings), "results"
        // (a list of arbitrary JSON objects, one per ping), and optionally
        // "userid" (a string).
        //
        // Don't use JSONObject here. As currently written, it's not
        // generics-aware and so it's possible to use it without triggering
        // unchecked exception warnings. JSONObject doesn't buy us anything
        // over using a HashMap<String, Object> here anyways.
        HashMap<String, Object> json_request = new HashMap<String, Object>();
        json_request.put("token", pings.token);
        List<String> results = new ArrayList<String>();
        results.add(client_info.getAddress().getHostAddress());
        results.add(client_info.getInterface());
        results.addAll(Arrays.asList(pings.results));
        json_request.put("results", results);
        String nick = client_info.getNickname();
        if (nick != null && nick.length() != 0)
            json_request.put("userid", nick);
        
        // Send request to server. Returns a constant (at least for now).
        Object json_result = doJsonRequest("/submit_ping_results", json_request);
    }

    /// The hostname of the Pings server.
    private String m_server_hostname;
    /// The port of the Pings server.
    private int m_server_port;
    private static final String CHARSET = "UTF-8";

    /** Sends a JSON object via POST to the given request URL path. Returns the
        JSON response object. */
    private Object doJsonRequest(String request_path, Object content) throws IOException {
        // Serialize content to JSON.
        String json_request = JSONValue.toJSONString(content);

        // Open connection to URL.
        URL server_url = new URL("http", m_server_hostname, m_server_port,
                                 request_path);

        HttpURLConnection connection = (HttpURLConnection)server_url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Accept-Charset", CHARSET);
        connection.setRequestProperty("Content-Type", "application/json;charset=" + CHARSET);
        connection.setConnectTimeout(connection_timeout);
        connection.setReadTimeout(socket_operation_timeout);

        // Write request.
        OutputStream output = connection.getOutputStream();
        try {
            output.write(json_request.getBytes(CHARSET));
        }
        finally {
            output.close();
        }
        
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new ServerProxy.Exception(String.format("Server returned HTTP status %d", status));
        }

        // Read back reply.
        InputStream response = connection.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(response));

        StringBuilder sb_response = new StringBuilder();
        String chunk = br.readLine();
        while (chunk != null) {
            sb_response.append(chunk);
            chunk = br.readLine();
        }

        return JSONValue.parse(sb_response.toString());
    }
}
