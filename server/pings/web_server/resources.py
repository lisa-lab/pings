import os, logging, memcache, ipaddr, random, pygeoip
from gevent_zeromq import zmq

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
zmq_publish_leaderboards_socket = None

def init_rankings_zmq(incr_scores_url, publish_leaderboards_url):
    global zmq_incr_score_socket, zmq_publish_leaderboards_socket

    zmq_incr_score_socket = get_zmq_context().socket(zmq.PUSH)
    zmq_incr_score_socket.connect(incr_scores_url)

    zmq_publish_leaderboards_socket = get_zmq_context().socket(zmq.SUB)
    zmq_publish_leaderboards_socket.connect(publish_leaderboards_url)
    # Subscribe to everything.
    zmq_publish_leaderboards_socket.setsockopt(zmq.SUBSCRIBE, '')

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

def get_token():
    """Gets a security token. (A random base64 ascii string.)"""
    token = os.urandom(16).encode("base64")[:22]
    token_mc.set(token, True, token_exptime)
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
    return token_mc.get(token.encode('ascii')) is not None


def get_pings(num_addresses=3):
    """Returns a list (of length 'num_addresses') of IP addresses to be
    pinged."""
    ip_addresses = []
    num_tries = 0

    while len(ip_addresses) < num_addresses and num_tries < 100:
        num_tries += 1
        # Create a random IPv4 address. Exclude 0.0.0.0 and 255.255.255.255.
        ip = ipaddr.IPv4Address(random.randint(1, 2**32-2))

        if not (ip.is_link_local or ip.is_loopback or ip.is_multicast or
                ip.is_private or ip.is_reserved or ip.is_unspecified):
            ip_addresses.append(str(ip))

    return ip_addresses


def get_geoip_data(ip_addresses):
    """When passed a list of IP addresses (as strings), returns a list of
    dicts containing GeoIP data, one for each IP address passed."""
    results = []
    for address in ip_addresses:
        try:
            geoip_data = geoip.record_by_addr(address)

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
    # Placeholder code for now. We will do something a bit fancier.
    points = len(results) * 100

    # Send them to server.
    zmq_incr_score_socket.send_json({'userid': userid,
                                     'score_increment': points})

def get_leaderboards():
    """Retrieves the latest leaderboards top scores."""
    top_scores = zmq_publish_leaderboards_socket.recv_json(zmq.NOBLOCK)
    # TODO Handle case where no message is present.
    logger.debug('Current leaderboards top score: %s', top_scores)
    return top_scores


def store_results(results):
    """Stores ping results. The results are sent via Zeromq to a
    storage_server instance."""
    logger.debug('Ping results: %s', results)
    zmq_send_results_socket.send_json(results)
