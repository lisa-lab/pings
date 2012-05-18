import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.net.InetAddress;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import javax.swing.*;


/**
 * This class holds the web applet. It creates the GUI and links it to the
 * PingsClient.
 * <p>
 * @author //FIXME
*/
public class PingsApplet extends JApplet implements ActionListener{
	
	private static final long serialVersionUID = 1L;
	
	// PingsClient related variables
	private final String SERVER_HOSTNAME = "localhost";
	private final int SERVER_PORT = 6543;
	private PingsClient m_pings_client;
	
	//GUI related variables, they holds the GUI components
	private JButton pause_button, rename_button ;
	private JLabel pings_counter_display, client_info_display;
	private JTextField nickname_field;
	private PingGlobe ping_globe;
	private Container button_container;
	
	//State variables
	private int pings_counter = 0;
	private GeoipInfo client_geoip_info = null;
	private Thread client_thread;
	
	/**
	* The initialization of the applet, it creates the PingsClient and the GUI
	* <p>
	* @see PingsClient
	* @see  #initGUI()s
	*/
	public void init() {
		m_pings_client = new PingsClient(SERVER_HOSTNAME, SERVER_PORT);	
		m_pings_client.setNickname("Yoda");
		client_thread = new Thread(m_pings_client);
		client_thread.start();
		
		initGUI();
		
		//FIXME : Remove simulation
		//new Thread((Runnable) new PingsClientSimulation(ping_globe)).start();
	}
	
	public static void main(String [ ] args) {}
	
	public void paint(Graphics g) {
		g.setColor(Color.BLACK);
	}
	
	//FIXME
	public void destroy() {
		client_thread.interrupt();
		}
	
	/**
	 * Position the component of the GUI.
	 * 
	 * Currently use absolute positioning to gather most of the component in the
	 * top-left corner.
	 */
	private void setLayout() {
		//Set the globe to use the full space available
		ping_globe.setBounds(0, 0, getWidth(), getHeight());
		
		//First raw of display
		Dimension name_size = nickname_field.getPreferredSize();
		Dimension update_name_size =  rename_button.getPreferredSize();
		Dimension client_info_size = client_info_display.getPreferredSize();
		
		int first_row_height = name_size.height;
		
		nickname_field.setBounds(5,5,
				name_size.width,first_row_height);
		
		rename_button.setBounds(8 + name_size.width, 5,
				update_name_size.width, first_row_height);
		
		client_info_display.setBounds(11 + name_size.width + update_name_size.width, 5,
				client_info_size.width,first_row_height );
		
		//Second raw of display
		Dimension counter_size = pings_counter_display.getPreferredSize();
		Dimension pause_size = pause_button.getMinimumSize();
		
		pings_counter_display.setBounds(5, 8 + first_row_height,
				counter_size.width, counter_size.height);
		
		pause_button.setBounds(8 + counter_size.width,8 + first_row_height,
				pause_size.width, counter_size.height);
	}
	
	/**
	 * Update the counter displaying the number of ping sent
	 */	
	private void updatePingsCounterDisplay() {
		if (pings_counter == 0) {
			pings_counter_display.setText("No pings sent yet");
		}
		else {
			pings_counter_display.setText(pings_counter + " pings sent");
		}
	}
	
	private void updatePingGUIValue(PingGlobe.PingGUI ping_gui, String value) {
		final String regex = "\\S+\\s\\S+\\s(\\d+)\\s(\\d+)\\s(\\d+).+";
		String[] values = value.split(regex);
		int nb_try = Integer.parseInt(values[0]);
		int nb_worked = Integer.parseInt(values[1]);
		float totaltime = Float.parseFloat(values[2]) /1000f;
		if (nb_worked < nb_try -1 )
		{
			ping_gui.SetValue(-1);
		}
		else 
		{
			ping_gui.SetValue(totaltime /nb_try );
		}
	}
	
	private void updateClientInfoDisplay(String ip_adress, GeoipInfo client_info) {
		String info_str = ": " +ip_adress + " "+client_info.city +", " + client_info.country;
		client_info_display.setText(info_str);
		setLayout();
	}
	
