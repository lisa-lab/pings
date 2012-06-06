import javax.swing.JApplet;

/**
 * This class holds the web applet. It creates the threads for the clients and
 * the GUI. It also provide methods for nice stop, start and destroy
 * <p>
 * @author //FIXME
*/
public class PingsApplet extends JApplet {
	
	private static final long serialVersionUID = 2L;
	
	private final String SERVER_HOSTNAME = "pings-prod-lb-838066006.us-east-1.elb.amazonaws.com";
	private final int SERVER_PORT = 80;
	private final String initial_nickname = "No Name";
	public final int nb_clients = 1;
	public PingsClient[] pings_clients;
	
	private PingsGUI ping_gui;
	
	private boolean simulation;

	
	
	/**
	* The initialization of the applet, it creates the PingsClient and the GUI
	* <p>
	* @see PingsClient
	* @see  #initGUI()s
	*/
	public void init() {
		pings_clients = new PingsClient[nb_clients];
		
		try {
			simulation = Boolean.parseBoolean(getParameter("simulation"));
		}
		catch (Exception _) {
			simulation = false;
		}
		
		for (int i = 0; i < nb_clients; i++) {
			if (simulation) {
				pings_clients[i] = (PingsClient) new PingsClientSimulation();
			}
			else {
				pings_clients[i] = new PingsClient(SERVER_HOSTNAME, SERVER_PORT);
			}
			pings_clients[i].setNickname(initial_nickname);
		}
		
		ping_gui = new PingsGUI(this);
		
		for (int i = 0; i < nb_clients; i++) {
			pings_clients[i].run();
			}
		
	}
	
	public void start () {
		for (int i = 0; i < nb_clients; i++) {
			pings_clients[i].resume();
			}
		this.repaint();
	}
	
	
	public void stop () {
		for (int i = 0; i < nb_clients; i++) {
			pings_clients[i].pause();
			}
	}
	
	public void destroy() {
		for (int i = 0; i < nb_clients; i++) {
			pings_clients[i].destroy();
			}
		}

	public void errorConnectingServer() {
		ping_gui.destroy();
		
	}
	
	public void restartApplet() {
		
	}
}
