import java.io.*;
import java.net.*;


// this class is disposable code
// to examplify how to use the various
// pingers and the expected format
//
public class external_command
{
 public static void main(String args[])
 {
  Client_Info this_client=new Client_Info();

  InetAddress my_local_addr=this_client.getAddress();
  InetAddress my_global_addr=null;

  try {  my_global_addr=this_client.getExternalAddress(); }
  catch (Exception e) { e.printStackTrace(); }
  finally{ /* we'll live through it */ }

  InetAddress target=null;
  try
   {
    // lookup the target's addr
    target=InetAddress.getByName(args[0]);
   }
  catch (Exception error) { }


  if (target!=null)
   {
    TCP_Pinger this_tcp_pinger=new TCP_Pinger(this_client);
    ICMP_Pinger this_pinger=new ICMP_Pinger(this_client);
    TraceRouter this_router=new TraceRouter(this_client);

    String text_local_addr = my_local_addr.toString().split("/")[1];

    String text_global_addr;
    if (my_global_addr!=null)
     text_global_addr = my_global_addr.toString().split("/")[1];
    else
     text_global_addr = "<unknown>";

    int z=this_pinger.ping(target);
    System.out.println( text_local_addr+" "+
                        text_global_addr+" "+
                        this_pinger.getLastPings());
    
    int zz=this_router.trace(target);
    System.out.println( text_local_addr+" "+
                        text_global_addr+" "+
                        this_router.getLastTrace());

    int zzz=this_tcp_pinger.ping(target,50);
    System.out.println( text_local_addr+" "+
                        text_global_addr+" "+
                        this_tcp_pinger.getLastPings());

   }
 }
}
