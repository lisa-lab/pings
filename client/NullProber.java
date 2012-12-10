import java.net.InetAddress;

/**
   This is a NullProber to help test the server. It prob nothing, so it work very fast!

   @author Frédéric Bastien
*/
public class NullProber implements  Prober {
    public String getLastProbe() { return "Null"; }

    /**
       Clears the last collected times
    */
    public void clearProbe() {}

    /**
       Probes (via one of ping, traceroute, etc.) an external IP address.
       A string containing the summary and times
       gathered is constructed, and accessible through
       Prober.getLastProbe() after having called probe(). If an error
       occured, getLastProbe() is undefined (may contain previous call's
       values).

       @see   Prober#getLastProbe()
       @see   Prober#clearProbe()
       @param addr The address to ping

       @return The external command return code
    */
    public int probe(InetAddress addr) throws InterruptedException {return 0;}
}
