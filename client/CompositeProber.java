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
    private String prob_result = "first";

    public CompositeProber(Prober[] probers, String prob_result){
	m_probers = probers;

	if(prob_result.equals("first") || prob_result.equals("and"))
	    this.prob_result = prob_result;
    }

    public CompositeProber(ClientInfo client_info, String prob_result) {
        m_probers = new Prober[3];
        m_probers[0] = new IcmpPinger(client_info);
        m_probers[1] = new TcpPinger(client_info);
        m_probers[2] = new TraceRouter(client_info);

	if(prob_result.equals("first") || prob_result.equals("and"))
	    this.prob_result = prob_result;
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
	if (prob_result == "first" && m_probers[0] == null)
	    succeeded = false;

	for (int i=0; i<m_probers.length; i++) {
	    Prober p = m_probers[i];
	    if(p == null)
		continue;
	    boolean succ = (p.probe(addr) == 0);
	    if (prob_result == "first" && i == 0)
		succeeded = succ;
	    else if(prob_result == "and")
		succeeded &= succ;
        }
        return (succeeded) ? 0 : 1;

    }
}
