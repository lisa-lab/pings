import cPickle
import logging
import os
import time

#We need pylibmc, not memcache or ultramemcache or umemcache
#We call token_mc.delete and only pylibmc return an error when the key
#was not there.  We rely on this behavior
import pylibmc as memcache
import ipaddr
import random
import pygeoip
from gevent_zeromq import zmq
from pings.web_server import leaderboards

logger = logging.getLogger(__name__)

# Warning: the use of a module-level variable for the zeromq sockets will
# NOT work if running this server with multiple threads. (You can't share
# zeromq sockets between threads.) This is not a problem because we are
# using gevent, which uses greenlets, not threads. But if ever changing to
# a WSGI server that uses threads, you will need to ensure that the zeromq
# socket is not accessed from threads other than the one which created it.

#
# Initialization
#

# Memcache instance for the security tokens.
token_mc = None
# Expiration time for the tokens in said memcache. If a client takes
# longer than this number of seconds to ping the list of addresses it was
# assigned and send back its reply, said reply will be refused when
# received.
token_exptime = None


def init_token_memcache(memcache_addresses, exptime):
    global token_mc, token_exptime
    token_mc = memcache.Client(memcache_addresses)
    token_exptime = exptime

#
# Zeromq resources
#

_zmq_context = None


def get_zmq_context():
    """Use this to retrieve the Zeromq context. It is created on demand if
    needed."""
    global _zmq_context
    if _zmq_context is None:
        _zmq_context = zmq.Context()
    return _zmq_context


# connection to the storage_server.
zmq_send_results_socket = None


def init_storage_zmq(zmq_urls):
    global zmq_send_results_socket
    zmq_send_results_socket = get_zmq_context().socket(zmq.PUSH)

    for url in zmq_urls:
        zmq_send_results_socket.connect(url)


# Zeromq sockets for leaderboards server
zmq_incr_score_socket = None


def init_rankings_zmq(incr_scores_url, publish_leaderboards_url):
    global zmq_incr_score_socket, leaderboards_proxy

    zmq_incr_score_socket = get_zmq_context().socket(zmq.PUSH)
    zmq_incr_score_socket.connect(incr_scores_url)

    leaderboards.init(get_zmq_context(), publish_leaderboards_url)

#
# GeoIP database
#

# GeoIP object
geoip = None


def init_geoip():
    """Initialize the GeoIP component. We expect the GeoLiteCity.dat file
    to be located in the directory containing the 'pings' Python package."""
    global geoip
    geoip = pygeoip.GeoIP(os.path.join(os.path.dirname(__file__),
                                       '..', '..', 'GeoLiteCity.dat'),
                          pygeoip.MEMORY_CACHE)


#
# Pyramid ressource class.
#

class Root(object):
    def __init__(self, request):
        self.request = request


#
# Functions that implement the low-level, core actions of the pings server.
#

# This is the total number of ip we try to pings
# The number of other clients is at max half that
#  none if less then 9 clients
#  and less then len(last_clients) * probability_to_ping
_num_addresses = 15


def init_web_service(num_addresses):
    global _num_addresses
    _num_addresses = num_addresses


def get_token():
    """Gets a security token. (A random base64 ascii string.)"""
    token = os.urandom(16).encode("base64")[:22]
    assert token_mc.set(token, True, token_exptime)
    return token


def check_token(token):
    """Checks that a given security token is still valid."""
    if token is None:
        return False

    # Lookup token in memcache.
    #
    # Memcache doesn't like getting Unicode for keys. And even though
    # get_token() returns a str, by the time it's sent to the client and
    # back, it will likely have been converted to Unicode (happening now,
    # at least). Convert the token back to a str here to handle this
    # problem in a localized way. Since the token is currently a base64
    # string, ascii is okay as an encoding. (If fed a token that can't be
    # converted to ASCII, Pyramid will convert the exception to a 500 error.)
    return token_mc.delete(token.encode('ascii')) is not None

# The address of the server that all clients should ping
always_up_addresses = ["173.194.73.104", "183.60.136.45", "195.22.144.60"]
# The probability that we ping an reference ip and used for the prob to
# ping past client ip.
probability_to_ping = 0.1


