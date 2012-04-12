import java.net.HttpURLConnection;
import java.net.URL;
import java.net.InetAddress;
import java.io.*;

import org.json.simple.JSONValue;

/** Interface to the Pings server.
 *  @todo Add methods to get addresses to ping, and to submit ping results.
 *  @todo Error handling when the http status returned is not 200.
 *
 *  @author Christian Hudon <chrish@pianocktail.org>
 */
public class ServerProxy {

    public ServerProxy(String server_hostname) {
        m_server_hostname = server_hostname;
    }

    /// The hostname for the Pings server.
    private String m_server_hostname;
    private static final String CHARSET = "UTF-8";

    /** Sends a JSON object via POST to the given request URL path. Returns the
        JSON response object. */
    private Object doJsonRequest(String request_path, Object content) throws IOException {
        // Serialize content to JSON.
        String json_request = JSONValue.toJSONString(content);

        // Open connection to URL.
        URL server_url = new URL("http", m_server_hostname, request_path);

        HttpURLConnection connection = (HttpURLConnection)server_url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Accept-Charset", CHARSET);
        connection.setRequestProperty("Content-Type", "application/json;charset=" + CHARSET);

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
            // TODO Improve error handling.
            return null;
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
