"""Module to access leaderboards results."""

# We can't have web pages, etc. retrieve the leaderboards results directly
# from the Zeromq SUB socket because said socket will return each result it
# receives only once. We want to return the latest leaderboards results
# always. Hence the need for this "adapter" module.


import threading
from gevent_zeromq import zmq

# Variable used to store the latest leaderboards results. The _lb_thread_fn
# assigns to this variable, while get_latest returns its value. This is
# safe, as assignment to a Python variable is atomic with respect to Python.
_latest_leaderboards = None

_init_done = False

def init(zmq_context, zmq_leaderboards_pubsub_url):
    """Initializes the internal thread that handles the leaderboard server.
    Must be called once per process."""

    global _init_done

    # Make sure people don't call us more than once. Given that we spawn a
    # thread below, the results could be... interesting.
    if _init_done:
        return

    # Start thread that monitors the leaderboards pubsub channel.
    thread = threading.Thread(target=_lb_thread_fn,
                              args=(zmq_context, zmq_leaderboards_pubsub_url,))
    thread.daemon = True
    thread.start()

    _init_done = True


def _lb_thread_fn(zmq_context, zmq_leaderboards_sub_url):
    """Function running in a separate thread. Subscribes to the
    leaderboards server updates and keeps only the latest one."""

    global _latest_leaderboards

    # Connection to the leaderboards server.
    zmq_lb_socket = zmq_context.socket(zmq.SUB)
    zmq_lb_socket.connect(zmq_leaderboards_sub_url)
    # Subscribe to everything.
    zmq_lb_socket.setsockopt(zmq.SUBSCRIBE, '')

    while True:
        _latest_leaderboards = zmq_lb_socket.recv_json()


def get_latest():
    """Returns the latest value of the leaderboards, or None if we have not
    seen a leaderboards update since we started running."""
    return _latest_leaderboards


__all__ = ['init', 'get_latest']