class active_queue:
    """Maintain a queue of a given maximum size s registering the last s
    elements that were added. We keep only the last entry of duplicates. Can be
    resized. Non thread-safe. Can remove older entry then a
    specified timeout  when asked."""
    #A better implementation would use doubly linked list
    def __init__(self, size, timeout=60 * 60):
        """
        :param timeout: the timeout of the element in seconds
                        default 1h.
        """
        self.by_address = {}  # ip -> time
        self.list = []  # in the order of addition.
        self.max_size = size
        self.timeout = timeout

    def add(self, elem, now):
        if elem in self.by_address:
            self.list.remove(elem)
            self.list.append(elem)
            self.by_address[elem] = now
        else:
            self.by_address[elem] = now
            if len(self.list) >= self.max_size:
                to_remove = self.list.pop(0)
                self.by_address.pop(to_remove)
            self.list.append(elem)

    def __len__(self):
        return len(self.list)

    def resize(self, new_size):
        if new_size < self.size:
            # Remove the begigging of the list from self.by_address
            for elem in self.list[:-new_size]:
                del self.by_address[elem]
            # Keep the end of the list
            self.list = self.list[-new_size:]
        self.size = new_size

    def get_random(self):
        return random.choice(self.list)

    def remove_old(self, now):
        """
        Remove ip older then self.timeout
        """
        nb_removed = 0
        while len(self.list) > 0:
            if (self.by_address[self.list[0]] + self.timeout) < now:
                del self.by_address[self.list[0]]
                self.list.pop(0)
                nb_removed += 1
            else:
                break
        return nb_removed

#Should larger than the current number of client connected but decreases
#efficiency if is too large
queue_size = 100
last_clients = active_queue(queue_size)

# Init the list of know ip that are pignable
try:
    f = open("addresses.pickle")
    d = cPickle.load(f)
    f.close()
    known_pignable = active_queue(len(d))
    now = time.time()
    for ip in d:
        known_pignable.add(ip, now)
    del d, f
except Exception, e:
    print ("We got an exception while reading the list of valid ip."
           " Did you download and unzip the file 'addresses.pickle.zip'?")
    print e
    print "We will init an empty list"

    known_pignable = active_queue(1000)


def get_int_from_ip(ip):
    int_parts = [int(str_) for str_ in ip.split('.')]
    return int_parts[3] + 256 * (int_parts[2] + 256 *
                                 (int_parts[1] + 256 * int_parts[0]))


def get_pings(client_addr):
    """Returns a list (of length 'num_addresses') of IP addresses to be
    pinged."""
    ip_addresses = []

    # Add some reference address to ping.
    for ip in always_up_addresses:
        if random.random() < probability_to_ping:
            ip_addresses.append(str(ip))

    # Pings other clients
    client_ip = get_int_from_ip(client_addr)
    # Do not ping clients when the cache is too low
    # Otherwise, this could cause a DDoS attack.
    if len(last_clients) > 9:
        # Add up to half of _num_addresses from other peers
        nb_cli_ip = _num_addresses / 3
        num_tries = 0
        # If too few past clients, lower the prob to ping them.
        max_tries = min(_num_addresses,
                        len(last_clients) * probability_to_ping)
        while len(ip_addresses) < nb_cli_ip and num_tries < max_tries:
            num_tries += 1
            try:
                random_ip = last_clients.get_random()
                # Don't ping ourself and don't ping twice the same ip
                if random_ip != client_ip and random_ip not in ip_addresses:
                    ip_addresses.append(random_ip)
            except Exception:
                pass
    ip_addresses = [str(ipaddr.IPv4Address(ip)) for ip in ip_addresses]

    # Get ip from the list of know good ip. If we know enough client,
    # 1/3 ip are clients, 1/3 are known pignable and 1/3 are random.
    n = (_num_addresses - len(ip_addresses)) / 2
    if len(known_pignable) > (2 * n):
        for i in range(n):
            random_ip = known_pignable.get_random()
            # Don't ping ourself and don't ping twice the same ip
            if random_ip != client_ip and random_ip not in ip_addresses:
                ip_addresses.append(random_ip)

    # Add current client to the list of pingable clients.
    if (client_ip != 2130706433 and  # don't accept 127.0.0.1
        not (2851995648 <= client_ip <= 28520611830) or  # is_link_local
        (2130706432 <= client_ip <= 2147483647) or  # is_loopback
        (3758096384 <= client_ip <= 4026531839) or  # is_multicast
        ((167772160 <= client_ip <= 184549375) or
         (2886729728 <= client_ip <= 2887778303) or
         (3232235520 <= client_ip <= 3232301055)) or  # is_private
        (4026531840 <= client_ip <= 4294967295)):
        now = time.time()
        last_clients.add(client_ip, now)

    # The num_tries < max_tries part of the loop is to guarantee that
    # this function executes in a bounded time.
    num_tries = 0
    max_tries = _num_addresses * 6
    while len(ip_addresses) < _num_addresses and num_tries < max_tries:
        num_tries += 1
        # Create a random IPv4 address. Exclude 0.0.0.0 and 255.255.255.255.
        ip = ipaddr.IPv4Address(random.randint(1, 2 ** 32 - 2))

        # Add address if it is a valid global IP address. (Addresses that
        # start with a leading first byte of 0 are also not valid
        # destination addresses, so filter them out too.)