	/**
	* Create and initialize the components of the GUI.
	* <p>
	* That is :the globe , one pause/resume button and a field to change the 
	* client's nickname.
	* The globe is turnable, zoomable and show an effect for pings
	* The buttons comes with tooltips and shortcuts.
	*/
	private void initGUI() {
		//Recover the content and background of the applet to add components
		button_container = getContentPane();
		button_container.setBackground (Color.BLACK);
		//button_container.setLayout (new FlowLayout ());
		
		//Add the pause/resume button to the applet
		pause_button = new JButton ("Pause");				
		pause_button.setMnemonic(KeyEvent.VK_P);
		pause_button.setToolTipText("Pause or resume the pings");
		pause_button.setActionCommand("pause");
		pause_button.addActionListener(this);
		button_container.add (pause_button);
		
		//Add the field to change the nickname
		nickname_field = new JTextField(15);
		button_container.add (nickname_field);
		//nickname_field.setText(m_pings_client.getNickname());
		
		//Add the button to change the nickname
		rename_button = new JButton ("Ok");
		rename_button.setMnemonic(KeyEvent.VK_U);
		rename_button.setToolTipText("Update your name for the leaderboard");
		rename_button.setActionCommand("rename");
		rename_button.addActionListener(this);
		rename_button.setEnabled(false);
		button_container.add (rename_button);
		
		//Add the display for the number of pings done
		pings_counter_display = new JLabel("");
		updatePingsCounterDisplay();
		button_container.add (pings_counter_display);
		
		//Add the display for the client info
		client_info_display = new JLabel(":                              ");
		button_container.add (client_info_display);
		
		//Add the globe
		ping_globe = new PingGlobe();
		getContentPane().add(ping_globe);
		
		//Set the layout
		setLayout();
		
		//Add an observer to the client to update the GUI.
		m_pings_client.addObserver(new Observer() {
			private PingGlobe.PingGUI gui_effect = null;
			
			public void update(Observable o, Object arg) {
				PingsClient client = (PingsClient)o;
				
				if (client_geoip_info != client.getSourceGeoip()) {
					client_geoip_info = client.getSourceGeoip();
					//FIXME: get ip from server
					updateClientInfoDisplay("", client_geoip_info);
					return;
				}
				
				GeoipInfo current_ping_geoip = client.getCurrentDestGeoip();
				
				//If there is a new ping add it to the counter and register an 
				//effect for the globe
				if (gui_effect == null && current_ping_geoip != null) {
					pings_counter++;
					updatePingsCounterDisplay();
					
					gui_effect = ping_globe.addPing(current_ping_geoip);
				}
				//Else if it's the last ping update it
				else if (gui_effect != null && current_ping_geoip == gui_effect.getGeoip()) {
					updatePingGUIValue(gui_effect,client.getCurrentPingResult());
					gui_effect = null;
				}
				//Else we somehow missed the result of the previous ping, hence 
				// we need to do some workaround for the old ping and declare a 
				// new one.
				else {
					gui_effect.unknownError();
					gui_effect = ping_globe.addPing(current_ping_geoip);
				}
			}
		});
	}
	
	//FIXME
	/**
	 * This method is used to refresh the Pause/Resume button to make it show
	 * 'Pause' or 'Resume' and act accordingly depending on the client state 
	 * expressed by the 'm_is_running' variable.
	 * <p>
	 * If the client is running this makes the button show "Pause" otherwise it 
	 * shows "Resume".
	 */
	private void refreshPauseButton() {
			if (client_thread.isInterrupted()) {
				pause_button.setText("Resume");
				pause_button.setActionCommand("resume");
			}
			else {
				pause_button.setText("Pause");
				pause_button.setActionCommand("pause");
			}
	}
	
	/**
	 * The listener for events in the applet : the interactions with the 
	 * components (other than the globe) is handle here.
	 */
	@SuppressWarnings("deprecation")
	public void actionPerformed (ActionEvent event) {
		
		//Find the issued command and store it in the 'command' variable
		String command = event.getActionCommand();
		
		//Handle the pause/resume button
		if (command.equals("pause")) {
			//Pause the client if it was running, do nothing otherwise
			
			//FIXME
			boolean was_running = !client_thread.isInterrupted();
			//Issue a warning if the client was not running
			if (!was_running) {				
				//System.out.println ("Was already paused.");
			}
			else {
				client_thread.suspend();
			}
			//Then refresh the button
			refreshPauseButton();
		}
		else if (command.equals("resume")) {
			//resume the client if it was paused, do nothing otherwise

			//FIXME
			//boolean was_running = !m_pings_client.m_is_running.compareAndSet(false, true);
			boolean was_running = client_thread.isInterrupted();
			//Issue a warning if the client was running
			if (was_running) {
				//System.out.println ("Was already running.");
			}
			else {

				client_thread.resume();
			}
			//Then refresh the button
			refreshPauseButton();
		}
		
		//Handle the rename button
		else if (command.equals("rename")) {
			String new_name = nickname_field.getText();
			m_pings_client.setNickname(new_name);
			System.out.println("Nick updated to " + new_name);
		}
		
		//Handle any other unknown component
		else {
			//FIXME
			System.out.println ("Error in button interface.");
		}
	}
}
