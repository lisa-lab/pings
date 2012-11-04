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
    private Container button_container;
    private Color text_color = new Color(70,70,70);
    private Color background_color = new Color(0,0,0);
    
    //GUI component for the retry
    private JTextArea retry_message;
    private JButton retry_button;
    
    //State variables
    private int pings_counter = 0;
    private GeoipInfo client_geoip_info = null;
    
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
    PingsGUI (PingsApplet parent) {
        applet = parent;
        applet.setBackground(background_color);
        
        //Recover the content and background of the applet to add components
        button_container = applet.getContentPane();
        button_container.setBackground (background_color);
        button_container.setLayout(null);
        
        //Add the pause/resume button to the applet
        pause_button = new JButton ("Pause");
        pause_button.setMnemonic(KeyEvent.VK_P);
        pause_button.setToolTipText(pause_tooltip);
        pause_button.setActionCommand("pause");
        pause_button.addActionListener(this);
        button_container.add (pause_button);
        
        //Add the button to change the nickname
        rename_button = new JButton ("Change");
        rename_button.setMnemonic(KeyEvent.VK_U);
        rename_button.setToolTipText("Update your name for the leaderboard");
        rename_button.setActionCommand("rename");
        rename_button.addActionListener(this);
        rename_button.setEnabled(false);
        button_container.add (rename_button);
        
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
        button_container.add (nickname_field);
        
        
        //Add the display for the number of pings done
        pings_counter_display = new JLabel("No ping sent yet");
        pings_counter_display.setForeground(text_color);
        button_container.add (pings_counter_display);
        
        //Add the display for the client info
        client_info_display = new JLabel("");
        client_info_display.setForeground(text_color);
        button_container.add (client_info_display);
        
        //Add the globe
        ping_globe = new PingsGlobe();
        ping_globe.resizeGlobe(Math.min(applet.getWidth(),applet.getHeight()));
        applet.getContentPane().add(ping_globe);
        
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
        //Set the globe to use the full space available
        ping_globe.setBounds(0, 0, applet.getWidth(), applet.getHeight());
        
        //First raw of display
        Dimension name_size = nickname_field.getPreferredSize();
        Dimension update_name_size =  rename_button.getPreferredSize();
        Dimension client_info_size = client_info_display.getPreferredSize();
        
        int row_height = name_size.height;
        
        nickname_field.setBounds(5,5,
                name_size.width,row_height);
        
        rename_button.setBounds(8 + name_size.width, 5,
                update_name_size.width, row_height);
        
        client_info_display.setBounds(11 + name_size.width + update_name_size.width, 5,
                client_info_size.width,row_height );
        
        //Second raw of display
        Dimension counter_size = pings_counter_display.getPreferredSize();
        Dimension pause_size = pause_button.getMinimumSize();
        
        pings_counter_display.setBounds(5, 8 + row_height,
                counter_size.width, row_height);
        
        pause_button.setBounds(8 + counter_size.width,8 + row_height,
                pause_size.width, row_height);
        
        retry_message.setBounds((applet.getWidth()/2)-200, (applet.getHeight()/2)-50, 400, 100);
        retry_button.setBounds((applet.getWidth()/2)-100, (applet.getHeight()/2)+50, 200, 50);
        
    }
    
    /**
     * Update the counter displaying the number of ping sent
     */    
    private void updatePingsCounterDisplay() {
        if (pings_counter == 0) {
            pings_counter_display.setText("No ping sent yet");
        }
        else {
            pings_counter_display.setText(pings_counter + " ip tested");
        }
        setLayout();
    }
    
    private void updateClientInfoDisplay(String ip_address, GeoipInfo client_info) {
        String info_str = ": " + ip_address + " ";
        if (client_info.city != null && !client_info.city.equals("")) info_str += client_info.city + ", ";
        info_str += client_info.country;
        client_info_display.setText(info_str);
        ping_globe.setOrigin(client_info);
        setLayout();
        applet.repaint();
    }
    
    public void check_enable_button() {
        if (nickname_field.getText().equals(applet.pings_clients[0].getNickname())) {
            rename_button.setEnabled(false);
        }
        else {
            rename_button.setEnabled(true);
        }
    }
    
    class clientThreadObserver implements Observer {
        
        private PingsGlobe.PingGUI gui_effect = null;
        private InetAddress last_address = null ;
        
        public void update(Observable o, Object arg) {
            
            PingsClient.subClient client = (PingsClient.subClient)o;
            
            if (client.getSourceGeoip() != null) {
	            if (!client.getSourceGeoip().equals(client_geoip_info)) {
	                client_geoip_info = client.getSourceGeoip();
	                //FIXME: get ip from server
	                SwingUtilities.invokeLater( new Runnable() {
	                    public void run() {
	                        updateClientInfoDisplay("", client_geoip_info);
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
                last_address = current_ping_address;
                
                pings_counter++;
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        updatePingsCounterDisplay();
                    }
                });
                
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
                if (gui_effect != null) gui_effect.unknownError();
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
            button_container.removeAll();
            
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
                    button_container.removeAll();
                    applet.restartApplet();
                }
            });
        }
        
        public void run() {
            button_container = applet.getContentPane();
            button_container.setBackground (background_color);
            button_container.setLayout(null);
            
            retry_message.setText(message);
            retry_message.setForeground(text_color);
            retry_message.setBackground(background_color);
            retry_message.setLineWrap(true);
            retry_message.setEditable(false);
            button_container.add (retry_message);
            
            retry_button.setText("Retry");
            retry_button.setMnemonic(KeyEvent.VK_R);
            retry_button.setToolTipText("Try to relaunch the application");
            retry_button.setActionCommand("retry_connect");
            retry_button.addActionListener(this);
            button_container.add (retry_button);
            
            setLayout();
            
            applet.repaint();
            
        }
    }
    
    public void createRetryInterface(String message) {
        SwingUtilities.invokeLater( new CreateRetryInterface(message));
    }
}