#        if not (ip.is_link_local or ip.is_loopback or ip.is_multicast or
#                ip.is_private or ip.is_reserved or ip.is_unspecified
#                or ip.packed[0] == '\x00'):

        if not ((2851995648 <= ip._ip <= 28520611830) or  # is_link_local
                (2130706432 <= ip._ip <= 2147483647) or  # is_loopback
                (3758096384 <= ip._ip <= 4026531839) or  # is_multicast
                ((167772160 <= ip._ip <= 184549375) or
                 (2886729728 <= ip._ip <= 2887778303) or
                 (3232235520 <= ip._ip <= 3232301055)) or  # is_private
                (4026531840 <= ip._ip <= 4294967295) or  # is_reserved
                (ip._ip == 0) or  # is_unspecified
                ip.packed[0] == '\x00' or
                # 143.93.75.67 This ip was used by a virus. People
                # that connection to it to detect infested virus. We
                # don't use it to don't have false alert.
                ip._ip == 2405256003):
            ip_addresses.append(str(ip))

    return ip_addresses


def get_geoip_data(ip_addresses):
    """When passed a list of IP addresses (as strings), returns a list of
    dicts containing GeoIP data, one for each IP address passed."""
    results = []
    for address in ip_addresses:
        try:
            geoip_data = geoip.record_by_addr(address)

# pygeoip 0.2.2 need the following bug fix
#         0.2.3 crash when we load the data file (DON'T USE)
#         0.2.4 without the fix the server err.
#               With the fix, we never find the info. (DON'T USE)
#         0.2.5 need the fix or the client crash.
#               With the fix 210 r/s (faster then 0.2.2)
            if geoip_data is not None:
                # The pygeoip library doesn't use Unicode for string values
                # and returns raw Latin-1 instead (at least for the free
                # GeoLiteCity.dat data). This makes other module (like json)
                # that expect Unicode to be used for non-ASCII characters
                # extremely unhappy. The relevant bug report is here:
                # https://github.com/appliedsec/pygeoip/issues/1
                #
                # As a workaround, convert all string values in returned
                # dict from Latin-1 to Unicode.
                for key, value in geoip_data.iteritems():
                    if isinstance(value, str):
                        geoip_data[key] = value.decode('latin-1')

        except Exception, e:
            logger.exception('Exception during lookup of geoip data ' +
                             'for IP address "%s".', address)
            geoip_data = None

        results.append(geoip_data)

    logger.debug('GeoIP results: %s', results)
    return results


def update_leaderboards(userid, results):
    """Computes the number of points which the ping results is worth, and
    sends a request to the leaderboards server to add that number to
    the leaderboards for the given userid."""
    # Compute how many points the given results are worth.
    points = len(results)

    # Send them to server.
    logger.debug('Adding %d to score of user "%s"', points, userid)
    zmq_incr_score_socket.send_json({'userid': userid,
                                     'score_increment': points})


def get_leaderboards():
    """Retrieves the latest leaderboards top scores."""
    top_scores = leaderboards.get_latest()
    logger.debug('Current leaderboards top score: %s', top_scores)
    return top_scores


def store_results(results):
    """Stores ping results. The results are sent via Zeromq to a
    storage_server instance."""
    logger.debug('Storing ping results: %s', results)
    zmq_send_results_socket.send_json(results)


def store_known_pignable(results):
    # Store in the known_pignable variable:
    now = time.time()
    try:
        for res in results:
            if res.startswith("ICMP"):
                sp = res.split()
                t = sp[4]
                if t.endswith("ms;"):
                    t = int(t[:-3])
                    if t > 10 and t < 2000:
                        known_pignable.add(sp[1], now)
    except Exception, e:
        print "Skip exception", e

if __name__ == "__main__":
    if True:  # False:
        nb_iter = 100000
        # Speed test get_pings
        t0 = time.time()
        for i in range(nb_iter):
            #        get_pings("132.204.25." + str(i%255))
            get_pings("132.204.25.12")
        t1 = time.time()
        print "Time per request (ms):", (t1 - t0) / float(nb_iter) * 1000
    else:
        nb_iter = 3000
        # Speed test
        init_geoip()
        pings = get_pings("132.204.25.12")
        t0 = time.time()
        for i in range(nb_iter):
            get_geoip_data(pings)
        t1 = time.time()
        print "Time per request (ms):", (t1 - t0) / float(nb_iter) * 1000
