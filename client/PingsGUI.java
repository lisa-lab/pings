import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.net.InetAddress;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

//TODO : change the System.out.println with appropriate log event

/**
 * This class holds the GUI part of the applet. It creates the GUI objects :
 * button, fields, label and the ping globe and is responsible for their drawing
 * 
 * @author RaphaelBonaque
*/
public class PingsGUI implements ActionListener {
    
    private PingsApplet applet;
    
    //GUI related variables, they holds the GUI components
    private JButton pause_button, rename_button ;
    private JLabel pings_counter_display, client_info_display;
    private JTextField nickname_field;
    private PingsGlobe ping_globe;
    private Container applet_container;
    private Color text_color = new Color(227, 90, 0); //orange
    private Color background_color = Color.white;

    //GUI related to printing problem while we continue to work.
    private JTextArea problem_display;

    //GUI component for the retry
    private JTextArea retry_message;
    private JButton retry_button;
    
    //State variables
    private AtomicInteger pings_counter;
    private AtomicInteger pings_failed_counter;
    private int old_pings_counter;
    private GeoipInfo client_geoip_info = null;
    private String ips = "";
    private String problem_string = "";
    
    //The observers of the subclients that add pings effect on the globe
    // see ClientThreadObserver for more details
    private clientThreadObserver[][] clients_observers;
    
