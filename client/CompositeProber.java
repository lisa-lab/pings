import java.net.InetAddress;

/**
 * Prober class that probes an IP address with multiple methods. Right now
 * it includes all the Prober implementations: IcmpPinger, TcpPinger and
 * TraceRouter. It returns all the result strings together,
 * separated by a ';'.
 *
 * @author Christian Hudon <chrish@pianocktail.org>
 */
class CompositeProber implements Prober {
    private Prober m_probers[];

    public CompositeProber(Prober[] probers){
	m_probers = probers;
    }

    public CompositeProber(ClientInfo client_info) {
        m_probers = new Prober[3];
        m_probers[0] = new IcmpPinger(client_info);
        m_probers[1] = new TcpPinger(client_info);
        m_probers[2] = new TraceRouter(client_info);
    }

    public String getLastProbe() {
        boolean first_one_done = false;
        StringBuilder sb = new StringBuilder();

        for (Prober p : m_probers) {
	    if(p == null)
		continue;
            if (first_one_done) {
                sb.append("; ");
            }
            else {
                first_one_done = true;
            }

            sb.append(p.getLastProbe());
        }

        return sb.toString();
    }

    public void clearProbe() {
        for (Prober p : m_probers) {
	    if(p != null)
		p.clearProbe();
        }
    }

    public int probe(InetAddress addr) throws InterruptedException {
        boolean succeeded = true;

        for (Prober p : m_probers) {
	    if(p == null)
		continue;
            succeeded &= (p.probe(addr) == 0);
        }

        return (succeeded) ? 0 : 1;
    }
}
