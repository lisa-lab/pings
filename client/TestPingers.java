import java.io.*;
import java.net.*;


/** This class is disposable code to examplify how to use the various
    pingers and the expected format
*/
public class TestPingers {
    public static void main(String args[]) throws InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: java TestPingers hostname");
            System.exit(2);
        }
        
        // FIXME: get from applet's cookie
        ClientInfo this_client = new ClientInfo(ClientInfo.m_cookie_name+"={user=bobafett;uuid=fatalapouet};path=/;expires=eventually");

        if (this_client.getNickname() == null)
            this_client.setNickname("bobafett");
 
        InetAddress my_local_addr = this_client.getAddress();
        InetAddress my_global_addr = null;

        try {
            my_global_addr = this_client.getExternalAddress();
        }
        catch (RuntimeException e) {
            System.err.println("Warning: could not get external address.");
        }

        InetAddress target = null;
        try {
            // Lookup the target's addr
            target = InetAddress.getByName(args[0]);
        }
        catch (UnknownHostException e) {
            System.err.println("Error: could not get address for host '" + args[0] + "'.");
            System.exit(1);
        }

        if (target != null) {
            System.out.println("\nStarting pings...");
            
            TcpPinger this_tcp_pinger = new TcpPinger(this_client);
            IcmpPinger this_pinger = new IcmpPinger(this_client);
            TraceRouter this_router = new TraceRouter(this_client);

            String text_local_addr = my_local_addr.toString().split("/")[1];

            String text_global_addr;
            if (my_global_addr != null)
                text_global_addr = my_global_addr.toString().split("/")[1];
            else
                text_global_addr = "<unknown>";

            this_pinger.ping(target);
            System.out.println(text_local_addr + " " +
                               text_global_addr + " " +
                               this_pinger.getLastPings());
    
            this_router.trace(target);
            System.out.println(text_local_addr + " " +
                               text_global_addr + " " +
                               this_router.getLastTrace());

            this_tcp_pinger.ping(target,50);
            System.out.println(text_local_addr + " " +
                               text_global_addr + " " +
                               this_tcp_pinger.getLastPings());

        }

        //FIXME: save to applet cookie
        System.out.println(this_client.getPreferences());
    }
}
