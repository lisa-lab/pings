import java.util.*;
import java.net.*;
import java.util.prefs.Preferences;

/**
   This class holds the configuration of the client, some elements of which
   are detected at launch, and where some others may be set through the
   interface and stored in local storage (in a context- and
   implementation-specific fashion; possibly in a cookie).

   <p>
   FIXME: add hooks for certain information (roaming and internal IP addr)

   @author   Steven Pigeon <pigeon@iro.umontreal.ca>
   @version  0.5
   @since    2012-03   
*/
public class Client_Info
 {
  /** 
      Stores the preferences in a possibly persistent fashion
  */
  private Preferences prefs;

  /** 
      Stores the detected OS
      @see Client_Info.OS_Type
  */
  private OS_Type this_os_type;

  /**
     Stores the user's nickname
  */
  private String nickname;

  /**
     Stores the address of the first network adapter
     FIXME: make sure it is indeed the adapter used
  */
  private InetAddress my_local_addr;

  /**
     Stores the global address of the client, as
     seen from "outside" its local network

     FIXME: needs an accessor to set it after getting it from server
  */
  private InetAddress my_global_addr;

  /**
     Stores the name of the first adapter
     FIXME: make sure it is indeed the adapter used
  */
  private String my_adapter;

  /**
     Holds the number of pings to perform
  */
  private int number_of_pings;

  /**
     Holds the number of pings per hop in traceroute. Because of the
     limitations of lovely Windows (XP), this should be 3, always.
  */
  private int number_of_traces;

  /**
     Time Out, in milliseconds, for TCP Ping. If a connection does not
     answer within tcp_timeout milliseconds, it times out and is reported
     as such.
  */
  private int tcp_timeout;

  /**
     List the supported OSes.
  */
  public enum OS_Type
   {
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
  public void detectInterface(InetAddress filter)
   {
    // Mostly inspired from Oracle's example
    Enumeration<NetworkInterface> interfaces=null;
    try { interfaces = NetworkInterface.getNetworkInterfaces(); }
    catch (Exception e) { }

    if (interfaces!=null)
     {
      if (filter==null)
       {
        // OK, we have no idea what address
        // a connection may have, so let us
        // guess the first up and non-loopback addr
        //
        for (NetworkInterface interf : Collections.list(interfaces))
         {
          boolean good=false;
          try { good = interf.isUp() && !interf.isLoopback(); }
          catch (Exception e) { }

          if (good)
           {
            // find a v4 address
            for (InetAddress addr : Collections.list(interf.getInetAddresses()))
             if (addr instanceof Inet4Address)
              {
               my_local_addr=addr;
               break;
              }
            my_adapter=interf.getDisplayName();
            break;
           }
         }
       }
      else
       {
        // we have an address to match
        // so, let's find which interface
        // has it
        for (NetworkInterface interf : Collections.list(interfaces))
         {
          for (InetAddress addr : Collections.list(interf.getInetAddresses()))
           if ((addr instanceof Inet4Address) && 
               addr.equals(filter))
            {
             my_local_addr=addr;
             break;
            }
          my_adapter=interf.getDisplayName();
          break;
         }
       }
     }
    else
     {
      // FIXME: what now?
     }
   }

  /**
     Detects and caches the OS
     @return the detected OS
     @see Client_Info.OS_Type
  */
  public OS_Type getOS()
   {
    // Does the detection once,
    // and caches the result.
    if (this_os_type!=OS_Type.NotDetected)
     return this_os_type;
    else
     {
      // let's detect the os.
      String os_name=System.getProperty("os.name").toLowerCase();
      //System.out.println(os_name);

      // FIXME: find the finer-grained
      // OS names.
      if ( os_name.compareTo("windows xp") ==0 )
       this_os_type=OS_Type.WinXP;
      else if (os_name.compareTo("windows 7") ==0 ) // FIXME: cross-check
       this_os_type=OS_Type.Win7;
      else if (os_name.indexOf("win") >=0 )
       this_os_type=OS_Type.WinOther; // nt 4? 2000? Vista? 8?
      else if (os_name.compareTo("linux") == 0)
       this_os_type=OS_Type.Linux; // Ubuntu? Recent? ???
      else if (os_name.indexOf("bsd") >=0 )
       this_os_type=OS_Type.BSD;
      // add other OSes ... Solaris? OSX? ...DOS? :p

      return this_os_type;
     }
   }
  
  /**
     Returns the local address (as detected by the first adapter)

     @see Client_Info#detectInterface

     @return an InetAddress
  */
  public InetAddress getAddress()
  {
   if (my_local_addr!=null)
    return my_local_addr;
   else
    {
     // detect my IP v4 addr (the first one?)
     detectInterface(null);
     return my_local_addr;
    }
  }

  /**
     Return the global address (as seen from
     the outside world)

     @return an InetAddress
  */
  public InetAddress getExternalAddress() throws RuntimeException
   {
    // FIXME: get external address from
    // service (or cookie (or...))
    if (my_global_addr!=null)
     return my_global_addr;
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
  public void setExternalAddress(InetAddress addr)
   {
    my_global_addr=addr;
   }

  /**
     Detects the (most probable) adapter for this client

     @return the adapter name
  */
  public String getInterface()
  {
   if (my_adapter!=null)
    return my_adapter;
   else
    {
     detectInterface(null);
     return my_adapter;
    }
  }

  /**
     @return the user's nickname or null if not set
  */
  public String getNickname() { return nickname; }

  /**
     Sets the user's nickname
     @param new_nickname the new nickname
  */
  public void setNickname(String new_nickname) { nickname=new_nickname; } 

  /**
     @return the number of pings to perform
  */
  public int getNumberOfPings() { return number_of_pings; }

  /**
     The number of pings per hops is, for OS-specific reasons,
     fixed to the default of 3

     @return the number of pings per hops
  */
  public int getNumberOfTraces() { return number_of_traces; }

  /**
     @return the time in millisecond to wait before TCP_Ping times out
  */
  public int getTCPTimeOut() { return tcp_timeout; }


  /**
     Loads preferences/sets defaults
  */
  public void loadPreferences()
  {
   this_os_type=OS_Type.NotDetected;
   my_local_addr=null;
   my_global_addr=null;
   my_adapter=null;
   nickname=null;
   number_of_pings=10;
   number_of_traces=3;
   tcp_timeout = 1000; // in milliseconds
   
   this_os_type=getOS(); // detect and set
   detectInterface(null); // detects and sets my_local_addr and my_adapter

   
   nickname=prefs.get("nickname",null);
   System.out.println("nick is "+nickname);
  }

  /**
     Saves preferences
  */
  public void savePreferences()
  {
   if (nickname!=null)
    prefs.put("nickname",nickname);

   // it is magically persistant on
   // object destruction or something.
   try { prefs.flush(); }
   catch (Exception e) { e.printStackTrace(); }
  }

  /**
     Default constructor
  */
  Client_Info()
  {
   prefs=Preferences.userRoot().node("pinger");//this.getClass().getName());
   loadPreferences();
  }
 }
