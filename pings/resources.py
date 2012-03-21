import os, logging, memcache, ipaddr, random
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

# Zeromq for the connection to the storage_server.
zmq_context = None
zmq_send_results_socket = None

def init_storage_zmq(zmq_urls):
    global zmq_context, zmq_send_results_socket
    zmq_context = zmq.Context()
    zmq_send_results_socket = zmq_context.socket(zmq.PUSH)

    for url in zmq_urls:
        zmq_send_results_socket.connect(url)

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
    """Get a security token."""
    token = os.urandom(16).encode("base64")[:22]
    token_mc.set(token, True, token_exptime)
    return token

def check_token(token):
    """Checks that a given security token is still valid."""
    return True
    if token is None:
        return False
    return token_mc.get(token) is not None

def get_pings():
    """Returns a list of IP addresses to be pinged. Currently we return up
    to 15 random IPv4 addresses."""
    addresses = []
    num_tries = 0

    while len(addresses) < 15 and num_tries < 100:
        num_tries += 1
        ip = ipaddr.IPv4Address(random.randint(1, 2**32-2))

        if not (ip.is_link_local or ip.is_loopback or ip.is_multicast or
                ip.is_private or ip.is_reserved or ip.is_unspecified):
            addresses.append(str(ip))

    return addresses

def store_results(results):
    """Stores ping results. The results are sent via Zeromq to a
    storage_server instance."""
    logger.debug('Ping results: %s', results)
    zmq_send_results_socket.send_json(results)
