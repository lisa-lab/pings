import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

import javax.swing.SwingUtilities;
import netscape.javascript.JSObject;

/**
 * Pings client. Connects to the Pings server, retrieves addresses
 * to be pinged, pings them and submits the results back to the server.
 *
 * To use, embed in a thread object, then start the thread. If you want
 * to be notified when the source geoip info or ping destination change, you
 * can register yourself with addNotifier().
 *
 * We give up (and stop the thread) after a total of MAX_ERROR_COUNT errors
 * has occurred. We also do exponential backoff on consecutive errors,
 * to avoid overloading the servers if they have a problem. There is a
 * maximum wait time of MAX_WAIT_TIME. This is set to wait for 5 days before
 * stopping to work without user intervention if we need to reboot the server.
 *
 * We will wait at least MIN_ROUND_TIME seconds in average before contacting
 * the server to get new ip to pings. As we have a queue of pings address to
 * get, each pings slot in the queue need to wait MIN_ROUND_TIME*pings_queue_size.
 * We also add a random number of up to 10% of the server MIN_ROUND_TIME to
 * help spread the users in case they all start at the same time.
 * We also multiple the wait_time by WAIT_TIME_BOOST to help load testing
 * wait_time is the time we should wait normally.
 * @author Christian Hudon <chrish@pianocktail.org>
 */
public class PingsClient extends Observable implements Runnable {
    public final int MAX_WAIT_TIME = 15 * 60; //Max wait time in seconds
    public final int MAX_ERROR_COUNT = 10 + //It take 10 tries to wait 15 minutes
	5*24*60/(MAX_WAIT_TIME/60); //We will try for 5 days before stopping!
    public final long MIN_ROUND_TIME = 1 * 60; //In seconds.
    public double WAIT_TIME_BOOST = 1; //default to 1, no change.

    // These variables are initialized in the constructor and then
    // only accessed by the subClients thread. No need for locking, etc.
    private ClientInfo m_client_info;
    private ServerProxy m_server_proxy;
    private final static Logger LOGGER = Logger.getLogger(PingsClient.class.getName());
    
    // These variables are accessed both by the PingsClient thread and
    // by other threads using this class. They need to use something
    // like the Java Atomic types or other to prevent fun multithreading
    // bugs!
    
    protected AtomicReference<GeoipInfo> m_source_geoip;
    protected AtomicBoolean m_is_running;
    protected AtomicReference<String> m_nick;
    protected AtomicInteger m_total_error_count;
    protected AtomicInteger m_total_submited_pings;
    
    // For analysis
    protected AtomicInteger m_measurements_failed;
    int num_measurements = 0;
    String measurements = "";
    boolean shown_analysis = false;

    //The number of subClient(s) to run simultaneously
    protected int subClient_number = 6;
    //The subClients
    protected subClient[] subClients_pool;
    protected Thread[] subClients_threads_pool;
    
    //The queue of ServerProxy.Pings
    private int pings_queue_size = 1;
    private ServerProxy.Pings[] pings_queue;
    
    //The index in the queue of the next Pings to look into to find new addresses
    //to be pinged. This is only used as an indication to allow pings to be
    //filled one after an other so that there are as much free addresses as 
    //possible
    private int next_pings_with_addresses = 0;
    
    //The number of addresses we didn't store result about for each Pings in the 
    //queue
    private int remaining_addresses[];
    //The index of the next available address for each Pings, please note that 
    //there is a difference between available/free addresses -which were not 
    //given to a subClient yet- and remaining addresses which might have been 
    //given to a subClient but didn't return result yet
    private int next_available_address[];

    //Variables used to notify the applet that there was a connection problem with
    //the server. The error_reason is also set when there is a problem,
    //but this is not yet an error.
    public boolean connect_error;
    public String error_reason;
    
    /**
       A reference to the applet. This is needed to set the cookie.
     */
    private PingsApplet applet;
    private long start_time;

    /**
       The prefix of cookies
       The name is hardcoded in the index.html file too.
    */
    public final static String m_cookie_name="udem_ping";

