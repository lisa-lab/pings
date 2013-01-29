import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.net.InetAddress;

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
            Pattern keep_filter2 = Pattern.compile("\\s+[0-9]+.[0-9]+.[0-9]+.[0-9]+\\s+.*"); // a line begining with a ip
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
                } else if(keep_filter2.matcher(s).matches() && // is a line beginning with an ip ...
			  !reject_filter.matcher(s).matches()) { // not full of stars. On OSX, this is the same hop as the previous line.
                    String t = s.replaceAll("^\\s*",""). // leading spaces
                        replaceAll("\\s$", ""). // trailing spaces
                        replaceAll("\\s+", " "). // strings of spaces
                        replaceAll("\\s+ms", "ms"); // space between number and units
                    ret += " " + t.replaceAll(translator[0], translator[1]);
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
        // FIXME: check if 70 is always ok for traceroute
        int retval = Launcher.launch(command, stdout_lines, stderr_lines, 70);

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

    /**
     * This test the parsing of the outputs.
     */
    public static void main(String args[]) throws InterruptedException {
	System.out.println("Test parsing of traceroute for OSX");
	String[][] to_test_OSX= {
	    {
"traceroute to google.ca (173.194.75.94), 64 hops max, 52 byte packets",
" 1  10.0.0.129  7.836 ms  2.898 ms  2.514 ms",
" 2  64.230.200.253  14.604 ms  11.269 ms  11.427 ms",
" 3  64.230.36.240  11.561 ms  11.053 ms  11.403 ms",
" 4  64.230.145.249  23.870 ms",
"    64.230.145.253  23.326 ms  23.787 ms",
" 5  64.230.173.33  33.082 ms  31.668 ms",
"    64.230.173.37  27.717 ms",
" 6  64.230.187.137  23.837 ms",
"    64.230.187.141  23.633 ms",
"    64.230.187.137  24.090 ms",
" 7  64.230.187.42  26.601 ms",
"    64.230.187.38  25.115 ms",
"    64.230.187.89  24.831 ms",
" 8  64.230.187.154  23.556 ms  24.946 ms  23.675 ms",
" 9  72.14.238.232  23.173 ms  23.175 ms",
"    209.85.255.68  23.419 ms",
"10  72.14.236.208  24.373 ms",
"    209.85.252.242  23.646 ms  23.634 ms",
"11  72.14.239.93  30.317 ms  30.364 ms",
"    209.85.249.11  41.954 ms",
"12  209.85.243.114  42.529 ms  41.304 ms  41.344 ms",
"13  216.239.48.59  41.975 ms  40.039 ms",
"    216.239.48.159  40.431 ms",
"14  * * *",
"15  173.194.75.94  42.157 ms  44.020 ms  41.075 ms",

//"1 10.0.0.129 7.836ms 2.898ms 2.514ms,2 64.230.200.253 14.604ms 11.269ms 11.427ms,3 64.230.36.240 11.561ms 11.053ms 11.403ms,4 64.230.145.249 23.870ms,5 64.230.173.33 33.082ms 31.668ms,6 64.230.187.137 23.837ms,7 64.230.187.42 26.601ms,8 64.230.187.154 23.556ms 24.946ms 23.675ms,9 72.14.238.232 23.173ms 23.175ms,10 72.14.236.208 24.373ms,11 72.14.239.93 30.317ms 30.364ms,12 209.85.243.114 42.529ms 41.304ms 41.344ms,13 216.239.48.59 41.975ms 40.039ms,15 173.194.75.94 42.157ms 44.020ms 41.075ms" //The returned strings
"1 10.0.0.129 7.836ms 2.898ms 2.514ms,2 64.230.200.253 14.604ms 11.269ms 11.427ms,3 64.230.36.240 11.561ms 11.053ms 11.403ms,4 64.230.145.249 23.870ms 64.230.145.253 23.326ms 23.787ms,5 64.230.173.33 33.082ms 31.668ms 64.230.173.37 27.717ms,6 64.230.187.137 23.837ms 64.230.187.141 23.633ms 64.230.187.137 24.090ms,7 64.230.187.42 26.601ms 64.230.187.38 25.115ms 64.230.187.89 24.831ms,8 64.230.187.154 23.556ms 24.946ms 23.675ms,9 72.14.238.232 23.173ms 23.175ms 209.85.255.68 23.419ms,10 72.14.236.208 24.373ms 209.85.252.242 23.646ms 23.634ms,11 72.14.239.93 30.317ms 30.364ms 209.85.249.11 41.954ms,12 209.85.243.114 42.529ms 41.304ms 41.344ms,13 216.239.48.59 41.975ms 40.039ms 216.239.48.159 40.431ms,15 173.194.75.94 42.157ms 44.020ms 41.075ms" //The returned strings
	    },  //OSX 10.6.8 10.7 10.7.5 10.8.2, //FIXME this one loose the 2nd ip times!
	    {
"traceroute: Warning: google.ca has multiple addresses; using 74.125.226.88",
"traceroute to google.ca (74.125.226.88), 64 hops max, 52 byte packets",
" 1  * * *",
"31  * * *",
"32  * * *",
"33  * * *",
"34  * * *",
"35  * * *",
"36  * * *",
"37  * * *",
"38  * * *",
"39  * * *",
"40  * * *",
"41  * * *",
"42  * * *",
"43  * * *",
"44  * * *",
"45  * * *",
"46  * * *",
"47  * * *",
"48  * * *",
"49  * * *",
"50  * * *",
"51  * * *",
"52  * * *",
"53  * * *",
"54  * * *",
"55  * * *",
"56  * * *",
"57  * * *",
"58  * * *",
"59  * * *",
"60  * * *",
"61  * * *",
"62  * * *",
"63  * * *",
"64  * * *",
"TIMEOUT"//The returned strings
}//unknow OSX
	};
	for(int i = 0; i < to_test_OSX.length; i++){
	    String[] ins = Arrays.copyOfRange(to_test_OSX[i], 0, to_test_OSX[i].length - 1);
	    String ans = to_test_OSX[i][to_test_OSX[i].length - 1];
	    LinkedList<String> to_test = new LinkedList(Arrays.asList(ins));
	    String out = getTimes(OS_SPECIFIC_REJECT_REGEX[0], OS_SPECIFIC_TRANSLATOR[0], 0, to_test);
	    if(!out.equals(ans)){
		System.out.println("BAD OUTPUT!!!\nExpected output: " + ans);
		System.out.println("         Output: " + out);
	    }else{
		System.out.println("Test passed");
	    }
	}

	System.out.println("\nTest parsing of traceroute for linux");
	String[][] to_test_linux= {
	    {
"traceroute to google.ca (74.125.224.119), 30 hops max, 40 byte packets",
" 1  171.64.68.2  0.578 ms  0.777 ms  0.838 ms",
" 2  171.66.6.201  16.197 ms  16.199 ms  16.180 ms",
" 3  171.64.255.210  0.949 ms  0.582 ms  0.856 ms",
" 4  68.65.168.51  0.470 ms  0.466 ms  0.464 ms",
" 5  137.164.50.157  0.800 ms  0.740 ms  0.749 ms",
" 6  137.164.47.121  1.932 ms  1.783 ms  1.668 ms",
" 7  137.164.46.205  0.963 ms  0.955 ms  0.962 ms",
" 8  137.164.131.61  1.446 ms  1.474 ms  1.463 ms",
" 9  137.164.130.94  1.525 ms  1.492 ms  1.554 ms",
"10  216.239.49.250  1.918 ms  1.921 ms  2.214 ms",
"11  64.233.174.109  2.104 ms  2.385 ms  2.455 ms",
"12  74.125.224.119  2.309 ms  1.803 ms  2.255 ms",
"1 171.64.68.2 0.578ms 0.777ms 0.838ms,2 171.66.6.201 16.197ms 16.199ms 16.180ms,3 171.64.255.210 0.949ms 0.582ms 0.856ms,4 68.65.168.51 0.470ms 0.466ms 0.464ms,5 137.164.50.157 0.800ms 0.740ms 0.749ms,6 137.164.47.121 1.932ms 1.783ms 1.668ms,7 137.164.46.205 0.963ms 0.955ms 0.962ms,8 137.164.131.61 1.446ms 1.474ms 1.463ms,9 137.164.130.94 1.525ms 1.492ms 1.554ms,10 216.239.49.250 1.918ms 1.921ms 2.214ms,11 64.233.174.109 2.104ms 2.385ms 2.455ms,12 74.125.224.119 2.309ms 1.803ms 2.255ms"//The answer
	    },
	    {
"traceroute to google.ca (173.194.75.94), 30 hops max, 60 byte packets",
" 1  192.168.0.1  0.575 ms  0.644 ms  0.739 ms",
" 2  * * *",
" 3  10.170.163.249  15.494 ms  15.770 ms  15.904 ms",
" 4  216.113.123.113  16.096 ms  16.282 ms  16.560 ms",
" 5  216.113.122.58  33.703 ms  33.857 ms  34.041 ms",
" 6  72.14.214.126  30.328 ms  29.976 ms  33.711 ms",
" 7  216.239.46.248  36.119 ms  29.764 ms  29.565 ms",
" 8  72.14.236.98  27.923 ms  27.623 ms 72.14.236.146  30.459 ms",
" 9  209.85.243.114  44.669 ms  40.801 ms  41.186 ms",
"10  216.239.48.159  39.557 ms 216.239.48.157  37.484 ms 216.239.48.183  41.527 ms",
"11  * * *",
"12  173.194.75.94  38.309 ms  36.743 ms  37.386 ms",
"1 192.168.0.1 0.575ms 0.644ms 0.739ms,3 10.170.163.249 15.494ms 15.770ms 15.904ms,4 216.113.123.113 16.096ms 16.282ms 16.560ms,5 216.113.122.58 33.703ms 33.857ms 34.041ms,6 72.14.214.126 30.328ms 29.976ms 33.711ms,7 216.239.46.248 36.119ms 29.764ms 29.565ms,8 72.14.236.98 27.923ms 27.623ms 72.14.236.146 30.459ms,9 209.85.243.114 44.669ms 40.801ms 41.186ms,10 216.239.48.159 39.557ms 216.239.48.157 37.484ms 216.239.48.183 41.527ms,12 173.194.75.94 38.309ms 36.743ms 37.386ms"//the answer
}//ubuntu 12.04
	};
	for(int i = 0; i < to_test_linux.length; i++){
	    String[] ins = Arrays.copyOfRange(to_test_linux[i], 0, to_test_linux[i].length - 1);
	    String ans = to_test_linux[i][to_test_linux[i].length - 1];
	    LinkedList<String> to_test = new LinkedList(Arrays.asList(ins));
	    String out = getTimes(OS_SPECIFIC_REJECT_REGEX[0], OS_SPECIFIC_TRANSLATOR[0], 0, to_test);
	    if(!out.equals(ans)){
		System.out.println("BAD OUTPUT!!!\nExpected output: " + ans);
		System.out.println("         Output: " + out);
	    }else{
		System.out.println("Test passed");
	    }
	}

	System.out.println("\nTest parsing of traceroute for Windows");
	String[][] to_test_win= {
	    {
		//"-->tracert -d -w 1000 3 google.ca",
"",
"Tracing route to google.ca [173.194.73.94]",
"over a maximum of 30 hops:",
"",
"  1    <1 ms    <1 ms    <1 ms  192.168.1.1",
"  2     *        *        *     Request timed out.",
"  3    18 ms    32 ms    31 ms  216.113.122.17",
"  4    36 ms    21 ms    22 ms  216.113.124.86",
"  5    19 ms    21 ms    25 ms  72.14.214.126",
"  6    22 ms    39 ms    23 ms  216.239.46.248",
"  7    20 ms    26 ms    39 ms  72.14.236.148",
"  8    45 ms    45 ms    36 ms  209.85.241.222",
"  9    32 ms    32 ms    31 ms  216.239.48.103",
" 10     *        *        *     Request timed out.",
" 11    36 ms    34 ms    31 ms  173.194.73.94",
"",
"Trace complete.",
"1 192.168.1.1 <1ms <1ms <1ms,3 216.113.122.17 18ms 32ms 31ms,4 216.113.124.86 36ms 21ms 22ms,5 72.14.214.126 19ms 21ms 25ms,6 216.239.46.248 22ms 39ms 23ms,7 72.14.236.148 20ms 26ms 39ms,8 209.85.241.222 45ms 45ms 36ms,9 216.239.48.103 32ms 32ms 31ms,11 173.194.73.94 36ms 34ms 31ms"// The answer
	    }, //Windows XP Pro SP3 (English)
	    {
//C:\Users\sjtu>tracert -d -w 1000 google.ca
"通过最多 30 个跃点跟踪",
"到 google.ca [74.125.135.94] 的路由:",
"",
"  1    42 ms   101 ms    98 ms  192.168.1.254",
"  2    14 ms    17 ms    15 ms  67.69.122.130",
"  3    13 ms    16 ms    17 ms  65.38.93.233",
"  4    17 ms    13 ms    32 ms  77.67.70.125",
"  5    47 ms    50 ms    49 ms  89.149.181.110",
"  6    48 ms    32 ms    59 ms  72.14.212.15",
"  7    64 ms    33 ms    31 ms  209.85.255.68",
"  8    47 ms    90 ms    52 ms  72.14.236.208",
"  9    41 ms    42 ms    58 ms  72.14.239.93",
" 10    49 ms   105 ms    48 ms  72.14.235.12",
" 11    64 ms    56 ms    59 ms  72.14.239.66",
" 12    75 ms    71 ms    69 ms  72.14.237.213",
" 13   103 ms   106 ms   104 ms  64.233.174.140",
" 14   239 ms   223 ms   200 ms  64.233.174.177",
" 15   300 ms   222 ms   195 ms  209.85.255.35",
" 16   200 ms   267 ms   225 ms  64.233.175.0",
" 17   290 ms   289 ms   257 ms  66.249.94.105",
" 18   312 ms   544 ms   604 ms  209.85.242.233",
" 19   267 ms   304 ms   294 ms  209.85.242.125",
" 20     *        *        *     请求超时。",
" 21   293 ms   275 ms   277 ms  74.125.135.94",
"1 192.168.1.254 42ms 101ms 98ms,2 67.69.122.130 14ms 17ms 15ms,3 65.38.93.233 13ms 16ms 17ms,4 77.67.70.125 17ms 13ms 32ms,5 89.149.181.110 47ms 50ms 49ms,6 72.14.212.15 48ms 32ms 59ms,7 209.85.255.68 64ms 33ms 31ms,8 72.14.236.208 47ms 90ms 52ms,9 72.14.239.93 41ms 42ms 58ms,10 72.14.235.12 49ms 105ms 48ms,11 72.14.239.66 64ms 56ms 59ms,12 72.14.237.213 75ms 71ms 69ms,13 64.233.174.140 103ms 106ms 104ms,14 64.233.174.177 239ms 223ms 200ms,15 209.85.255.35 300ms 222ms 195ms,16 64.233.175.0 200ms 267ms 225ms,17 66.249.94.105 290ms 289ms 257ms,18 209.85.242.233 312ms 544ms 604ms,19 209.85.242.125 267ms 304ms 294ms,21 74.125.135.94 293ms 275ms 277ms"//The answer
}// Windows 7 Chinese sp1
	};

	for(int i = 0; i < to_test_win.length; i++){
	    String[] ins = Arrays.copyOfRange(to_test_win[i], 0, to_test_win[i].length - 1);
	    String ans = to_test_win[i][to_test_win[i].length - 1];
	    LinkedList<String> to_test = new LinkedList(Arrays.asList(ins));
	    String out = getTimes(OS_SPECIFIC_REJECT_REGEX[0], OS_SPECIFIC_TRANSLATOR[1], 0, to_test);
	    if(!out.equals(ans)){
		System.out.println("BAD OUTPUT!!!\nExpected output: " + ans);
		System.out.println("         Output: " + out);
	    }else{
		System.out.println("Test passed");
	    }
	}
    }
}
