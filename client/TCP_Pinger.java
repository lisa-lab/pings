import java.net.*;
import java.text.*;

/**
   This class does TCP-based pinging of a remote destination using a
   specific port


   @author   Steven Pigeon <pigeon@iro.umontreal.ca>
   @version  0.5
   @since    2012-03
*/
public class TCP_Pinger
 {
  /** Holds the last collected times */
  private String tcp_times;

  /** A reference to Client_Info */
  private Client_Info info;

  /**
     Returns the last collected times as a String.

     <p>The output is structured as follows

     <p>protocol addr:port nb_sent nb_received timeout [times]+

     <p>for example:

     <p><tt>TCP 132.204.24.179:50 10 0 1000 !6.713ms !4.896ms !3.770ms !4.588ms !8.609ms * !21.504ms !3.359ms !8.367ms !3.439ms</tt>


     @return The last collected times
  */
  public String getLastPings() { return tcp_times; }

  /**
     Clears the last collected times
  */
  public void clearPings() { tcp_times=""; }

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

    @see   TCP_Pinger#getLastPings()
    @see   TCP_Pinger#clearPings()
    @param addr The address to ping
    @param port The port to ping

    @return 0 (for compatibility with other pinger-classes that return the exit code)
  */
  public int ping(InetAddress addr,int port)
   {
    DecimalFormat format=new DecimalFormat("0.000");
    InetSocketAddress sock_addr=new InetSocketAddress(addr,port);
    String times="";
    int fails=0;
    for (int p=0;p<info.getNumberOfPings();p++)
     {
      Socket ping_socket = new Socket();
      String prefix=" ";

      if (p!=0)
       {
        try{ Thread.sleep(500); } // sleep half a second
        catch (Exception e) { /* slept less? */ }
       }

      boolean timed_out=false;
      long start=System.nanoTime();
      try { ping_socket.connect(sock_addr,info.getTCPTimeOut()); }

      catch (ConnectException e) { fails++; prefix=" !"; }
      catch (SocketTimeoutException e) { fails++; prefix=" *"; timed_out=true; }
      catch (Exception e) { fails++; prefix=" ?"; }

      long stop=System.nanoTime();

      // if the connection was refused/quick error'd : it has ! as a prefix
      // if the connection timed out, it is shown as * (without time)
      // if some other error occured, it is prefixed with ?
      //
      times+=prefix+(timed_out ? "" : format.format((stop-start)/1000000.0f)+"ms" );
     }

    // The string returned is structured as follows:
    // protocol addr:port sent received timeoutvalue [ times ]+
    //
    tcp_times=
     "TCP "
     +addr.toString().split("/")[1]+":"+port
     +" "+info.getNumberOfPings()+" "+(info.getNumberOfPings()-fails)
     +" "+info.getTCPTimeOut()
     +times;

    return 0;
   }

  /**
     Creates a TCP_Pinger (linked to a Client_Info configuration)
     @param this_info A reference to a Client_Info
     @see Client_Info
  */
  public TCP_Pinger(Client_Info this_info )
   {
    tcp_times="";
    info=this_info;
   }
 }