    public PingsClient(String server_hostname, int server_port, PingsApplet applet,
		       String uuid, String nick, int nb_submited_pings) {
        m_client_info = new ClientInfo(uuid, nick);
	this.applet = applet;

        m_server_proxy = new ServerProxy(server_hostname, server_port);
        
        m_nick = new AtomicReference<String>(m_client_info.getNickname());
        m_source_geoip = new AtomicReference<GeoipInfo>();
        m_total_error_count = new AtomicInteger();
        m_total_submited_pings = new AtomicInteger(nb_submited_pings);
        m_measurements_failed = new AtomicInteger();
        m_is_running = new AtomicBoolean(false);
        
        //Initialize the pings_queue
        pings_queue = new ServerProxy.Pings[pings_queue_size];
        next_available_address = new int[pings_queue_size];
        remaining_addresses= new int[pings_queue_size];
        for (int pings_index = 0; pings_index < pings_queue_size; pings_index++) {
            pings_queue[pings_index] = null;
            next_available_address[pings_index] = -1;
            remaining_addresses[pings_index] = 0;
        }
        
        //Initialize the subClients pool
        subClients_pool = new subClient[subClient_number];
        subClients_threads_pool = new Thread[subClient_number];
        for (int i = 0; i < subClient_number; i++) {
            subClients_pool[i] = new subClient();
            subClients_threads_pool[i] = new Thread(subClients_pool[i]);
            subClients_threads_pool[i].setName("SubClient "+ i);
        }
        
        resetErrorCount();
    }
    
    /**
     * This constructor in only for simulation and test purpose
     */
    public PingsClient() {
        m_client_info = new ClientInfo("", "");
        m_nick = new AtomicReference<String>("");
        m_source_geoip = new AtomicReference<GeoipInfo>();
        m_total_error_count = new AtomicInteger();
        m_total_submited_pings = new AtomicInteger();
        m_measurements_failed = new AtomicInteger();
        m_is_running = new AtomicBoolean(false);
        pings_queue = new ServerProxy.Pings[pings_queue_size];
    }
    
    public void run() {
        LOGGER.info("PingsClient starting.");
        resetErrorCount();
        m_is_running.set(true);
	this.start_time = System.currentTimeMillis();
        
        for (int pings_index = 0; pings_index < pings_queue_size; pings_index++) {
            sendResultsGetNewAddress(pings_index);
        }
        
        for (int pings_index = 0; pings_index < subClient_number; pings_index++) {
            subClients_threads_pool[pings_index].start();
        }
    }
    
    /**
     * Launched as a thread this class get new addresses using setNewAddress and
     * pings them (the same method report them to the server) .
     * Used as an Observable it provides an interface for the GUI.
     */
    class subClient extends Observable implements Runnable {
        //The class that actually do the pings
        private Prober prober;
        
        //The information on the current ping, they are mainly used by the GUI
        //observers
        protected InetAddress current_ping_dest = null;
        protected GeoipInfo current_dest_geoip = null;
        protected String current_ping_result = null;
        
        //The position of the current address/geoip in the pings_queue
        private int current_pings_index;
        private int current_address_index;
        
        //The number of consecutive errors that occurred in this thread.
        private int consecutive_error_count = 0;
        
        private boolean sucide;
        
	String problem_string = "";

        public subClient () {
            prober = new CompositeProber(m_client_info);
        }
        
