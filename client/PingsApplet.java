import java.awt.*;
import java.awt.event.*;

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
	private JTextField nickname_field;
	private PingGlobe ping_globe;
	
	/**
	* The initialization of the applet, it creates the PingsClient and then GUI
	* <p>
	* @see PingsClient
	* @see  #initGUI()
	*/
	public void init() {
		
		// Initialization (on page load).
		m_pings_client = new PingsClient(SERVER_HOSTNAME, SERVER_PORT);
		new Thread(m_pings_client).start();
		
		initGUI();
		
		//FIXME : Remove dummy examples
		useDummyExamples();
	}
	
	/**
	 * Interrupt the still-running threads of the client
	 */
	public void destroy() {
		m_pings_client.interrupt();
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
		Container content_pane = getContentPane();
		content_pane.setBackground (Color.BLACK);
		content_pane.setLayout (new FlowLayout ());
		
		//Add the pause/resume button to the applet
		pause_button = new JButton ("Pause");				
		pause_button.setMnemonic(KeyEvent.VK_P);
		pause_button.setToolTipText("Pause or resume the pings");
		pause_button.setActionCommand("pause");
		pause_button.addActionListener(this);
		content_pane.add (pause_button);
		
		//Add the field to change the nickname
		nickname_field = new JTextField(15);
		content_pane.add (nickname_field);
		nickname_field.setText(m_pings_client.getNickname());
		
		//Add the button to change the nickname
		rename_button = new JButton ("Update your name");
		rename_button.setMnemonic(KeyEvent.VK_U);
		rename_button.setToolTipText("Update your name for the leaderboard");
		rename_button.setActionCommand("rename");
		rename_button.addActionListener(this);
		content_pane.add (rename_button);
		
		//Add the globe
		ping_globe = new PingGlobe();
		content_pane.add(ping_globe);
	}
	
	/**
	 * This method is used to refresh the Pause/Resume button to make it show
	 * 'Pause' or 'Resume' and act accordingly depending on the client state 
	 * expressed by the 'm_is_running' variable.
	 * <p>
	 * If the client is running this makes the button show "Pause" otherwise it 
	 * shows "Resume".
	 */
	private void refreshPauseButton() {
			if (m_pings_client.m_is_running.get()) {
				pause_button.setText("Pause");
				pause_button.setActionCommand("pause");				
			}
			else {
				pause_button.setText("Resume");
				pause_button.setActionCommand("resume");
			}
	}
	
	/**
	 * The listener for events in the applet : the interactions with the 
	 * components (other than the globe) is handle here.
	 */
	public void actionPerformed (ActionEvent event) {
		
		//Find the issued command and store it in the 'command' variable
		String command = event.getActionCommand();
		
		//Handle the pause/resume button
		if (command.equals("pause")) {
			//Pause the client if it was running, do nothing otherwise
			boolean was_running = m_pings_client.m_is_running.compareAndSet(true, false);
			//Issue a warning if the client was not running
			if (!was_running) {
				//FIXME
				System.out.println ("Was already paused.");
			}
			//Then refresh the button
			refreshPauseButton();
		}
		else if (command.equals("resume")) {
			//resume the client if it was paused, do nothing otherwise
			boolean was_running = !m_pings_client.m_is_running.compareAndSet(false, true);
			//Issue a warning if the client was running
			if (was_running) {
				//FIXME
				System.out.println ("Was already running.");
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
	
	private void useDummyExamples() {
		//FIXME
		//For test purpose only :
		
		GeoipInfo montreal = new GeoipInfo("Montreal","Canada",-73.55f,45.5f);
		GeoipInfo newyork = new GeoipInfo("New-York","United States",-70.93f, 40.65f);
		GeoipInfo paris = new GeoipInfo("Paris","France",2.35f, 48.85f);
		GeoipInfo tokyo = new GeoipInfo("Tokyo","Japan",139.68f, 35.68f);
		
		ping_globe.setOrigin(montreal);
		
		ping_globe.addPing(newyork);
		ping_globe.addPing(paris);
		ping_globe.addPing(tokyo);
	}
}