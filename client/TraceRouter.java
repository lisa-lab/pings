import java.util.*;
import java.util.regex.*;
import java.net.*;

/**
   This class does the traceroute to a remote destination

   @author   Steven Pigeon <pigeon@iro.umontreal.ca>
*/
public class TraceRouter implements Prober {
    /** Holds the last collected trace */
    private String m_trace_times;

    /** A reference to ClientInfo */
    private ClientInfo m_info;

    /** Describes the OS-Specific traceroute commands */
    private static final String[][] OS_SPECIFIC_COMMAND = {
        // Number of query per hops must remain the LAST parameter because
        // it is appended using ClientInfo.getNumberOfTraces() (except for
        // windows XP, which doesn't take this argument).
        //
        // Linux: no lookup, send 3 probes, wait max. 0.5s for reply
        {"traceroute", "-n", "-w", "1", "-q"},
        // BSD, OSX ?
        {"traceroute", "-n", "-w", "1", "-q"},
        // Windows XP (careful: no -q parameter!)
        {"tracert", "-d", "-w", "1000"}
    };

    /** Regexes to reject completely failed hops */
    private static final String[] OS_SPECIFIC_REJECT_REGEX = {
        // Checks if all pings were lost
        // ex:  5 * * *
        "\\s*[0-9]+(\\s+\\*){3}.*"
    };

    /** Regexes to canonalize OS-specific output to a common format */
    private static final String[][] OS_SPECIFIC_TRANSLATOR = {
        // Windows outputs trace in a slightly different order than all the
        // others:
        //     n time time time ip
        // rather than:
        //     n ip time time time (for linux, bsd, etc.)
        
        // Everything except Windows, of course.
        {"", ""},
        // Windows XP
        {"\\s*([0-9]+)\\s(<?(\\*|[0-9]+)m?s?)\\s(<?(\\*|[0-9]+)m?s?)\\s(<?(\\*|[0-9]+)m?s?)\\s([0-9\\.]+).*", "$1 $8 $2 $4 $6"}
    };

    /**
       Returns the last collected routes as a String.

       <p>The output is structured as follows

       <p>protocol addr [[,]hop_number time time time]

       <p>for example:

       <p><tt>TROUTE 132.204.24.179 1 10.39.128.2 7.078ms 8.637ms 8.628ms,5 132.204.24.179 8.499ms * *</tt>

       @return The last collected routes
    */
    public String getLastProbe() { return m_trace_times; }

    /**
       Clears the last collected times
    */
    public void clearProbe() { m_trace_times = ""; }

    /**
       Returns the traceroute outputs needed.
    */
    public static String getTimes(String reject_regex, String[] translator, int retval,
				  List<String> stdout_lines) {
	String ret = "";
        if (retval == 0) {
            // Success!
            // scan output for times
            //
            Pattern keep_filter = Pattern.compile("\\s*[0-9]+\\s.*"); // a line begining with a number
            Pattern reject_filter = Pattern.compile(reject_regex); // a line filled with stars
            boolean first = true;

            for (String s : stdout_lines)
                if (keep_filter.matcher(s).matches() && // is a line beginning with a number ...
                    !reject_filter.matcher(s).matches()) { // ...but not full of stars
                    String t = s.replaceAll("^\\s*",""). // leading spaces
                        replaceAll("\\s$", ""). // trailing spaces
                        replaceAll("\\s+", " "). // strings of spaces
                        replaceAll("\\s+ms", "ms"); // space between number and units
                    ret += (first ? "" : ",") + t.replaceAll(translator[0], translator[1]);
                    first = false;
                }

            if (first)
                // No assignation?
                ret += "TIMEOUT";
        }
        else
            // Deal with error codes
            ret += "failed " + String.valueOf(retval);
	return ret;
    }

    /**
       Invokes an external traceroute command and collects the route data

       @param addr Address to route to

       @return The external command exit code
    */
    public int probe(InetAddress addr) throws InterruptedException {
        String[] specific_command;
        String reject_regex;
        String[] translator;

        // Do OS-specific stuff
        switch (m_info.getOS()) {
        case OSX:
            specific_command = OS_SPECIFIC_COMMAND[1];
            reject_regex = OS_SPECIFIC_REJECT_REGEX[0]; // FIXME: cross-check
            translator = OS_SPECIFIC_TRANSLATOR[0]; // FIXME: cross-check
            break;

        case WinXP:
        case Win7:
        case WinOther:
            specific_command = OS_SPECIFIC_COMMAND[2];
            reject_regex = OS_SPECIFIC_REJECT_REGEX[0];
            translator = OS_SPECIFIC_TRANSLATOR[1];
            break;

        default:
        case BSD:
        case Linux:
            specific_command = OS_SPECIFIC_COMMAND[0];
            reject_regex = OS_SPECIFIC_REJECT_REGEX[0];
            translator = OS_SPECIFIC_TRANSLATOR[0];
            break;
        }

        ArrayList<String> command = new ArrayList<String>();
        for (String s : specific_command)
            command.add(s); // copy the params

        if ((m_info.getOS() != ClientInfo.OSType.WinXP) &&
        		(m_info.getOS() != ClientInfo.OSType.Win7)  &&
        		(m_info.getOS() != ClientInfo.OSType.WinOther))
            // FIXME: other Windows version?
            command.add(Integer.toString(m_info.getNumberOfTraces()));

        command.add(addr.toString().split("/")[1]); // append addr (as string) to traceroute

        LinkedList<String> stdout_lines = new LinkedList<String>();
        LinkedList<String> stderr_lines = new LinkedList<String>();
        // FIXME: check if 40 is always ok for traceroute
        int retval = Launcher.launch(command, stdout_lines, stderr_lines, 40);

        m_trace_times = "TROUTE " + addr.toString().split("/")[1] + " "; // the protocol (TraceROUTE)
	m_trace_times += getTimes(reject_regex, translator, retval, stdout_lines);
        
        return retval; // icmp_times may or may not be assigned!
    }

    /**
       Creates a TraceRouter (linked to a ClientInfo configuration)
       @param this_info A reference to a ClientInfo
       @see ClientInfo
    */
    public TraceRouter(ClientInfo this_info) {
        m_trace_times = "";
        m_info = this_info;
    }
}