        public void run() {
            while (!sucide) {
                try {            
                    //Get a new Address (and send the result of the previous one)
                    setNewAddress (this, current_ping_result,
                            current_pings_index, current_address_index);
                    if (sucide) break;
                    notifyObserversOfChange();
                    
                    //Ping this address                
                    prober.probe(current_ping_dest);
                    current_ping_result = prober.getLastProbe();
                    LOGGER.log(Level.INFO, "Ping result: {0}.",current_ping_result);
                    
                    //Extract relevant info for analysis
                    problem_string = addMeasurement(current_ping_result, current_ping_dest);

                    notifyObserversOfChange();
                    
                    //In case the thread is paused here
                    if (!m_is_running.get()) {
                        while (!m_is_running.get()) {
                            synchronized(pings_queue) {pings_queue.wait();}
                        }
                    }
                    
                    // Clear consecutive error count.
                    consecutive_error_count = 0;
                }
                catch (InterruptedException _) {break;}
                catch (Exception e) {
                    final int total_error_count = m_total_error_count.incrementAndGet();
                    consecutive_error_count++;
                    
                    LOGGER.log(Level.WARNING, "Exception caught in subClient thread.", e);
		    // Exponential backoff for consecutive errors
		    int wait_time = (int)Math.pow(2, consecutive_error_count);
                    wait_time = Math.min(wait_time, MAX_WAIT_TIME);

                    if (consecutive_error_count > MAX_ERROR_COUNT) {
                        LOGGER.log(Level.SEVERE, "Too many errors; stopping the subClient thread.");
			PingsClient.this.errorConnectingToServer(
			    "Too many problem happened." +
			    "Click try to retry or reload this page to possibly get a newer clients version."
								 );
                        break;
                    }
                    else {
                        try {
                            Thread.sleep(1000 * wait_time);
                        } catch (InterruptedException _) {break;}
                    }
                }
            }
        }
        
        /**
         * Set the parameters for a new ping, so that changes might be notified
         * to the GUI.
         * 
         * @param add the address of the new target to ping
         * @param geo the geoip of the new target to ping
         * @param pings_index the index of the current Pings in the pings_queue
         * @param address_index the index of the address in the current Pings
         */
        public void setCurrentAddressProperty (InetAddress add, GeoipInfo geo,
                int pings_index, int address_index) {
            current_ping_dest = add;
            current_dest_geoip = geo;
            current_ping_result = null;
            current_pings_index = pings_index;
            current_address_index = address_index;
        }
        
        /**
         *  Combines java.util.Observable's setChanged() and notifyObservers()
         *  to notify the GUI
         */
        protected void notifyObserversOfChange() {
            setChanged();
            notifyObservers();
        }
        
        public InetAddress getCurrentPingDest() {
            return current_ping_dest;
        }
        
        public GeoipInfo getCurrentDestGeoip() {
            return current_dest_geoip;
        }
        
        public String getCurrentPingResult() {
            return current_ping_result;
        }
        
        public GeoipInfo getSourceGeoip() {
            return PingsClient.this.getSourceGeoip();
        }

	public InetAddress getSourceAddress() {
	    return PingsClient.this.getSourceAddress();
	}

	public InetAddress getSourceExternalAddress() {
	    return PingsClient.this.getSourceExternalAddress();
	}

        public void destroy() {
            sucide = true;
        }
    
    }
    
