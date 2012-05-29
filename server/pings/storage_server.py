"""Very simple storage server. Gets JSON messages via Zeromq and appends
them to a file. A new file is used every 10 minutes.

We don't call sync after every message to get the best bandwidth from the
disk. Given that a new file is used every 10 minutes, if there is a
problem, we lose only that 10 minutes of data. This is reliable enough for
our purposes. The files are also created on-demand, so if there is no
activity for a while, we don't get a ton of empty files.

The files are stored in a different directory for each day. The date and 10
minutes are in the UTC timezone, to avoid problems when changing to or
from daylight saving time.

To use more than one storage server on a single computer, pass them
different root directories."""

import sys, os, time, datetime, errno, json, zmq, ConfigParser, logging
from logging.config import fileConfig

logger = logging.getLogger(__name__)


class OnDemandFile:
    """Creates a new file on-demand every 10 minutes. Files are stored in
    directories named for the UTC date."""

    # Time period for which the same file is used, in minutes.
    time_period = 10

    def __init__(self, root_dir='.'):
        self.root_dir = root_dir
        self.f = None
        self.current_date = None
        self.current_time_period = None

    def _get_file(self):
        # When the date doesn't equal self.current_date, or the time_period
        # doesn't equal self.current_time_period, we create a new file.
        dt = datetime.datetime.utcnow()
        date = dt.date()
        time_period = int((dt.hour * 60 + dt.minute) / self.time_period)
        force_new_file = False

        if date != self.current_date:
            try:
                os.mkdir(os.path.join(self.root_dir, date.isoformat()))
            except OSError, e:
                if e.errno == errno.EEXIST:
                    # Ignore error when directory already exists. This can
                    # happen when the storage server is restarted.
                    pass
                else:
                    raise
            self.current_date = date
            force_new_file = True

        if force_new_file or time_period != self.current_time_period:
            filename = '%02dh%02d-UTC.data' % (dt.hour, int(dt.minute / 10) * 10)

            if self.f is not None:
                self.f.close()
            self.f = open(os.path.join(self.root_dir,
                                       self.current_date.isoformat(),
                                       filename), 'at')
            self.current_time_period = time_period

        return self.f
        
    def write_msg(self, msg_string):
        """Appends a string message to the file. If the string contains
        newlines, each is replaced by a space. A different file is used every hour.
        The format for each line is 'timestamp msg_string' (where timestamp is the
        number of seconds since the Unix epoch."""
        f = self._get_file()
        f.write('%.3f %s\n' % (time.time(), msg_string.replace('\n', ' ')))


def main():
    # Parse command line.
    if not (2 <= len(sys.argv) <= 3):
        print >>sys.stderr, "Usage: storage_server config.ini [output_directory]"
        sys.exit(1)
    elif len(sys.argv) == 3:
        root_dir = sys.argv[2]
    else:
        root_dir = '.'

    config_filename = sys.argv[1]

    # First configure logging
    fileConfig(config_filename)

    # Load config file.
    config_parser = ConfigParser.SafeConfigParser()
    config_parser.read(config_filename)
    config_section = 'storage_server'

    port = config_parser.getint(config_section, 'port')
    localhost_only = config_parser.getboolean(config_section, 'localhost_only')

    # Create input and output objects.
    f = OnDemandFile(root_dir)
    
    zmq_context = zmq.Context()
    zmq_socket = zmq_context.socket(zmq.PULL)
    if localhost_only:
        zmq_socket.bind('tcp://127.0.0.1:%d' % port)
    else:
        zmq_socket.bind('tcp://*:%d' % port)

    # Do the work
    while True:
        try:
            msg = zmq_socket.recv_json()
            f.write_msg(json.dumps(msg))
            logger.debug('Stored message: %s', msg)
        except Exception, e:
            logging.exception('Exception while processing message: %s', msg)
