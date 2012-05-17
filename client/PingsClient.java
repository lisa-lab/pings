import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pings client. Connects to the Pings server, retrieves addresses
 * to be pinged, pings them and submits the results back to the server.
 *
 * To use, embed in a thread object, then start the thread. If you want
 * to be notified when the source geoip info or ping destination change, you
 * can register yourself with addNotifier().
 *
 * We give up (and stop the thread) after a total of MAX_ERROR_COUNT errors
 * has occured. We also do exponential backoff on consecutive errors,
 * to avoid overloading the servers if they have a problem.
 *
 * @author Christian Hudon <chrish@pianocktail.org>
 */
public class PingsClient extends Observable implements Runnable {
    public final int MAX_ERROR_COUNT = 20;

    // These variables are initialized in the constructor and then
    // only accessed by the PingsClient thread. No need for locking, etc.
    private ClientInfo m_client_info;
    private Prober m_prober;
    private ServerProxy m_server_proxy;
    private int m_consecutive_error_count;
    private final static Logger LOGGER = Logger.getLogger(PingsClient.class.getName());

    // These variables are accessed both by the PingsClient thread and
    // by other threads using this class. They need to use something
    // like the Java Atomic types or other to prevent fun multithreading
    // bugs!
    private AtomicReference<String> m_nick;
    private AtomicReference<InetAddress> m_current_ping_dest;
    private AtomicReference<GeoipInfo> m_current_dest_geoip;
    private AtomicReference<GeoipInfo> m_source_geoip;
    private AtomicInteger m_total_error_count;

    public PingsClient(String server_hostname, int server_port) {
        m_client_info = new ClientInfo();
        m_prober = new CompositeProber(m_client_info);
        m_server_proxy = new ServerProxy(server_hostname, server_port);

        m_nick = new AtomicReference<String>("");
        m_current_ping_dest = new AtomicReference<InetAddress>();
        m_current_dest_geoip = new AtomicReference<GeoipInfo>();
        m_source_geoip = new AtomicReference<GeoipInfo>();
        m_total_error_count = new AtomicInteger();

        resetErrorCount();
    }

    public void setNickname(String nick) {
        m_nick.set(nick);
    }
    
    public String getNickname() {
    	return m_nick.get();
    }

    public InetAddress getCurrentPingDest() {
        return m_current_ping_dest.get();
    }

    public GeoipInfo getSourceGeoip() {
        return m_source_geoip.get();
    }

    public GeoipInfo getCurrentDestGeoip() {
        return m_current_dest_geoip.get();
    }

    public int getErrorCount() {
        return m_total_error_count.get();
    }

    /** Combines java.util.Observable's setChanged() and notifyObservers(). */
    private void notifyObserversOfChange() {
        setChanged();
        notifyObservers();
    }

    private void resetErrorCount() {
        m_consecutive_error_count = 0;
        m_total_error_count.set(0);
    }

    @Override
    public void run() {
        LOGGER.info("PingsClient worker thread starting.");
        resetErrorCount();

        try {
            while (true) {
                try {
                    // Get source geoip data and list of addresses to ping.
                    ServerProxy.Pings pings = m_server_proxy.getPings(m_client_info);
                    m_source_geoip.set(m_client_info.getGeoipInfo());
                    notifyObserversOfChange();
                    final int num_pings = pings.addresses.length;
                    LOGGER.log(Level.INFO, "Got {0} pings from server.", num_pings);

                    for (int i = 0; i < pings.addresses.length; i++) {
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }

                        // Clear old data.
                        m_prober.clearProbe();

                        // Ping address.
                        LOGGER.log(Level.INFO, "Pinging address {0} ({1}/{2}).",
                                   new Object[] { pings.addresses[i].toString(),
                                                  i+1, num_pings });
                        m_current_ping_dest.set(pings.addresses[i]);
                        m_current_dest_geoip.set(pings.geoip_info[i]);
                        notifyObserversOfChange();
                        m_prober.probe(pings.addresses[i]);

                        // Save ping result.
                        m_current_ping_dest.set(null);
                        m_current_dest_geoip.set(null);
                        pings.results[i] = m_prober.getLastProbe();
                        notifyObserversOfChange();
                        LOGGER.log(Level.INFO, "Ping result: {0}.",
                                   pings.results[i]);

                        // Clear consecutive error count.
                        m_consecutive_error_count = 0;
                    }

                    // Make sure nick is up-to-date before returning the
                    // ping results.
                    m_client_info.setNickname(m_nick.get());

                    // Submit results to server.
                    LOGGER.info("Submitting results to server.");
                    m_server_proxy.submitResults(m_client_info, pings);
                }
                catch (IOException e) {
                    final int total_error_count = m_total_error_count.incrementAndGet();
                    m_consecutive_error_count++;

                    LOGGER.log(Level.WARNING, "Exception caught in PingsClient thread.", e);

                    if (total_error_count > MAX_ERROR_COUNT) {
                        LOGGER.log(Level.SEVERE, "Too many errors; stopping PingsClient thread.");
                        throw new InterruptedException();
                    }
                    else {
                        // Exponential backoff for consecutive errors. Also
                        // avoid thread busy-loop if IOException keeps getting
                        // raised in call to getPings.
                        Thread.sleep(1000 * (int)Math.pow(2, m_consecutive_error_count));
                    }
                }
            }
        }
        catch (InterruptedException e) {
            // We've been interrupted. Stop doing work and return.
        }

        LOGGER.info("PingsClient worker thread ending.");
    }

    public static void main(String args[]) throws InterruptedException {
        if (args.length > 2) {
            System.err.println("Usage: PingsClient [hostname [port]]");
            System.exit(1);
        }

        String hostname = (args.length >= 1) ? args[0] : "localhost";

        int port = 6543;
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            }
            catch (NumberFormatException e) {
                System.err.println("Error: port argument must be an integer.");
                System.exit(2);
            }
        }

        PingsClient client = new PingsClient(hostname, port);
        client.setNickname("yoda");

        client.addObserver(new Observer() {
                public void update(Observable o, Object arg) {
                    PingsClient client = (PingsClient)o;

                    InetAddress current_ping_dest = client.getCurrentPingDest();
                    if (current_ping_dest != null)
                        System.out.printf("Current ping dest: %s\n",
                                          current_ping_dest.toString());

                    GeoipInfo dest_geoip_info = client.getCurrentDestGeoip();
                    if (dest_geoip_info != null) {
                        System.out.printf("Long: %f; lat: %f; city: %s; country: %s\n",
                                          dest_geoip_info.longitude,
                                          dest_geoip_info.latitude,
                                          dest_geoip_info.city,
                                          dest_geoip_info.country);
                    }
                }
            });

        Thread thread = new Thread(client);
        thread.start();
        thread.join();
    }
}
