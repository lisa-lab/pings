import java.util.*;
import java.util.regex.*;
import java.net.*;

/**
   This class launches an external ping command and returns the collected
   times.


   @author   Steven Pigeon <pigeon@iro.umontreal.ca>
   @version  0.5
   @since    2012-03
*/
public class ICMP_Pinger
{
 /** Holds the last collected times */
 private String icmp_times;

 /** A reference to Client_Info */
 private Client_Info info;

 /** Describes OS-specific commands for external ping command */
 private static final String[][] os_specific_command=
 {
  // the number of pings paramater must be LAST
  // because it is appended at run-time using
  // Client_Info.getNumberOfPings()
  //
  {"ping","-w","30","-W","2","-c"}, // Linux
  {"ping","-c"}, // BSD, OSX ?
  {"ping","-w","2000","-n"} // Windows xp
 };

 /** OS-specific regexes to get the summary line */
 private static final String[][] os_specific_summary_regex=
  {
   // On *nixes: "7 packets transmitted, 6 received, 14% packet loss, time 6014ms"
   /** matches *nixes ping summary line */
   {"(([0-9]+)\\s[\\w|\\s]+),\\s(([0-9]+)\\s[\\w|\\s]+),.*(time)\\s(.*)","$2 $4 $6"}, // linux/bsd/osx

   // On Windows " Packets: Sent = 10, Received = 9, Lost = 1 (10% loss)"
   /** matches Windows ping summary line */
   {"[\\s|\\w|:]+=\\s([0-9]+),[\\s|\\w]+=\\s([0-9]+),.*","$1 $2 ?ms"} // window xp
  };

 /**
     Returns the last collected times as a String.

     <p>The output is structed as follows:

     <p>protocol addr sent received total_elapsed_time [times+]

     <p>for example:

     <p><tt>ICMP 132.204.24.179 11 10 10022ms 3.66ms 7.14ms 2.39ms 11.6ms 3.59ms 5.56ms 2.93ms 7.54ms 5.75ms 4.54m</tt>

     @return The last collected times
 */
 public String getLastPings() { return icmp_times; }

 /**
    Clears the last collected times
 */
 public void clearPings() { icmp_times=""; }

 /**
     Returns the ping summary (how many sent, how many received, and if
     possible the time in milliseconds elapsed)

     @return The ping summary
 */
 public static String getSummary(String[] summary_regex,
                                 List<String> stdout_lines)
  {
   Pattern summary = Pattern.compile(".*(r|R)eceived.*");
   for (String s:stdout_lines)
    if (summary.matcher(s).matches())
     return s.replaceAll(summary_regex[0],summary_regex[1]);

   return "invalid syntax"; // if something bad happens?
  }

 /**
    Pings an external IP address. A string containing the summary and times
    gathered is constructed, and accessible through
    ICMP_Ping.getLastPings() after having called ping(). If an error
    occured, getLastPings() is undefined (may contain previous call's
    values).

    @see   ICMP_Pinger#getLastPings()
    @see   ICMP_Pinger#clearPings()
    @param addr The address to ping

    @return The external command return code
 */
 public int ping(InetAddress addr)
 {
  String[] specific_command;
  String[] summary_regex;

  // does the OS-specific setup
  switch (info.getOS())
   {
    default:
    case Linux:
         specific_command=os_specific_command[0];
         summary_regex=os_specific_summary_regex[0];
         break;
    case BSD:
    case OSX:
         specific_command=os_specific_command[1];
         summary_regex=os_specific_summary_regex[0]; // FIXME: cross-check
         break;
    case WinXP:
    case Win7:
    case WinOther:
         specific_command=os_specific_command[2];
         summary_regex=os_specific_summary_regex[1];
         break;
   }

  ArrayList<String> command=new ArrayList<String>();
  for (String s : specific_command)
   command.add(s); // copy the params
  command.add(Integer.toString(info.getNumberOfPings()));
  command.add(addr.toString().split("/")[1]); // append addr (as string) to ping

  LinkedList<String> stdout_lines=new LinkedList<String>();
  LinkedList<String> stderr_lines=new LinkedList<String>();
  int retval=Launcher.launch(command,
                             stdout_lines,
                             stderr_lines,
                             20); // FIXME: ? check if always ok for ping

  icmp_times="ICMP "+addr.toString().split("/")[1]+" "; // the protocol+target
  if (retval==0) // Success!
   {
    // scan for summary
    //
    icmp_times+=getSummary(summary_regex,stdout_lines);

    // scan output for times
    //
    Pattern times = Pattern.compile(".*\\stime(=|<)[0-9]+.*"); // osx/bsd/nunux/windows?
    for (String s:stdout_lines)
     if (times.matcher(s).matches())
      // regex to match times, probably good
      // for all variants.
      //
      // .* matches stuff before
      // time= or time< (if time<, it goes in the number) (group 1 and 2)
      // (<?[0-9]+(\\.[0-9]+)?) matches an int or float  (group 3 and 4)
      // (\\ ?) matches an optional whitespace (group 5)
      // (\\S+) matches the unit (ms, s, etc) (group 6)
      // .* matches the trailing crap, if any (group 7)
      //
      icmp_times+=" "+s.replaceAll("(.*time(=?))(<?[0-9]+(\\.[0-9]+)?)(\\ ?)(\\S+).*","$3$6");
   }
  else
   // deal with errors
   if (retval==1)
    // find summary anyway
    icmp_times+=getSummary(summary_regex,stdout_lines);
   else
    icmp_times+="failed "+String.valueOf(retval);

  return retval; // icmp_times may or may not be assigned!
 }

 /**
    Creates an ICMP_Pinger (linked to a Client_Info configuration)
    @param this_info A reference to a Client_Info
    @see Client_Info
 */
 public ICMP_Pinger(Client_Info this_info)
  {
   icmp_times="";
   info=this_info;
  }
}
