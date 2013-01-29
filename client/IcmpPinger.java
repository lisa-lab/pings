import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.regex.Pattern;
import java.net.InetAddress;

/**
   This class launches an external ping command and returns the collected
   times.

   @author   Steven Pigeon <pigeon@iro.umontreal.ca>
*/
public class IcmpPinger implements Prober {
    /** Holds the last collected times */
    private String m_icmp_times;

    /** A reference to ClientInfo */
    private ClientInfo m_info;

    /** Describes OS-specific commands for external ping command */
    private static final String[][] OS_SPECIFIC_COMMAND =  {
        // The number of pings parameter must be LAST because it is
        // appended at run-time using ClientInfo.getNumberOfPings()
        //
        // Linux
        {"ping", "-w", "15", "-W", "2", "-c"},
        // BSD, OSX ?
        {"ping", "-c"},
        // Windows XP        
        {"ping", "-w", "2000", "-n"}
    };

    /** OS-specific regexes to get the summary line */
    private static final String[][] OS_SPECIFIC_SUMMARY_REGEX = {
        // On *nixes: "7 packets transmitted, 6 received, 14% packet loss, time 6014ms"
        {"(([0-9]+)\\s[\\w|\\s]+),\\s(([0-9]+)\\s[\\w|\\s]+),.*(time)\\s(.*)", "$2 $4 $6"},

        // On OSX: "7 packets transmitted, 6 received, 14% packet loss"
        {"(([0-9]+)\\s[\\w|\\s]+),\\s(([0-9]+)\\s[\\w|\\s]+).*", "$2 $4 ?ms"},

        // On English Windows XP: " Packets: Sent = 10, Received = 9, Lost = 1 (10% loss)"
	// On French Windows 7: "     Paquets : envoyés = 3, reçus = 0, perdus = 3 (perte 100%),"
	// To be resistent to language, we make a very generic pattern. Hopefully, they don't swap numbers!
	// [,，﹐] is the 2 representation of the ',' symbol in Unicode, the second one is the fullwidth vresion
        {"[^=]+=\\s*(\\d+)[,，﹐][^=]+=\\s*(\\d+)[,，﹐][^=]*=.*", "$1 $2 ?ms"},
    };

    public String getLastProbe() { return m_icmp_times; }

    public void clearProbe() { m_icmp_times = ""; }

