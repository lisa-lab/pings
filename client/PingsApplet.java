import java.util.Observable;
import java.util.Observer;

import javax.swing.JApplet;
import netscape.javascript.JSObject;

/**
 * This class holds the web applet. It creates the threads for the clients and
 * the GUI. It also provide methods for nice stop, start and destroy
 * <p>
 * @author RaphaelBonaque
*/
public class PingsApplet extends JApplet {
	
	private static final long serialVersionUID = 2L;
	
	//All the variables related to the PingsClient
	private String SERVER_HOSTNAME = "iconnect.iro.umontreal.ca";
	private int SERVER_PORT = 6543;
	static public final String initial_nickname = "Enter Your Nickname Here";
	public final int nb_clients = 1; // should always be 1 now. Otherwise, some part of the logic don't work.
	public PingsClient[] pings_clients;
	
	//Store the GUI, essentially to be able to stop it
	private PingsGUI pings_gui = null;
	
	//Is the applet running in simulation mode
	private boolean simulation;
	
	//Specify if the applet is on the "retry" screen
	private boolean on_retry_screen;
	
	/**
	* The initialization of the applet, it creates the PingsClient and the GUI
	* <p>
	* @see PingsClient
	* @see  #initGUI()s
	*/
	public void init() {
		pings_clients = new PingsClient[nb_clients];
		on_retry_screen = false;

		/* Get hostname and port from the html file.*/
		String s = getParameter("ping_hostname");
		System.out.println("ping_hostname " + s);
		if (s != null)
		    SERVER_HOSTNAME = s;
		s = getParameter("ping_port");
		System.out.println("ping_port " + s);
		if (s != null)
		    SERVER_PORT = Integer.parseInt(s);
		String uuid = getParameter("ping_uuid");
		String nick = getParameter("ping_nickname");
		String nb_pings = getParameter("ping_nb_pings");
		int nb_pings_int = 0;
		if (nb_pings != null)
		    nb_pings_int = Integer.parseInt(nb_pings);

		//Setup the simulation mode if the argument 'simulation' was set to true
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
			    System.out.println("ping_uuid " + uuid);
			    System.out.println("ping_nickname " + nick);
			    System.out.println("ping_nb_pings " + nb_pings);
			    /* Do not try to get the cookie this way. It don't work well on Chrome.
			       Chrome append garbage to the end of the cookie!
			    if (uuid == null || uuid.length() == 0)
				uuid = (String)JSObject.getWindow(this).eval("javascript:get_cookie('" +
									     PingsClient.m_cookie_name + "_uuid', '')");
			    if (nick == null || nick.length() == 0)
				nick = (String)JSObject.getWindow(this).eval("javascript:get_cookie('" +
									     PingsClient.m_cookie_name + "_nickname', '')");
			    System.out.println("got uuid " + uuid);
			    System.out.println("got nickname " + nick);
			    */
			    if (uuid != null && uuid.length() != 36) {
				System.out.println("Bad UUID(" + uuid + "), will generate a new one.");
				uuid = "";
			    }
			    nick = PingsClient.sanitize_string(nick);
			    System.out.println("sanitized nickname " + nick);
			    if (nick == null || nick.length() == 0) {
				nick = initial_nickname;
				System.out.println("No nickname, use the default one.");
			    }
			    int old_nb = 0;
			    if(pings_clients[i] != null)
				old_nb = pings_clients[i].getSubmitedPingsCount();
			    else
				old_nb = nb_pings_int;
			    pings_clients[i] = new PingsClient(SERVER_HOSTNAME, SERVER_PORT, this, uuid, nick, old_nb);
			    pings_clients[i].addObserver(new ConnectErrorObserver());
			    pings_clients[i].addObserver(new ConnectProblemObserver());
			}
		}
		if (pings_gui == null)
		    pings_gui = new PingsGUI(this, nb_pings_int);
		else {
		    pings_gui = new PingsGUI(this, pings_gui.getPingsCount());
		}
		
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
		pings_gui.destroy();
	}

	public synchronized void errorConnectingServer(String message) {
		if (!on_retry_screen) {
			
			System.out.println("Unable to join the server, aborting the client(s)");
			
			on_retry_screen = true;
			
			for (int i = 0; i < nb_clients; i++) {
				pings_clients[i].destroy();
			}
			// Why do we need the following if?
			if (pings_gui == null) {
			    pings_gui = new PingsGUI(this, 0);
			}
			pings_gui.destroy();
			pings_gui.createRetryInterface(message);
		}
	}
	
	public synchronized void restartApplet() {
		if (on_retry_screen) {
			String name = pings_clients[0].getNickname();
			init();
			for (int i = 0; i < nb_clients; i++) {
				pings_clients[i].setNickname(name);
			}
			this.repaint();
		}
	}
	
	private class ConnectErrorObserver implements Observer {

		public void update(Observable arg0, Object arg1) {
			PingsClient client = (PingsClient) arg0;
			if (client.connect_error) {
				PingsApplet.this.errorConnectingServer(client.error_reason);
			}
		}
	}

	private class ConnectProblemObserver implements Observer {

		public void update(Observable arg0, Object arg1) {
			PingsClient client = (PingsClient) arg0;
			PingsApplet.this.pings_gui.updateProblemDisplay(client.error_reason);
		}
	}
}