    //Some strings
    private String pause_tooltip = "Pause the application once the current pings are done.";
    private String resume_tooltip = "Resume the application.";
    /**
    * Create and initialize the components of the GUI.
    * <p>
    * That is :the globe , one pause/resume button and a field to change the 
    * client's nickname.
    * The globe is turnable, zoomable and show an effect for pings
    * The buttons comes with tooltips and shortcuts.
    */
    PingsGUI(PingsApplet parent, int old_pings_counter) {
	pings_counter = new AtomicInteger();
	this.old_pings_counter = old_pings_counter;
	pings_failed_counter = new AtomicInteger();

        applet = parent;
        applet.setBackground(background_color);
        
        //Recover the content and background of the applet to add components
        applet_container = applet.getContentPane();
        applet_container.setBackground(background_color);
        applet_container.setLayout(null);
        
        //Add the pause/resume button to the applet
        pause_button = new JButton("Pause");
        pause_button.setMnemonic(KeyEvent.VK_P);
        pause_button.setToolTipText(pause_tooltip);
        pause_button.setActionCommand("pause");
        pause_button.addActionListener(this);
        applet_container.add(pause_button);
        
        //Add the button to change the nickname
        rename_button = new JButton("Change");
        rename_button.setMnemonic(KeyEvent.VK_U);
        rename_button.setToolTipText("Update your name for the leaderboard");
        rename_button.setActionCommand("rename");
        rename_button.addActionListener(this);
        rename_button.setEnabled(false);
        applet_container.add(rename_button);
        
        //Add the field to change the nickname
        nickname_field = new JTextField(15);
        nickname_field.setText(applet.pings_clients[0].getNickname());
        //Add a listener to able/disable the rename_button if the nickname is
        //different from the one stored
        nickname_field.getDocument().addDocumentListener(
            new DocumentListener() {
                public void changedUpdate(DocumentEvent e) {
                    check_enable_button();
                }                
                public void insertUpdate(DocumentEvent e) {
                    check_enable_button();
                }                
                public void removeUpdate(DocumentEvent e) {
                    check_enable_button();
                }
            });
        //Add a listener to update the name when 'ENTER' key is hit
        nickname_field.addKeyListener(
            new KeyListener() {
                public void keyTyped(KeyEvent e) {}
                public void keyReleased(KeyEvent e) {}
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        rename_button.doClick();
                    }
                }
            });
        applet_container.add(nickname_field);
        
        
        //Add the display for the number of pings done
        pings_counter_display = new JLabel("No ping sent yet");
        pings_counter_display.setForeground(text_color);
        applet_container.add(pings_counter_display);
        
        //Add the display for the client info
        client_info_display = new JLabel("");
        client_info_display.setForeground(text_color);
        applet_container.add(client_info_display);
        
        //Add the globe
        ping_globe = new PingsGlobe(PingsClient.subClient_number * 2);
        int lines_size = 10 + nickname_field.getPreferredSize().height;
        ping_globe.resizeGlobe(Math.min(applet.getWidth(), applet.getHeight()) - lines_size);
        ping_globe.setBackground(background_color);
        applet_container.add(ping_globe);

	//Add the problem display
	problem_display = new JTextArea("");
	problem_display.setForeground(Color.red);
	problem_display.setBackground(background_color);
	//problem_display.setLineWrap(true);//Enabling this make problem_display being hided
	problem_display.setEditable(false);
	applet_container.add(problem_display);

        //Create the component for the "retry view" but don't hook them to the applet
        retry_message = new JTextArea("");
        retry_button = new JButton();
        
        //Set the layout
        setLayout();
        
        //Add an observer to refresh globe on resize
        applet.addComponentListener(new onResize());
        
        //Add an observer to the client to update the GUI.
        clients_observers = new clientThreadObserver[applet.nb_clients][];
        for (int i = 0; i < applet.nb_clients; i++) {
            PingsClient.subClient[] subClientsPool = applet.pings_clients[i].getSubClientsPoolCopy();
            clients_observers[i] = new clientThreadObserver[subClientsPool.length];
            for (int j = 0; j < subClientsPool.length; j++) {
                clients_observers[i][j] = new clientThreadObserver();
                subClientsPool[j].addObserver(clients_observers[i][j]);
            }
        }
    }
    
    /**
     * Position the component of the GUI.
     * 
     * Currently use absolute positioning to gather most of the component in the
     * top-left corner.
     */
    private void setLayout() {
        //First row of display
        Dimension name_size = nickname_field.getPreferredSize();
        Dimension update_name_size =  rename_button.getPreferredSize();
        Dimension counter_size = pings_counter_display.getPreferredSize();
        Dimension pause_size = pause_button.getMinimumSize();
        
        int row_height = name_size.height;
        
        nickname_field.setBounds(5, 5,
				 name_size.width, row_height);
        
        rename_button.setBounds(8 + name_size.width, 5,
				update_name_size.width, row_height);
        
        pause_button.setBounds(11 + name_size.width + update_name_size.width, 5,
			       pause_size.width, row_height);
        
        pings_counter_display.setBounds(applet.getWidth() - counter_size.width, 5,
					counter_size.width, row_height);
        //Second row of display
        Dimension client_info_size = client_info_display.getPreferredSize();
        
        client_info_display.setBounds(5, 8 + row_height,
				      client_info_size.width, row_height);
        
	//Set the problem display
        Dimension problem_size = problem_display.getPreferredSize();
	int n_lines = 0;
	String prob_text = problem_display.getText();
	if (prob_text.length() > 0) n_lines++;
	for(int i=0; i < prob_text.length(); i++){
	    if(prob_text.charAt(i) == '\n') n_lines++;
	}
	problem_display.setBounds(5, 11 + 2 * row_height,
				  problem_size.width, n_lines * row_height);

        //Set the globe to use the full space available - the 2 lines
        ping_globe.setBounds(0, 10 + (2 + n_lines) * row_height,
			     applet.getWidth(), applet.getHeight());
        
        retry_message.setBounds((applet.getWidth()/2)-200, (applet.getHeight()/2)-50, 400, 100);
        retry_button.setBounds((applet.getWidth()/2)-100, (applet.getHeight()/2)+50, 200, 50);

    }
    
    /**
     * Update the counter displaying the number of ping sent
     */    
    private void updatePingsCounterDisplay() {
	int session = pings_counter.get();
	int nb = session + old_pings_counter;
        if (nb == 0) {
            pings_counter_display.setText("No ping sent yet");
        }
        else {
	    int failed = pings_failed_counter.get();
	    String s = "ip tested: total(cookies) " +nb+ ", session " +session;

	    if(session >= 9 && ((float)failed)/session > 0.6)
		s += ", failed " + failed;

	    pings_counter_display.setText(s);
        }
        setLayout();
    }
    
    /**
     * Update the problem display
     */
    public void updateProblemDisplay(String s) {
	problem_display.setText(s);
        setLayout();
    }

    private void updateClientInfoDisplay(String ip_address, GeoipInfo client_info) {
        String info_str = ip_address;
	int nb = 0;
	if (ip_address != null && !ip_address.equals(""))
	    nb += 1;
        if (client_info != null &&
	    (client_info.city != null) &&
	    !client_info.city.equals("")){
	    if (nb > 0){
		info_str += ", ";
	    }
	    info_str += client_info.city;
	    nb += 1;
	}
	if (client_info != null &&
	    (client_info.region != null) &&
	    !client_info.region.equals("")){
	    if (nb > 0){
		info_str += ", ";
	    }
	    info_str += client_info.region;
	}
	if (client_info != null &&
	    (client_info.country != null) &&
	    !client_info.country.equals("")){
	    if (nb > 0){
		info_str += ", ";
	    }
	    info_str += client_info.country;
	}
	if (info_str.equals(ip_address)){
	    if (nb > 0){
		info_str += ", ";
	    }
	    info_str += "No geographic information";
	}
        client_info_display.setText(info_str);
        client_info_display.repaint();
	if (client_info != null)
	    ping_globe.setOrigin(client_info);
    }
    
    public void check_enable_button() {
        if (nickname_field.getText().equals(applet.pings_clients[0].getNickname())) {
            rename_button.setEnabled(false);
        }
        else {
            rename_button.setEnabled(true);
        }
    }

    public int getPingsCount() {
	return pings_counter.get() + old_pings_counter;
    }

    class clientThreadObserver implements Observer {
        
        private PingsGlobe.PingGUI gui_effect = null;
        private InetAddress last_address = null ;
        
        public void update(Observable o, Object arg) {
            
            PingsClient.subClient client = (PingsClient.subClient)o;
            
            if (client.getSourceGeoip() != null || client_info_display.getText().length() == 0) {
	            if (client_info_display.getText().length() == 0 ||
			!client.getSourceGeoip().equals(client_geoip_info)) {
	                client_geoip_info = client.getSourceGeoip();
			InetAddress local = client.getSourceAddress();
			InetAddress global = null;
			try{
			    global = client.getSourceExternalAddress();
			} catch (RuntimeException _) {}
			String ip = "";
			if (local != null){
			    ip += "Local ip: ";
			    String s = local.toString();
			    if (s.charAt(0) == '/')
				ip += s.substring(1);
			}
			if (global != null){
			    if (ip.length() > 0)
				ip += ", ";
			    ip += "Global ip: ";
			    String s = global.toString();
			    if (s.charAt(0) == '/')
				ip += s.substring(1);
			}
			ips = ip;
	                //FIXME: get ip from server
	                SwingUtilities.invokeLater( new Runnable() {
	                    public void run() {
	                        updateClientInfoDisplay(ips, client_geoip_info);
	                    }
	                });
	            }
            }
            
            GeoipInfo current_ping_geoip = client.getCurrentDestGeoip();
            InetAddress current_ping_address = client.getCurrentPingDest();
            
            //If there are several subClient threads then this might still be 
            // reachable as they try to set the same geoip several times
            if (current_ping_address == null) return;
            
            //If there is a new ping add it to the counter and register an 
            //effect for the globe
            if (gui_effect == null && last_address != current_ping_address) {
		int nb_ping;
		int nb_fail;
		if (last_address == null)
		    nb_ping = pings_counter.get();
		else{ // The thread finished testing an ip
		    nb_ping = pings_counter.incrementAndGet();

		    if(!client.last_pings_succeded){
			nb_fail = pings_failed_counter.incrementAndGet();
			if((nb_fail >= 15) && ((nb_ping - 1) <= nb_fail)){ // -1 as we don't incr counter at the same time with a lock.
			    String ret = "All pings failed. Are pings blocked by a firewall? Your institution's firewall?";

			    if(!ret.equals(problem_string)){
				problem_string = ret;
				SwingUtilities.invokeLater( new Runnable() {
					public void run() {
					    System.out.println("Will call updateProblemDisplay with " + problem_string);
					    updateProblemDisplay(problem_string);
					}
				    });
			    }
			}
		    }else
			nb_fail = pings_failed_counter.get();
		    System.out.println("Pings finished stats (old pings, new pings, failed new pings): " +
				       old_pings_counter + " " + nb_ping + " " + nb_fail);
		}

                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        updatePingsCounterDisplay();
                    }
                });
                
                last_address = current_ping_address;
                gui_effect = ping_globe.addPing(current_ping_geoip);
            }
            //Else if it's the last ping update it
            else if (gui_effect != null && last_address == current_ping_address) {
                gui_effect.updatePingGUIValue(client.getCurrentPingResult());
                gui_effect = null;
            }
            //Else there are two case :
            //_ either the destination address is the same (and with the 
            //current implementation there is no way to know it). This case has a
            //very low probability.
            //_ or we somehow missed the result of the previous ping, hence 
            //we need to do some workaround for the old ping and declare a 
            //new one.
            else {
                if (gui_effect != null) gui_effect.Error();
                String value = client.getCurrentPingResult();
                gui_effect = ping_globe.addPing(current_ping_geoip);
                if (value!= null && !value.equals("")) {
                    gui_effect.updatePingGUIValue(client.getCurrentPingResult());
                    gui_effect = null;
                }
            }
        }
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
            if (!applet.pings_clients[0].isRunning()) {
                pause_button.setText("Resume");
                pause_button.setToolTipText(resume_tooltip);
                pause_button.setActionCommand("resume");
            }
            else {
                pause_button.setText("Pause");
                pause_button.setToolTipText(pause_tooltip);
                pause_button.setActionCommand("pause");
            }
            setLayout();
    }
    
    /**
     * The listener for events in the applet : the interactions with the 
     * components (other than the globe) are handled here.
     */
    public void actionPerformed (ActionEvent event) {
        
        //Find the issued command and store it in the 'command' variable
        String command = event.getActionCommand();
        
        //Handle the pause/resume button
        if (command.equals("pause")) {
            //Pause the client if it was running, do nothing otherwise
            
            boolean was_running = applet.pings_clients[0].isRunning();
            //Issue a warning if the client was not running
            if (!was_running) {
                //System.out.println ("Was already paused.");
            }
            else {
                applet.stop();
            }
            //Then refresh the button
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    refreshPauseButton();
                }
            });
        }
        else if (command.equals("resume")) {
            //resume the client if it was paused, do nothing otherwise
            
            boolean was_running = applet.pings_clients[0].isRunning();
            //Issue a warning if the client was running
            if (was_running) {
                //System.out.println ("Was already running.");
            }
            else {
                applet.start();
            }
            //Then refresh the button
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    refreshPauseButton();
                }
            });
        }
        
        //Handle the rename button
        else if (command.equals("rename")) {
            String new_name = nickname_field.getText();
	    new_name = PingsClient.sanitize_string(new_name);
	    nickname_field.setText(new_name);

            for (int i = 0; i < applet.nb_clients; i++) {
                applet.pings_clients[i].setNickname(new_name);
            }
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    rename_button.setEnabled(false);
                }
            });
            System.out.println("Nick updated to " + new_name);
        }
        
        //Handle any other unknown component
        else {
            //FIXME
            System.out.println ("Error in button interface.");
        }
    }
    
    class onResize implements ComponentListener{
        
        public void componentResized(ComponentEvent e) {
            ping_globe.forceRefresh();
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    setLayout();
                }
            });
        }
        public void componentShown(ComponentEvent e) {}
        public void componentHidden(ComponentEvent e) {}
        public void componentMoved(ComponentEvent e) {}
    }
    
    /**
     * Destroy as much of the GUI as possible.
     * <p>
     * It remove the components and unregister the observers of the clients,
     * for this reason it can fail is the client was destroyed before (hence the
     * 'try').
     */
    public void destroy() {
        try {
            ping_globe.removeAll();
            applet_container.removeAll();
            
            for (int i = 0; i < applet.nb_clients; i++) {
                PingsClient.subClient[] subClientsPool = applet.pings_clients[i].getSubClientsPoolCopy();
                for (int j = 0; j < subClientsPool.length; j++) {
                    subClientsPool[j].deleteObserver(clients_observers[i][j]);
                }
            }
        } catch (Exception _) {}
        
    }
    
    private class CreateRetryInterface implements Runnable, ActionListener{
        
        private String message;
        
        public CreateRetryInterface(String msg) {
            this.message = msg;
        }
        
        //Handle the retry button
        public void actionPerformed(ActionEvent e) {
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    applet_container.removeAll();
                    applet.restartApplet();
                }
            });
        }
        
        public void run() {
            applet_container = applet.getContentPane();
            applet_container.setBackground(background_color);
            applet_container.setLayout(null);
            
            retry_message.setText(message);
            retry_message.setForeground(text_color);
            retry_message.setBackground(background_color);
            retry_message.setLineWrap(true);
            retry_message.setEditable(false);
            applet_container.add(retry_message);
            
            retry_button.setText("Retry");
            retry_button.setMnemonic(KeyEvent.VK_R);
            retry_button.setToolTipText("Try to relaunch the application");
            retry_button.setActionCommand("retry_connect");
            retry_button.addActionListener(this);
            applet_container.add(retry_button);
            
            setLayout();
            
            applet.repaint();
            
        }
    }
    
    public void createRetryInterface(String message) {
        SwingUtilities.invokeLater( new CreateRetryInterface(message));
    }
}