    /**
       Returns the ping summary (how many sent, how many received, and if
       possible the time in milliseconds elapsed)

       @return The ping summary
    */
    public static String getSummary(String[] summary_regex,
                                    List<String> stdout_lines) {
	Pattern summary = Pattern.compile(summary_regex[0]);
        for (String s : stdout_lines)
            if (summary.matcher(s).matches())
                return s.replaceAll(summary_regex[0], summary_regex[1]);

	if(stdout_lines.size() == 0){
	    return "ping have no output";
	}

	String str = stdout_lines.get(0);
        for (String s : stdout_lines){
	    if (s.length() > 0 && s.charAt(0) == ' ' && s.contains("=")){
		str = s;
		break;
	    }
	}
        return "ping summary not found. The closest output line is : '" + str + "'";
    }
    /**
       @return The ping times
    */
    public static String getTimes(List<String> stdout_lines) {
	String ret = "";
	Pattern times = Pattern.compile(".*\\s(time|temps|tiempo|tempo|durata|Zeit|时间)(=|<)[0-9]+.*"); // osx/bsd/nunux/windows?
	for (String s : stdout_lines){
	    if (times.matcher(s).matches()){
		// Regex to match times, probably good for all
		// variants.
		//
		// .* matches stuff before
		// time= or time< (if time<, it goes in the number) (group 1 and 2)
		// (<?[0-9]+(\\.[0-9]+)?) matches an int or float  (group 3 and 4)
		// (\\ ?) matches an optional whitespace (group 5)
		// (\\S+) matches the unit (ms, s, etc) (group 6)
		// .* matches the trailing crap, if any (group 7)
		//
		ret += " " + s.replaceAll("(.*(tiempo|tempo|durata|time|temps|Zeit|时间)=?)(<?[0-9]+(\\.[0-9]+)?)(\\ ?)(\\S+).*", "$3$6");
	    }else if(s.contains("TTL")){// All known(French, English, Espagnol
		String[] part = s.split(" ");
		String out = null;
		for (String p : part){
		    if(p.length() > 2 && p.endsWith("ms")){
			out = p;
			break;
		    }
		}
		if(out!=null)
		    ret +=  " " + out;
		else
		    ret += " '" + s + "'";
	    }
	}
	if(ret.length() == 0){
	    System.out.println("Pings outputs not parsed: ");
	    for (String s : stdout_lines){
		System.out.println(s);
	    }
	}
	return ret;

    }
    public int probe(InetAddress addr) throws InterruptedException {
        String[] specific_command;
        String[] summary_regex;

        // does the OS-specific setup
        switch (m_info.getOS()) {
        case BSD:
        case OSX:
            specific_command = OS_SPECIFIC_COMMAND[1];
            summary_regex = OS_SPECIFIC_SUMMARY_REGEX[1];
            break;

        case WinXP:
        case Win7:
        case WinOther:
            specific_command = OS_SPECIFIC_COMMAND[2];
            summary_regex = OS_SPECIFIC_SUMMARY_REGEX[2];
            break;

        case Linux:
        default:
            specific_command = OS_SPECIFIC_COMMAND[0];
            summary_regex = OS_SPECIFIC_SUMMARY_REGEX[0];
            break;
        }

        ArrayList<String> command = new ArrayList<String>();
        for (String s : specific_command)
            command.add(s); // copy the params
        command.add(Integer.toString(m_info.getNumberOfPings()));
        command.add(addr.toString().split("/")[1]); // append addr (as string) to ping

        LinkedList<String> stdout_lines = new LinkedList<String>();
        LinkedList<String> stderr_lines = new LinkedList<String>();
  
        // FIXME: Check if 20 is always ok for ping.
        int retval = Launcher.launch(command, stdout_lines, stderr_lines, 20);

        m_icmp_times = "ICMP " + addr.toString().split("/")[1] + " "; // the protocol+target
        if (retval==0) {
            // Success! Scan for summary.
            //
            m_icmp_times += getSummary(summary_regex,stdout_lines);

            // Scan output for times
            //
	    m_icmp_times += getTimes(stdout_lines);
        }
        else
            // Deal with errors
            if (retval == 1)
                // find summary anyway
                m_icmp_times += getSummary(summary_regex, stdout_lines);
            else
                m_icmp_times += "failed " + String.valueOf(retval);

        return retval; // m_icmp_times may or may not be assigned!
    }

    /**
       Creates an IcmpPinger (linked to a ClientInfo configuration)
       @param this_info A reference to a ClientInfo
       @see ClientInfo
    */
    public IcmpPinger(ClientInfo this_info) {
        m_icmp_times = "";
        m_info = this_info;
    }

