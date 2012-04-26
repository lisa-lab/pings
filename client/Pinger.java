import java.net.InetAddress;

/**
   Common interface to all pingers.

   @author Christian Hudon <chrish@pianocktail.org>
*/
public interface Pinger {
    /**
       Returns the last collected times as a String.

       <p>The output is structed as follows:

       <p>protocol addr sent received total_elapsed_time [times+]

       <p>for example:

       <p><tt>ICMP 132.204.24.179 11 10 10022ms 3.66ms 7.14ms 2.39ms 11.6ms 3.59ms 5.56ms 2.93ms 7.54ms 5.75ms 4.54m</tt>

       @return The last collected times
    */
    public String getLastPings();

    /**
       Clears the last collected times
    */
    public void clearPings();

    /**
       Pings an external IP address. A string containing the summary and times
       gathered is constructed, and accessible through
       Pinger.getLastPings() after having called ping(). If an error
       occured, getLastPings() is undefined (may contain previous call's
       values).

       @see   Pinger#getLastPings()
       @see   Pinger#clearPings()
       @param addr The address to ping

       @return The external command return code
    */
    public int ping(InetAddress addr);
}
