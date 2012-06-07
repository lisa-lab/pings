import java.util.*;
import java.net.*;
import java.util.logging.Logger;


/**
   This class holds the configuration of the client, some elements of which
   are detected at launch, and where some others may be set through the
   interface and stored in local storage (in a context- and
   implementation-specific fashion; possibly in a cookie).

   <p>
   FIXME
   <ul>
     <li>Add hooks for certain information (roaming and internal IP addr).
   </ul>

   @author   Steven Pigeon <pigeon@iro.umontreal.ca>
*/
public class ClientInfo {
    /// Logging
    private final static Logger LOGGER = Logger.getLogger(ClientInfo.class.getName());

    /**
       Stores this client's UUID
    */
    String m_uuid;
    /**
        Stores the detected OS
        @see ClientInfo.OSType
    */
    private OSType m_os_type;

    /**
       Stores the user's nickname
    */
    private String m_nickname;

    /**
       Stores the address of the first network adapter
       FIXME: make sure it is indeed the adapter used
    */
    private InetAddress m_local_addr;

    /**
       Stores the global address of the client, as
       seen from "outside" its local network

       FIXME: needs an accessor to set it after getting it from server
    */
    private InetAddress m_global_addr;

    /**
       Stores the name of the first adapter
       FIXME: make sure it is indeed the adapter used
    */
    private String m_adapter;

    /**
       Holds the number of pings to perform
    */
    private int m_number_of_pings;

    /**
       Holds the number of pings per hop in traceroute. Because of the
       limitations of lovely Windows (XP), this should be 3, always.
    */
    private int m_number_of_traces;

    /**
       Time Out, in milliseconds, for TCP Ping. If a connection does not
       answer within m_tcp_timeout milliseconds, it times out and is reported
       as such.
    */
    private int m_tcp_timeout;

    /**
       Geoip information for the client address.
    */
    private GeoipInfo m_client_geoip_info;


    /**
       The name of the cookie
    */
    public final static String m_cookie_name="udem_ping";

    /**
       List the supported OSes.
    */
    public enum OSType {
        /** not detected yet */
        NotDetected,

        /** A Linux distribution */
        Linux,

        /** A variant of BSD */
        BSD,

        /** Apple's OS X */
        OSX,

        /** Windows XP */
        WinXP,

        /** Windows 7 */
        Win7,

        /** Windows 2000, Windows NT, Win6, Win8 */
        WinOther,

        /** Some other OS we haven't planned for yet */
        Unsupported
    };

    /**
         Finds the interface attached to a given (local) IP address.

        <p>It will need to be fixed later on. We guess that the first up and
        non-loopback interface is the one used to access outside. While it
        may be a cromulent first guess, it should be validated when actually
        connecting to the server.

        @param filter finds the adapter that has this address. If null, it
        will proceed to a complete detection of the (most probable) outbound
        IP address
    */
    public void detectInterface(InetAddress filter) {
        // Mostly inspired from Oracle's example
        Enumeration<NetworkInterface> interfaces = null;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        }
        catch (Exception e) { }