    /**
     * Invoked by a subClient this method store its last result and give him a 
     * new address to ping.
     * <p>
     * If there are no more addresses available on the current Pings it calls 
     * sendResultGetNewAddress to get new ones and proceed to the next Pings in 
     * the queue. If there are no more address anywhere it waits to be wake up by
     * the thread receiving new address.
     * 
     */
    protected void setNewAddress (subClient sub, String last_result,
        int current_pings_index, int current_address_index) {
        
        synchronized(pings_queue) {
        
            //Store the result of the subClient in the corresponding Pings
            if (last_result != null) {
                ServerProxy.Pings pings = pings_queue[current_pings_index];
                pings.results[current_address_index] = last_result;
                
                remaining_addresses[current_pings_index] -= 1;
                //If it was the last address to get result from on the current Pings then
                //send the results an get a new address list
                if (remaining_addresses[current_pings_index] == 0 ) {
                    sendResultsGetNewAddress(current_pings_index);
                }
            }        
            
            //Get a new address to ping
            int pings_index = next_pings_with_addresses;
            
            //Loop thought pings_queue to find a free address and give it to sub
            // index_to_wait allow to decide if we have cycle or not
            int index_to_wait = next_pings_with_addresses;
            while (true) {
                
                ServerProxy.Pings local_pings = pings_queue[pings_index];
                
                if (next_available_address[pings_index] != -1 && local_pings != null) {
                    //If the Pings at address pings_index have some free address we
                    //take one
                    int address_index = next_available_address[pings_index];
                    next_available_address[pings_index]++;
                    
                    if (next_available_address[pings_index] == local_pings.addresses.length) {
                        next_available_address[pings_index] = -1;
                        next_pings_with_addresses = (next_pings_with_addresses +1) % pings_queue_size;
                    }
                    sub.setCurrentAddressProperty(local_pings.addresses[address_index],
                        local_pings.geoip_info[address_index],
                        pings_index,
                        address_index);
                    break;
                }
                
                //Otherwise we change next_pings_with_addresses and proceed to the 
                //next ping if we didn't loop 
                next_pings_with_addresses = (next_pings_with_addresses +1) % pings_queue_size;
                pings_index = (pings_index +1) % pings_queue_size;
                if (pings_index == index_to_wait && next_available_address[index_to_wait] == -1 ) {
                    try {
                        pings_queue.wait(100);
                    } catch (InterruptedException e) {
                        sub.destroy();
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Send the results of a Pings to the server and get a new list of addresses
     * to ping.
     * <p>
     * When the list is obtained it set the index and next_address parameters 
     * correctly then wake up the methods potentially waiting for new addresses lists.
     * <p>
     * Doesn't need to be synchronized as only one instance can execute at a time
     * over a given pings_index.
     * <p>
     * If the Pings at the current index is null then it get replaced by a fresh
     * Pings
     * It die after having submited/fetch new results once.
     */
    private class SendResultsGetNewAddress extends Thread {
        
        private int pings_index;
        private int consecutive_error_count = 0;
	private Random rand = new Random();
        
        public SendResultsGetNewAddress(int pings_index) {
            this.pings_index = pings_index;
            this.setName("SendResultsGetNewAddress " + pings_index);
        }
        
        public void run() {
            while (true) {
		String catched = "";
		String err_msg = "";
                try {
                    // Submit results to server.
                    if (pings_queue[pings_index] != null) {
                        LOGGER.info("Submitting results to server for pings_index=" +
				    pings_index);
                        m_server_proxy.submitResults(m_client_info,
						     pings_queue[pings_index]);

			// Save the number of submitted ip in a cookie:
			synchronized(m_total_submited_pings) {
			    int n = m_total_submited_pings.addAndGet(pings_queue[pings_index].addresses.length);
			    setCookieNbPings(n);
			}

			//Wait if needed
			//elapsed_time and wait_time are in mili-seconds.
			long elapsed_time = System.currentTimeMillis() - pings_queue[pings_index].time_fetched;
			long min_round_time = Math.max(MIN_ROUND_TIME, pings_queue[pings_index].min_round_time);
			min_round_time += 0.1 * this.rand.nextInt((int)min_round_time);
			long wait_time = (min_round_time * pings_queue_size * 1000) - elapsed_time ;
			wait_time = (long)(wait_time * WAIT_TIME_BOOST);

			// In case the submit succeed, but the get fail, if we don't null it, it will get resubmitted.
			pings_queue[pings_index] = null;

			if (wait_time > 0){
			    LOGGER.info("\nWaiting before the next round for pings_index=" + pings_index +
					" elapsed_time(ms)=" + elapsed_time +
					" min_round_time(s)=" + min_round_time +
					" pings_queue_size=" + pings_queue_size +
					" wait_time(ms)=" + wait_time);
			    try {
				Thread.sleep(wait_time);
			    } catch (InterruptedException e1) {}
			}
			if(consecutive_error_count >= 6)
			    PingsClient.this.displayProblem("");
		    }

                    // Get source geoip data and list of addresses to ping.
                    pings_queue[pings_index] = m_server_proxy.getPings(m_client_info);
                    m_source_geoip.set(m_client_info.getGeoipInfo());
                    
                    //Setting the remaining_address and next
                    remaining_addresses[pings_index] = pings_queue[pings_index].addresses.length;
                    next_available_address[pings_index] = 0;
                    
                    //Wake up threads that might wait for new addresses
                    synchronized(pings_queue){
                            pings_queue.notify();
		    }
                    
		    if(consecutive_error_count >= 6)
			PingsClient.this.displayProblem("");
		    //consecutive_error_count = 0; useless as a new thread will be created for the next fetch.
                    break;
                }
                catch (UnknownHostException _) {
		    catched = "UnknownHostException";
		    err_msg =  "A problem happened while trying to connect to the server. The most probable causes are :\n" +
			"_ you are not connected to the internet\n" +
			"_ you have a DNS problem\n" +
			"_ the server you are trying to join is not correctly configured";
                }
                catch (ConnectException e) {
		    catched = "ConnectException";
		    err_msg = "A problem happened while trying to connect to the server. The most probable causes are :\n" +
			"_ a firewall is blocking the connection\n" +
			"_ you lost your connection to internet\n"+
			"_ the server you are trying to join reject the connection";
                }
                catch(SocketTimeoutException e) {
		    catched = "SocketTimeoutException";
		    err_msg = "A problem happened while trying to connect to the server . The most probable causes are :\n" +
			"_ a firewall is blocking the connection\n" +
			"_ the server is overloaded\n" +
			"_ the connection is taking too long";
                }
                catch (IOException e) {
		    catched = "IOException";
		    err_msg =  "Exception caught in PingsClient thread " + pings_index +
			" when contacting the server.\n" + e;
                } // catch IOException
		if (catched != ""){
                    consecutive_error_count++;
                    int wait_time = (int)Math.pow(2, consecutive_error_count);
		    wait_time = Math.min(wait_time, MAX_WAIT_TIME);
		    err_msg += "\n This is the " + consecutive_error_count +
			       " consecutive error count. We will wait " + wait_time +
			       " seconds before recontacting it again.";
                    LOGGER.log(Level.WARNING, err_msg);

                    if (consecutive_error_count > MAX_ERROR_COUNT) {
                        LOGGER.log(Level.SEVERE,
				   "Too many errors; stopping PingsClient thread " + pings_index);
			PingsClient.this.errorConnectingToServer(
			    "Too many problem happened while trying to connect to the server." +
			    "Click try to retry or reload this page to possibly get a newer clients version."
								 );
                        break;
                    }
                    else {
			//If we wait for more then 1 minutes, display the error messages.
			//subClient thread will remove this when fixed.
			if (wait_time >= 60)
			    PingsClient.this.displayProblem(err_msg);

                        // Exponential backoff for consecutive errors. Also
                        // avoid thread busy-loop if IOException keeps getting
                        // raised in call to getPings.
                        try {
                            Thread.sleep(1000 * wait_time);
                        } catch (InterruptedException e1) {
			    LOGGER.log(Level.SEVERE,
				       "SendResultsGetNewAddress got interrupted while waiting after an errors. pings_index=" +
				       pings_index, e1);
			    PingsClient.this.errorConnectingToServer(
			        "Interupted while a thread was sleeping.");
                            break;
                        }
                    }
		}
            }
        }
    }
    
    /**
     * A function that launch the thread class sendResultsGetNewAddress
     * @see SendResultsGetNewAddress
     */
    private void sendResultsGetNewAddress(int pings_index) {
        new SendResultsGetNewAddress(pings_index).start();
    }
    
    public void errorConnectingToServer(String reason) {
        this.connect_error = true;
        this.error_reason = reason;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                PingsClient.this.setChanged();
                notifyObservers();
            }
        });
    }

    public void displayProblem(String prob){
	this.error_reason = prob;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                PingsClient.this.setChanged();
                notifyObservers();
            }
        });
    }
    
    public subClient[] getSubClientsPoolCopy() {
        subClient[] copy = new subClient[subClient_number];
        for (int i = 0; i < subClient_number; i++) {
            copy[i] = subClients_pool[i];
        }
        return copy;
    }
    
    public void setNickname(String nick) {
        m_nick.set(nick);
        m_client_info.setNickname(nick);
	this.setCookie();
    }
    
    public String getNickname() {
        return m_nick.get();
    }
    
    public GeoipInfo getSourceGeoip() {
        return m_source_geoip.get();
    }
    
    public InetAddress getSourceAddress() {
	return m_client_info.getAddress();
    }

    public InetAddress getSourceExternalAddress() {
	return m_client_info.getExternalAddress();
    }

    public int getErrorCount() {
        return m_total_error_count.get();
    }

    public int getSubmitedPingsCount() {
        return m_total_submited_pings.get();
    }
    
    private void resetErrorCount() {
        m_total_error_count.set(0);
    }
    
    public void pause() {
        synchronized(pings_queue) {
           m_is_running.set(false);
        }
    }
    
    public void resume() {
	this.setCookie();
        m_is_running.set(true);
        for (int i = 0; i < subClient_number; i++) {
            synchronized(pings_queue) {
                pings_queue.notify();
            }
        }
    }
    
    public void destroy() {
        for (int i = 0; i < subClient_number; i++) {
            subClients_threads_pool[i].interrupt();
        }
        this.m_is_running.set(false);
    }
    public boolean isRunning() {
        return m_is_running.get();
    }

    // Return a string of problem detected.
    // If we have more then 15 pings failed and none succeded, we return a string problem.
    // Otherwise, we return an empty string.
    public String addMeasurement(String current_ping_result, InetAddress current_ping_dest) {
	if(shown_analysis)
	    return "";
        String[] icmp_result = current_ping_result.split(";")[0].split(" ");
        String last = icmp_result[icmp_result.length - 1];
        boolean ok = last.substring(last.length() - 2).equals("ms");
	float value = -999f;
	String ret = "";
	if(!ok){
	    LOGGER.log(Level.INFO, "Bad measurements: " + current_ping_result);
	}else{
	    last = last.substring(0, last.length() - 2);
	    value = Float.parseFloat(last);
	    ok &= value >= 10 && value < 1900;
	}
	synchronized(measurements) {
	    if(!ok && num_measurements == 0){
		int n_fail = m_measurements_failed.incrementAndGet();
		if(n_fail >= 15){
		    ret = "All pings failed. Are pings blocked by a firewall? Your institution's firewall?";
		}
	    }

	    if (ok) {
		String measurement = current_ping_dest.getHostAddress() + "," + last;
		if (num_measurements > 0) measurements += "-";
		measurements += measurement;
		num_measurements++;
		LOGGER.log(Level.INFO, "Measurement #" + Integer.toString(num_measurements) + ": " + measurement);
		// If the applet is started for more then 15 minutes and we have at least 5 ip,
		// request the repport.
		if (num_measurements >= 5 && (System.currentTimeMillis() - start_time) > 1000*60*15){ // 15 minutes
		    LOGGER.log(Level.INFO, "\n\nTry to print the feedback");
		    LOGGER.log(Level.INFO, "javascript:get_analysis('" + measurements + "')");
		    JSObject.getWindow(applet).eval("javascript:get_analysis('" + measurements + "')");
		    shown_analysis = true;
		}
	    }
	}
	return ret;
    }
        
    private void setOneCookie(String name, String value){
	System.out.println("setCookie " + name + " " + value);
	// The try..catch work around some browser bugs. This could make some cookie not saved
	// but having something working without cookie is better then nothing working!
	try{
	    JSObject.getWindow(this.applet).eval("javascript:set_cookie('" + name +
						 "', '" + value + "')");
	}catch(NullPointerException e){
	    LOGGER.log(Level.INFO, "Cannot set cookie due to NullPointerException");
	}

    }
    public void setCookie() {
	setOneCookie(m_cookie_name + "_uuid", this.m_client_info.m_uuid);
	setOneCookie(m_cookie_name + "_nickname", this.m_client_info.getNickname());
    }

    public void setCookieNbPings(int n) {
	setOneCookie(m_cookie_name + "_nb_pings", Integer.toString(n));
    }

    /*
     * This make sure we can put into a cookie the following string by removing caractere that aren't safe.
     */
    public static String sanitize_string(String str) {
	//{a-zA-Z0-9 .@}
	if (str != null)
	    str = str.replaceAll("[^a-zA-Z0-9 .@]", "");
	return str;
    }

    public static void main(String args[]) throws InterruptedException {
	String hostname = "iconnect.iro.umontreal.ca";
        int port = 6543;
	boolean null_prober = false;
	boolean icmp_prober = false;
	int nb_clients = 1;

	//Parse input
	for (int i = 0 ; i < args.length ; i++) {
	    if (args[i].equals("--null")) {
		null_prober = true;
	    } else if (args[i].equals("--icmp")) {
		icmp_prober = true;
	    } else if (args[i].startsWith("-n=")) {
		try {
		    nb_clients = Integer.parseInt(args[i].substring(3));
		}
		catch (NumberFormatException e) {
		    System.err.println("Error: port argument must be an integer.");
		    System.exit(2);
		}

	    } else if ((args.length - i) > 2) {
		System.err.println("Usage: PingsClient [-n=N] [--{null,icmp}] [hostname [port]]");
		System.exit(1);
	    } else {
		hostname = args[i];
		if ((i + 1) < args.length){
		    try {
			port = Integer.parseInt(args[i + 1]);
		    }
		    catch (NumberFormatException e) {
			System.err.println("Error: port argument must be an integer.");
			System.exit(2);
		    }
		}
		break;
	    }
	}
	System.out.println("Hostname: " + hostname);
	System.out.println("Port:" + port);
	System.out.println("Nb clients:" + nb_clients);
	if (null_prober && icmp_prober) {
		System.err.println("Can only use one of --null and --icmp parameter");
		System.exit(1);
	}
        // 1 clients 4587M virtual 34M real
	// 10 clients 6497M virtual 60M real
	// 20 clients 6645M virtual 99M real
	// 20 clients 6800M virtual 146M real
	// 50 clients 6993M virtual 124M real
	// 50 clients Out of memory on 8G computers. even with ulimit -v unlimited

        PingsClient[] clients = new PingsClient[nb_clients];
	for (int i = 0 ; i < nb_clients ; i++) {
	    clients[i] = new PingsClient(hostname, port, null, "", "", 0);
	    //Do only the ICMP ping as this is faster
	    for(int j = 0 ; j < clients[i].subClients_pool.length ; j++){
		PingsClient c = clients[i];
		if (null_prober)
		    c.subClients_pool[j].prober = new NullProber();
		if (icmp_prober)
		    c.subClients_pool[j].prober = new IcmpPinger(c.m_client_info);
	    }
	    clients[i].setNickname("yoda");
	    clients[i].run();
	}
        if (false)
	    for (int idx_client = 0 ; idx_client < nb_clients ; idx_client++) {
		PingsClient client = clients[idx_client];
		subClient[] copy = client.getSubClientsPoolCopy();
		for (int i = 0 ; i < copy.length ; i++ ) {
		    copy[i].addObserver(new Observer() {
			    public void update(Observable o, Object arg) {
				subClient client = (subClient)o;

				InetAddress current_ping_dest = client.getCurrentPingDest();
				if (current_ping_dest != null)
				    System.out.printf("Current ping dest: %s\n",
						      current_ping_dest.toString());

				GeoipInfo dest_geoip_info = client.getCurrentDestGeoip();
				if (dest_geoip_info != null) {
				    System.out.printf("Long: %f; lat: %f; city: %s; country: %s\n",
						      dest_geoip_info.longitude,
						      dest_geoip_info.latitude,
						      dest_geoip_info.city,
						      dest_geoip_info.country);
				}
			    }
			});
		}
	    }
        
    }
}
