import java.io.IOException;
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
    private IcmpPinger m_pinger;
    private ServerProxy m_server_proxy;

    // These variables are accessed both by the PingsClient thread and
    // by other threads using this class. They need to use something
    // like the Java Atomic types or other to prevent fun multithreading
    // bugs!
    private AtomicBoolean m_is_running;
    private AtomicReference<String> m_nick;

    public PingsClient(String server_hostname, int server_port) {
        m_client_info = new ClientInfo();
        m_pinger = new IcmpPinger(m_client_info);
        m_server_proxy = new ServerProxy(server_hostname, server_port);

        m_is_running = new AtomicBoolean(true);
    }

    public void setNickname(String nick) {
        m_nick.set(nick);
    }

    public void halt() {
        m_is_running.set(false);
    }

    public void run() {
        while (m_is_running.get()) {
            try {
                ServerProxy.Pings pings = m_server_proxy.getPings(m_client_info);

                for (int i = 0; i < pings.addresses.length; i++) {
                    m_pinger.clearPings();
                    m_pinger.ping(pings.addresses[i]);
                    pings.results[i] = m_pinger.getLastPings();
                }

                // Make sure nick is up-to-date before returning the ping results.
                m_client_info.setNickname(m_nick.get());
                m_server_proxy.submitResults(m_client_info, pings);
            }
            catch (IOException e) {
                // Avoid thread busy-loop if IOException keeps getting raised
                // in call to getPings.
                try {
                    sleep(1000);
                }
                catch (InterruptedException e2) {
                }
            }
        }
    }

    public static void main(String args[]) {
        PingsClient client = new PingsClient("localhost", 6543);
        client.start();
    }
}
