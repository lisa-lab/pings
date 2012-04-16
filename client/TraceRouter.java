import java.util.*;
import java.util.regex.*;
import java.net.*;

/**
   This class does the traceroute to a remote destination

   @author   Steven Pigeon <pigeon@iro.umontreal.ca>
   @version  0.5
   @since    2012-03
*/
public class TraceRouter
{
 /** Holds the last collected trace */
 private String trace_times;

 /** A reference to ClientInfo */
 private ClientInfo info;

 /** Describes the OS-Specific traceroute commands */
 private static final String[][] os_specific_command=
 {
  // number of query per hops must remain the
  // LAST parameter because it is appended using
  // ClientInfo.getNumberOfTraces()
  //
  // (except for windows XP, which doesn't take
  // this argument)
  //
  {"traceroute","-n","-w","1","-q"}, // Linux no lookup, send 3 probes, wait max. 0.5s for reply
  {"traceroute","-n","-w","1","-q"}, // BSD, OSX ?
  {"tracert","-d","-w","1000"} // Windows xp // careful: no -q parameter!
 };

 /** Regexes to reject completely failed hops */
 private static final String[] os_specific_reject_regex=
 {
  // checks if all pings were lost
  // ex:  5 * * *
  "\\s*[0-9]+(\\s+\\*){3}.*"
 };

 /** Regexes to canonalize OS-specific output to a common format */
 private static final String[][] os_specific_translator=
  {
   // windows outputs trace in a slightly
   // different order than all the others:
   // n time time time ip
   // rather than
   // n ip time time time (for linux, bsd, etc.)

   {"",""}, // everything except windows, of course.
   {"\\s*([0-9]+)\\s(<?(\\*|[0-9]+)m?s?)\\s(<?(\\*|[0-9]+)m?s?)\\s(<?(\\*|[0-9]+)m?s?)\\s([0-9\\.]+).*","$1 $8 $2 $4 $6"}  // windows xp
  };

 /**
     Returns the last collected routes as a String.

     <p>The output is structured as follows

     <p>protocol addr [[,]hop_number time time time]

     <p>for example:

     <p><tt>TROUTE 132.204.24.179 1 10.39.128.2 7.078ms 8.637ms 8.628ms,5 132.204.24.179 8.499ms * *</tt>

     @return The last collected routes
  */
 public String getLastTrace() { return trace_times; }

 /**
    Clears the last collected times
 */
 public void clearTrace() { trace_times=""; }


 /**
    Invokes an external traceroute command and collects the route data

    @param addr Address to route to

    @return The external command exit code
 */
 public int trace(InetAddress addr)
 {
  String[] specific_command;
  String reject_regex;
  String[] translator;

  // Do OS-specific stuff
  switch (info.getOS())
   {
   default:
   case BSD:
   case Linux:
        specific_command=os_specific_command[0];
        reject_regex=os_specific_reject_regex[0];
        translator=os_specific_translator[0];
        break;
   case OSX:
        specific_command=os_specific_command[1];
        reject_regex=os_specific_reject_regex[0]; // FIXME: cross-check
        translator=os_specific_translator[0]; // FIXME: cross-check
        break;
   case WinXP:
   case Win7:
   case WinOther:
        specific_command=os_specific_command[2];
        reject_regex=os_specific_reject_regex[0];
        translator=os_specific_translator[1];
        break;
   }

  ArrayList<String> command=new ArrayList<String>();
  for (String s : specific_command)
   command.add(s); // copy the params

  if (info.getOS()!=ClientInfo.OS_Type.WinXP)
   // except for windows xp?
   // FIXME: other Windows version?
   command.add(Integer.toString(info.getNumberOfTraces()));

  command.add(addr.toString().split("/")[1]); // append addr (as string) to traceroute

  //for (String s: command)
  // System.out.println(s);

  LinkedList<String> stdout_lines=new LinkedList<String>();
  LinkedList<String> stderr_lines=new LinkedList<String>();
  int retval=Launcher.launch(command,
                             stdout_lines,
                             stderr_lines,
                             40); // FIXME: ? check if always ok for traceroute

  trace_times="TROUTE "+addr.toString().split("/")[1]+" "; // the protocol (TraceROUTE)
  if (retval==0) // Success!
   {
    // scan output for times
    //
    Pattern keep_filter = Pattern.compile("\\s*[0-9]+\\s.*"); // a line begining with a number
    Pattern reject_filter = Pattern.compile(reject_regex); // a line filled with stars
    boolean first=true;
    for (String s:stdout_lines)
     if (keep_filter.matcher(s).matches() && // is a line beginning with a number ...
         !reject_filter.matcher(s).matches()) // ...but not full of stars
      {
       String t=s.replaceAll("^\\s*",""). // leading spaces
                  replaceAll("\\s$",""). // trailing spaces
                  replaceAll("\\s+"," "). // strings of spaces
                  replaceAll("\\s+ms","ms"); // space between number and units
       trace_times+=(first?"":",")+t.replaceAll(translator[0],translator[1]);
       first=false;
      }

    //System.out.println(trace_times);
    if (first)
     // no assignation?
     trace_times+="TIMEOUT";
   }
  else
   // deal with error codes
   trace_times+="failed "+String.valueOf(retval);

  return retval; // icmp_times may or may not be assigned!
 }

 /**
    Creates a TraceRouter (linked to a ClientInfo configuration)
    @param this_info A reference to a ClientInfo
    @see ClientInfo
 */
 public TraceRouter(ClientInfo this_info)
 {
  trace_times="";
  info=this_info;
 }
}
