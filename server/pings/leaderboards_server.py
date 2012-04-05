"""Leaderboards server. Maintains multiple leaderboards. Requests to update
the score for a given userid come on over Zeromq. We also broadcast
periodically the top scores for each leaderboard over Zeromq.

The periodic Zeromq broadcast of leaderboard scores keeps the load on this
server and its backend from leaderboard top scores requests constant,
instead of scaling linearly with the number of connections to the Pings
project web page, if we were to have said Pings web page ask for the top
leaderboard scores each time before displaying itself. More generally, the
use of Zeromq between the leaderboards server and the main Pings requests
server allows us to decouple these two parts and have the main server
continue working at serving pings requests and results even if the
leaderboards server has trouble keeping up under load."""

import sys, json, zmq, ConfigParser, logging, errno
from logging.config import fileConfig


class SimpleLeaderboard:
    """Atrociously inefficient, but very simple implementation of
    leaderboard. Not means to be used in production!"""

    def __init__(self):
        self.leaderboard = {}

    def incr_score(self, userid, score_increment):
        """Increment the score for a given userid."""
        previous_score = self.leaderboard.setdefault(userid, 0)
        self.leaderboard[userid] = previous_score + score_increment

    def get_top_scores(self, num_top_entries):
        """Returns a list of (userid, score) tuples for the top entries in
        the leaderboard, sorted by decreasing score. The number of entries
        returned will be less than the number requested in num_top_entries
        if there are fewer entries in the leaderboard than the number
        requested."""
        all_scores = sorted(self.leaderboard.iteritems(),
                            key=lambda items: items[1])
        num_entries_to_return = min(num_top_entries, len(all_scores))
        return list(reversed(all_scores[-num_entries_to_return:]))
    

def main():
    # Parse command line.
    if len(sys.argv) != 2:
        print >>sys.stderr, "Usage: leaderboards_server config.ini"
        sys.exit(1)

    config_filename = sys.argv[1]

    # First configure logging.
    fileConfig(config_filename)
    global logger
    logger = logging.getLogger(__name__)

    # Load config file.
    config_parser = ConfigParser.SafeConfigParser()
    config_parser.read(config_filename)
    config_section = 'leaderboards_server'

    incr_scores_port = config_parser.getint(config_section, 'incr_scores_port')
    publish_leaderboards_port = config_parser.getint(config_section, 'publish_leaderboards_port')
    localhost_only = config_parser.getboolean(config_section, 'localhost_only')
    if incr_scores_port == publish_leaderboards_port:
        print >>sys.stderr, ("Error: the 'incr_scores_port and publish_leaderboards_port " +
                             "cannot have the same value!")
        sys.exit(2)

    # Create Zeromq sockets.
    zmq_context = zmq.Context()
    
    zmq_incr_score_socket = zmq_context.socket(zmq.PULL)
    if localhost_only:
        zmq_incr_score_socket.bind('tcp://127.0.0.1:%d' % incr_scores_port)
    else:
        zmq_incr_score_socket.bind('tcp://*:%d' % incr_scores_port)

    zmq_publish_leaderboards_socket = zmq_context.socket(zmq.PUB)
    if localhost_only:
        zmq_publish_leaderboards_socket.bind('tcp://127.0.0.1:%d' % publish_leaderboards_port)
    else:
        zmq_publish_leaderboards_socket.bind('tcp://*:%d' % publish_leaderboards_port)

    # Do the work
    leaderboard = SimpleLeaderboard()
    while True:
        if zmq_incr_score_socket.poll(1000) != 0:
            while True:
                try:
                    msg = zmq_incr_score_socket.recv_json(zmq.NOBLOCK)
                    leaderboard.incr_score(msg['userid'],
                                           msg['score_increment'])
                except zmq.ZMQError, e:
                    if e.errno == errno.EAGAIN:
                        break
                    else:
                        raise

        top_scores = leaderboard.get_top_scores(15)
        zmq_publish_leaderboards_socket.send_json(top_scores)
        logger.debug('Leaderboard: %s', top_scores)
