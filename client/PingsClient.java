import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/** Pings client. Connects to the Pings server, retrieves addresses
 *  to be pinged, pings them and submits the results back to the server.
 *
 * @todo IMPORTANT: Give up after a certain number of errors in a row,
 * instead of continuously hammering the server! If doing more than a few
 * retries, exponential backoff would probably be a good idea too.
 * @todo Improve error handling when server_proxy raises an exception.
 * @author Christian Hudon <chrish@pianocktail.org>
 */
public class PingsClient extends Thread {
    // These variables are initialized in the constructor and then
    // only accessed by the PingsClient thread. No need for locking, etc.
    private ClientInfo m_client_info;
    private Pinger m_pinger;
    private ServerProxy m_server_proxy;
    private final static Logger LOGGER = Logger.getLogger(PingsClient.class.getName());

    // These variables are accessed both by the PingsClient thread and
    // by other threads using this class. They need to use something
    // like the Java Atomic types or other to prevent fun multithreading
    // bugs!
    private AtomicReference<String> m_nick;

    public PingsClient(String server_hostname, int server_port) {
        m_client_info = new ClientInfo();
        m_pinger = new TcpPinger(m_client_info);
        m_server_proxy = new ServerProxy(server_hostname, server_port);

        m_nick = new AtomicReference<String>("");
    }

    public void setNickname(String nick) {
        m_nick.set(nick);
    }

    public void run() {
        try {
            while (true) {
                LOGGER.info("PingsClient worker thread starting.");
                try {
                    ServerProxy.Pings pings = m_server_proxy.getPings(m_client_info);
                    final int num_pings = pings.addresses.length;
                    LOGGER.log(Level.INFO, "Got {0} pings from server.", num_pings);

                    for (int i = 0; i < pings.addresses.length; i++) {
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }

                        m_pinger.clearPings();

                        LOGGER.log(Level.INFO, "Pinging address {0} ({1}/{2}).",
                                   new Object[] { pings.addresses[i].toString(),
                                                  i+1, num_pings });
                        m_pinger.ping(pings.addresses[i]);

                        pings.results[i] = m_pinger.getLastPings();
                        LOGGER.log(Level.INFO, "Ping result: {0}.", pings.results[i]);
                    }

                    // Make sure nick is up-to-date before returning the ping results.
                    m_client_info.setNickname(m_nick.get());

                    LOGGER.info("Submitting results to server.");
                    m_server_proxy.submitResults(m_client_info, pings);
                }
                catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Exception caught in PingsClient thread.", e);
                    // Avoid thread busy-loop if IOException keeps getting raised
                    // in call to getPings.
                    sleep(1000);
                }
            }
        }
        catch (InterruptedException e) {
            // We've been interrupted. No more work.
        }

        LOGGER.info("PingsClient worker thread ending.");
    }

    public static void main(String args[]) {
        PingsClient client = new PingsClient("localhost", 6543);
        client.setNickname("yoda");
        client.start();
    }
}
