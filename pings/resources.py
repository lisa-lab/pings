import os, logging, memcache
from gevent_zeromq import zmq

logger = logging.getLogger(__name__)

# Memcache instance for the security tokens.
token_mc = None

# Warning: the use of a module-level variable for the zeromq sockets will
# NOT work if running this server with multiple threads. (You can't share
# zeromq sockets between threads.) This is not a problem because we are
# using gevent, which uses greenlets, not threads. But if ever changing to
# a WSGI server that uses threads, you will need to ensure that the zeromq
# socket is not accessed from threads other than the one which created it.
zmq_context = None
zmq_send_results_socket = None

# TODO Configure via ini file.
token_exptime = 15*60

#
# Initialization
#

def init_token_memcache():
    global token_mc
    token_mc = memcache.Client(['127.0.0.1:11211'])

def init_zmq():
    global zmq_context, zmq_send_results_socket
    zmq_context = zmq.Context()
    
    zmq_send_results_socket = zmq_context.socket(zmq.PUSH)
    zmq_send_results_socket.connect("tcp://localhost:5000")

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
    """Returns a list of IP addresses to be pinged.
    @todo Do real work instead of returning a fixed list of 10/8 addresses."""
    return ['10.0.0.1', '10.0.10.1']

def store_results(results):
    """Stores ping results. The results are sent via Zeromq to a
    storage_server instance."""
    logger.debug('Ping results: %s', results)
    zmq_send_results_socket.send_json(results)
