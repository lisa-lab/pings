import javax.swing.JApplet;

/**
 * This class holds the web applet. It creates the threads for the clients and
 * the GUI. It also provide methods for nice stop, start and destroy
 * <p>
 * @author //FIXME
*/
public class PingsApplet extends JApplet {
	
	private static final long serialVersionUID = 1L;
	
	private final String SERVER_HOSTNAME = "ec2-184-72-202-57.compute-1.amazonaws.com";
	private final int SERVER_PORT = 6543;
	private final String initial_nickname = "no name";
	public final int nb_client_threads = 5;
	public Thread[] pings_clients_threads;
	public PingsClient[] pings_clients;
	
	private boolean simulation;
	
	/**
	* The initialization of the applet, it creates the PingsClient and the GUI
	* <p>
	* @see PingsClient
	* @see  #initGUI()s
	*/
	public void init() {
		
		pings_clients_threads = new Thread[nb_client_threads];
		pings_clients = new PingsClient[nb_client_threads];
		
		try {
			simulation = Boolean.parseBoolean(getParameter("simulation"));
		}
		catch (Exception _) {
			simulation = false;
		}
		
		for (int i = 0; i < nb_client_threads; i++) {
			if (simulation) {
				pings_clients[i] = (PingsClient) new PingsClientSimulation();
			}
			else {
				pings_clients[i] = new PingsClient(SERVER_HOSTNAME, SERVER_PORT);
			}
			pings_clients[i].setNickname(initial_nickname);
			pings_clients_threads[i] = new Thread(pings_clients[i]);
		}
		
		new PingsGUI(this);
		
		for (int i = 0; i < nb_client_threads; i++) {
			pings_clients_threads[i].start();
			}
		
	}
	
	public void start () {
		for (int i = 0; i < nb_client_threads; i++) {
			pings_clients[i].resume();
			}
		this.repaint();
	}
	
	public void stop () {
		for (int i = 0; i < nb_client_threads; i++) {
			pings_clients[i].pause();
			}
	}
	
	public void destroy() {
		for (int i = 0; i < nb_client_threads; i++) {
			pings_clients_threads[i].interrupt();
			}
		}
}
