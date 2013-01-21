import java.net.*;
import java.text.*;
import java.io.IOException;

/**
   This class does TCP-based pinging of a remote destination using a
   specific port

   @author   Steven Pigeon <pigeon@iro.umontreal.ca>
*/
public class TcpPinger implements Prober {
    /** Holds the last collected times */
    private String m_tcp_times;

    /** A reference to ClientInfo */
    private ClientInfo m_info;

    /**
       Returns the last collected times as a String.

       <p>The output is structured as follows

       <p>protocol addr:port nb_sent nb_received timeout [times]+

       <p>for example:

       <p><tt>TCP 132.204.24.179:50 10 0 1000 !6.713ms !4.896ms !3.770ms !4.588ms !8.609ms * !21.504ms !3.359ms !8.367ms !3.439ms</tt>

       @return The last collected times
    */
    public String getLastProbe() { return m_tcp_times; }

    public void clearProbe() { m_tcp_times = ""; }

    /**
       Pings an external IP address using the default port (80).
    */
    public int probe(InetAddress addr) throws InterruptedException {
        return probe(addr, 80);
    }

    /**
       Pings an external IP address using a specific port. A string
       containing the summary and times gathered is constructed, and
       accessible through TCP_Ping.getLastPings() after having called
       ping(). If an error occured, getLastPings() is undefined (may contain
       previous call's values).

       <p>The output is structured as follows

       <p>protocol addr:port nb_sent nb_received timeout [times]+

       <p>and times may be prefixed by ! if the connection is refused or
       fails rapidly, * (without time, just * alone) if it timed out, and
       prefixed by ? if some other error occured

       @see   TcpPinger#getLastPings()
       @see   TcpPinger#clearPings()
       @param addr The address to ping
       @param port The port to ping

       @return 0 (for compatibility with other pinger-classes that return the exit code)
    */
    public int probe(InetAddress addr, int port) throws InterruptedException {
        DecimalFormat format = new DecimalFormat("0.000");
        InetSocketAddress sock_addr = new InetSocketAddress(addr,port);
        String times = "";
        int fails = 0;
        
        for (int p = 0; p < m_info.getNumberOfPings(); p++) {
            String prefix = " ";

            if (p != 0 && fails < p) {
                // Sleep half a second
                Thread.sleep(500);
            } else if (fails == p && fails == 3) {
		// If the 3 first connection fail, we mark all pings as failed,
		// but they won't appear in the results.
            	fails = m_info.getNumberOfPings();
            	break;
            }

            boolean timed_out = false;
            long start = System.nanoTime();

            Socket ping_socket = null;
            try {
                ping_socket = new Socket();
                ping_socket.connect(sock_addr, m_info.getTCPTimeOut());
            }
            catch (ConnectException e) {
                fails++;
                prefix = " !";
            }
            catch (SocketTimeoutException e) {
                fails++;
                prefix = " *";
                timed_out = true;
            }
            catch (IOException e) {
                fails++;
                prefix = " ?";
            }
            finally {
                try {
                    if (ping_socket != null)
                        ping_socket.close();
                }
                catch (IOException e) {
                }
            }

            long stop = System.nanoTime();

            // if the connection was refused/quick error'd : it has ! as a prefix
            // if the connection timed out, it is shown as * (without time)
            // if some other error occured, it is prefixed with ?
            //
            times += prefix + (timed_out ? "" : format.format((stop-start) / 1.0e6f) + "ms" );
        }

        // The string returned is structured as follows:
        // protocol addr:port sent received timeoutvalue [ times ]+
        //
        m_tcp_times = "TCP " + addr.toString().split("/")[1] + ":" + port +
            " " + m_info.getNumberOfPings() + " " + (m_info.getNumberOfPings()-fails) +
            " " + m_info.getTCPTimeOut() + times;

        return 0;
    }

    /**
       Creates a TcpPinger (linked to a ClientInfo configuration)
       @param this_info A reference to a ClientInfo
       @see ClientInfo
    */
    public TcpPinger(ClientInfo this_info) {
        m_tcp_times = "";
        m_info = this_info;
    }
}