    public static void main(String args[]) throws InterruptedException {
	System.out.println("Test parsing of ICMP summary on windows");
	// Tests getSummary on Windows regex as there is difference in output depending of the OS language.
	String[] to_test= {" Packets: Sent = 10, Received = 9, Lost = 1 (10% loss)", //
			   "    Packets: Sent = 5, Received = 5, Lost = 0 (0% loss),",//Windows XP Pro SP3 (English)
			   "Paquets : envoyés = 5, reçus = 5, perdus = 0 (perte 0%),", // Windows Vista Sp2
			   "     Paquets : envoyes = 10, recus = 9, perdus = 1 (perte 10%),",
			   "     Paquets : envoyés = 10, recus = 9, perdus = 1 (perte 10%),",
			   "     Paquets : envoyes = 10, reçus = 9, perdus = 1 (perte 10%),",
			   "     Paquets : envoyés = 10, reçus = 9, perdus = 1 (perte 10%),",
			   "    数据包: 已发送 = 5，已接收 = 5，丢失 = 0 (0% 丢失)，",
	};
        ArrayList<String> out = new ArrayList<String>();
	for (int i = 0; i < to_test.length; i++){
	    out.add(to_test[i]);
	    System.out.println(IcmpPinger.getSummary(OS_SPECIFIC_SUMMARY_REGEX[2], out));
	    out.remove(0);
	}

	System.out.println("\nTest parsing of ICMP summary on linux");
	// Tests getSummary regex as there is difference in output depending of the OS language.
	String[] to_test3 = {
	    "3 packets transmitted, 0 received, 100% packet loss, time 1999ms",//FC14
	};
	for (int i = 0; i < to_test3.length; i++){
	    out.add(to_test3[i]);
	    System.out.println(IcmpPinger.getSummary(OS_SPECIFIC_SUMMARY_REGEX[0], out));
	    out.remove(0);
	}
	System.out.println("\nTest parsing of ICMP summary on OSX");
	// Tests getSummary regex as there is difference in output depending of the OS language.
	String[] to_test4 = {
	    "35 packets transmitted, 0 packets received, 100.0% packet loss",//OSX lab computer
	    "5 packets transmitted, 5 packets received, 0.0% packet loss" //OSX 10.7, OSX 10.7.5, 10.8.2, 10.6.8
	};
	for (int i = 0; i < to_test4.length; i++){
	    out.add(to_test4[i]);
	    System.out.println(IcmpPinger.getSummary(OS_SPECIFIC_SUMMARY_REGEX[1], out));
	    out.remove(0);
	}

	// Test getTimes
	System.out.println("\nTest parsing of ICMP individual outputs");
	String[] to_test2 = {
	    "64 bytes from nuq04s08-in-f31.1e100.net (74.125.224.127): icmp_seq=1 ttl=53 time=1.97 ms", //Some linux
	    "64 bytes from 127.0.0.1: icmp_seq=1 ttl=64 time=0.040 ms", //linux
	    "64 bytes from 173.194.75.94: icmp_seq=2 ttl=45 time=454.709 ms", //OSX 10.7, 10.6.8, 10.7.5, 10.8.2
	    "64 bytes from vb-in-f94.1e100.net (173.194.73.94): icmp_req=1 ttl=48 time=46.0 ms", //Ubuntu 11.10
	    "Réponse de 173.194.75.94 : octets=32 temps=40 ms TTL=48", //Windows7 French, Windows Vista Sp2 French
	    "Reply from 74.125.224.82: bytes=1500 time=40ms TTL=52", // Windows7 English, Win XP Pro SP3 (English)

	    // The example bellow come from the translation provided by Microsoft of that page.
	    // from http://support.microsoft.com/kb/323388
	    "Respuesta desde 192.168.1.104: bytes=32 tiempo=40ms TTL=61", //Windows es
	    "Reply from 192.168.1.104: bytes=32 time=40ms TTL=61", //be Nederlands
	    "Resposta de 192.168.1.104: bytes=32 tempo=40 ms TTL=61",	    //pt Portugues
	    "Odpověď od 192.168.1.104: bajty = 32 čas = 40ms TTL = 61 Cestina", //cs
	    "الرد على من 192.168.1.104: بايت = الوقت 32 = 40ms TTL = 61", // ar
	    "Antwort von 192.168.1.104: Bytes=32 Zeit=40ms TTL=61", // de
	    "Balasan dari 192.168.1.104: byte = 32 waktu = 40ms TTL = 61", //id
	    "Risposta da 192.168.1.104: byte=32 durata=40 ms TTL=61", //it
	    "192.168.1.104 Cevabı: bayt = 32 süre = 40ms TTL = 61 =", // tr
	    "Thư trả lời từ 192.168.1.104: bytes = 32 time = 40ms TTL = 61", // vi-vn
	    "Απάντηση από 192.168.1.104: bytes = 32 time = 40ms TTL = 61", //el
	    "ตอบกลับจาก 192.168.1.104: ไบต์ =เวลา 32 = 40ms TTL = 61", //th

	    "Respuesta desde 192.168.1.104: bytes=32 tiemp=40 ms TTL=61", //modif Windows es
	    //Output from a lab member
	    "来自 74.125.135.94 的回复: 字节=32 时间=40ms TTL=40",

	};
	for (int i = 0; i < to_test2.length; i++){
	    out.add(to_test2[i]);
	    System.out.println(IcmpPinger.getTimes(out));
	    out.remove(0);
	}

	return;
    }
}