        if (interfaces != null) {
            if (filter == null) {
                // OK, we have no idea what address a connection may have,
                // so let us guess the first up and non-loopback addr
                //
                for (NetworkInterface interf : Collections.list(interfaces)) {
                    boolean good = false;
                    try {
                        good = interf.isUp() && !interf.isLoopback();
                    }
                    catch (Exception e) { }

                    if (good) {
                        // find a v4 address
                        for (InetAddress addr : Collections.list(interf.getInetAddresses()))
                            if (addr instanceof Inet4Address) {
                                m_local_addr = addr;
                                break;
                            }
                        m_adapter = interf.getDisplayName();
                        break;
                    }
                }
            }
            else {
                // We have an address to match so, let's find which
                // interface has it
                for (NetworkInterface interf : Collections.list(interfaces)) {
                    for (InetAddress addr : Collections.list(interf.getInetAddresses()))
                        if ((addr instanceof Inet4Address) &&
                            addr.equals(filter)) {
                            m_local_addr = addr;
                            break;
                        }
                    m_adapter=interf.getDisplayName();
                    break;
                }
            }
        }
        else {
            // FIXME: what now?
        }
    }

    /**
       Detects and caches the OS
       @return the detected OS
       @see ClientInfo.OSType
    */
    public OSType getOS() {
        // Does the detection once, and caches the result.
        if (m_os_type != OSType.NotDetected)
            return m_os_type;
        else {
            // let's detect the os.
            String os_name = System.getProperty("os.name").toLowerCase();

            // FIXME: find the finer-grained
            // OS names.
            if (os_name.compareTo("windows xp") == 0)
                m_os_type=OSType.WinXP;
            else if (os_name.compareTo("windows 7") == 0) // FIXME: cross-check
                m_os_type=OSType.Win7;
            else if (os_name.indexOf("win") >=0)
                m_os_type=OSType.WinOther; // nt 4? 2000? Vista? 8?
            else if (os_name.compareTo("linux") == 0)
                m_os_type=OSType.Linux; // Ubuntu? Recent? ???
            else if (os_name.indexOf("bsd") >=0)
                m_os_type=OSType.BSD;
            // add other OSes ... Solaris? OSX? ...DOS? :p

            return m_os_type;
        }
    }

    /**
       Returns the local address (as detected by the first adapter)

       @see ClientInfo#detectInterface

       @return an InetAddress
    */
    public InetAddress getAddress() {
        if (m_local_addr!=null)
            return m_local_addr;
        else {
            // detect my IP v4 addr (the first one?)
            detectInterface(null);
            return m_local_addr;
        }
    }

    /**
       Return the global address (as seen from
       the outside world)

       @return an InetAddress
    */
    public InetAddress getExternalAddress() throws RuntimeException {
        // FIXME: get external address from
        // service (or cookie (or...))
        if (m_global_addr != null)
            return m_global_addr;
        else
            throw new RuntimeException("External address not set");
    }

    /**
       Sets the external address for this client
       (it is detected while contacting the server
       so it is not known until the client tries to
       get its list of addresses to ping

       @param addr an InetAddress
    */
    public void setExternalAddress(InetAddress addr) {
        m_global_addr = addr;
    }

    /**
       Detects the (most probable) adapter for this client

       @return the adapter name
    */
    public String getInterface() {
        if (m_adapter != null)
            return m_adapter;
        else {
            detectInterface(null);
            return m_adapter;
        }
    }

    /**
       The UUID is generated at first launch or read from the configuration
       file if it already exists.

       @return the client's UUID
    */
    public String getUUID() { return m_uuid; }

    /**
       Sets a UUID, if none was read from the cookie.
    */
    public void setUUID() {
        m_uuid = UUID.randomUUID().toString();
    }

    /**
       @return the user's nickname or null if not set
    */
    public String getNickname() { return m_nickname; }

    /**
       Sets the user's nickname
       @param new_nickname the new nickname
    */
    public void setNickname(String new_nickname) { m_nickname = new_nickname; }

    /**
       @return the number of pings to perform
    */
    public int getNumberOfPings() { return m_number_of_pings; }

    /**
       The number of pings per hops is, for OS-specific reasons,
       fixed to the default of 3

       @return the number of pings per hops
    */
    public int getNumberOfTraces() { return m_number_of_traces; }

    /**
       @return the time in millisecond to wait before TCP_Ping times out
    */
    public int getTCPTimeOut() { return m_tcp_timeout; }

    /**
       Sets the geoip information for the client's IP address.
    */
    public void setGeoipInfo(GeoipInfo geoip_info) {
        m_client_geoip_info = geoip_info;
    }

    /**
       @return The geoip information for the client's IP address.
    */
    public GeoipInfo getGeoipInfo() {
        return m_client_geoip_info;
    }

    /**
       Uses the cookie strings to set preferences
       or (if the string is empty) sets defaults.
    */
    public void setPreferences(String cookie) {
        m_os_type = OSType.NotDetected;
        m_local_addr = null;
        m_global_addr = null;
        m_adapter = null;
        m_nickname = null;
        m_uuid = null;
        m_number_of_pings = 10;
        m_number_of_traces = 3;
        m_tcp_timeout = 1000; // In milliseconds

        m_os_type = getOS(); // Detect and set
        detectInterface(null); // Detects and sets m_local_addr and m_adapter

        if (cookie.length()!=0) {
            // gets the contents between the curly braces
            String[] keys=cookie.replaceAll("([^{]+)\\{([^}]+)\\}.*","$2").split(";");

            m_nickname=keys[0].split("=")[1];
            m_uuid=keys[1].split("=")[1];
        }
        else {
            m_nickname="";
            m_uuid = UUID.randomUUID().toString();
        }

        LOGGER.info("nick is " + m_nickname);
        LOGGER.info("uuid is " + m_uuid);
    }

    /**
       Saves preferences
    */
    public String getPreferences() {

        String cookie=m_cookie_name+"={nickname=";
        if (m_nickname != null)
            cookie+=m_nickname;

        // FIXME: expiration date may be necessary;
        // maybe it should be done in the cookie manager
        // also: not sure path=/ is useful.
        cookie+=":uuid="+m_uuid+"};path=/';"; // expires="+expirationDate();

        return cookie;
    }

    /**
       Constructor
    */
    public ClientInfo(String cookie) {
        setPreferences(cookie);
    }

    public ClientInfo() {
        this("");
    }
}
