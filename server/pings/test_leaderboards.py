import subprocess, mock, socket, time, errno

# mut: module under test
import pings.leaderboards_server as mut
from datetime import date


def start_redis_process(port):
    address = '127.0.0.1'
    redis_process = subprocess.Popen(['redis-server', '-'],
                                            stdin=subprocess.PIPE)
    # Write out configuration file.
    redis_process.stdin.write('''port %d
daemonize no
bind %s
''' % (port, address))
    redis_process.stdin.close()

    # Wait until we can connect. Try for 2 seconds max.
    start_time = time.time()
    accepting_connections = False
    while not accepting_connections and (time.time() - start_time < 2):
        try:
            s = socket.create_connection((address, port))
            s.close()
            accepting_connections = True
        except socket.error, e:
            if e.errno == errno.ECONNREFUSED:
                pass
            else:
                raise

    return redis_process


def stop_process(popen_instance):
    popen_instance.terminate()
    popen_instance.wait()


class FakeDate(date):
    "A fake replacement for date that can be mocked for testing."
    def __new__(cls, *args, **kwargs):
        return date.__new__(date, *args, **kwargs)


class FakeConstantDate(FakeDate):
    @classmethod
    def today(cls):
        return date(2012, 1, 1)


class LeaderboardTester:
    """Unit tests for leaderboard classes. To use, create a derived class
    and override the get_new_leaderboard_instance classmethod to return
    an instance of the leaderboard class you want to test."""

    def test_more_entries_requested_than_in_leaderboard(self):
        lb = self.get_new_leaderboard_instance()
        lb.incr_score('foo', 10)
        lb.incr_score('bar', 20)
        lb.incr_score('foo', 5)
        # This shouldn't fail with a exception!
        assert len(lb.get_top_scores(10)) == 2
    
    def test_empty_leaderboard(self):
        lb = self.get_new_leaderboard_instance()
        assert len(lb.get_top_scores(10)) == 0

    def test_reset_leaderboard(self):
        lb = self.get_new_leaderboard_instance()
        lb.incr_score('foo', 10)
        lb.incr_score('bar', 12)
        lb.incr_score('baz', 5)
        assert len(lb.get_top_scores(10)) != 0
        lb.reset()
        assert len(lb.get_top_scores(10)) == 0

    def test_add_entries(self):
        lb = self.get_new_leaderboard_instance()
        assert lb.get_top_scores(10) == []
        lb.incr_score('foo', 10)
        assert lb.get_top_scores(10) == [('foo', 10)]
        lb.incr_score('foo', 5)
        assert lb.get_top_scores(10) == [('foo', 15)]
        lb.incr_score('bar', 20)
        assert lb.get_top_scores(10) == [('bar', 20), ('foo', 15)]

    def test_more_entries_in_leaderboard_than_requested(self):
        lb = self.get_new_leaderboard_instance()
        lb.incr_score('foo', 10)
        lb.incr_score('bar', 12)
        lb.incr_score('baz', 5)
        assert len(lb.get_top_scores(2)) == 2

    def test_order_of_incr_irrelevant(self):
        lb1 = self.get_new_leaderboard_instance()
        lb1.incr_score('foo', 10)
        lb1.incr_score('bar', 15)
        lb1.incr_score('foo', 20)
        
        lb2 = self.get_new_leaderboard_instance()
        lb2.incr_score('foo', 20)
        lb2.incr_score('foo', 10)
        lb2.incr_score('bar', 15)

        assert lb1.get_top_scores(10) == lb2.get_top_scores(10)

    def test_sorting_is_on_score(self):
        lb = self.get_new_leaderboard_instance()
        lb.incr_score('a', 10)
        lb.incr_score('b', 12)
        lb.incr_score('c', 5)
        assert lb.get_top_scores(10) == [('b', 12), ('a', 10), ('c', 5)]


class TestSimpleLeaderboard(LeaderboardTester):
    @classmethod
    def get_new_leaderboard_instance(cls):
        # The leaderboard state is stored in the object, so we get a clean
        # slate each time.
        return mut.SimpleLeaderboard()


class RedisLeaderboardTestMixin:
    """Mixin for all the Redis-based leaderboard tests. Take care of
    starting and stopping a Redis instance, and supplying its port number
    to the class under test."""
    port = 14678
    # Override this in derived classes.
    class_under_test = None

    @classmethod
    def get_new_leaderboard_instance(cls):
        r = cls.class_under_test('localhost', port=cls.port)
        # Need to reset the leaderboard, as the state of the leaderboard is
        # stored in the Redis backend, not in the leaderboard object, and
        # we reuse the same Redis server process for all tests in this class.
        r.reset()
        return r

    @classmethod
    def setup_class(cls):
        """Start Redis server."""
        cls.redis_process = start_redis_process(cls.port)

    @classmethod
    def teardown_class(cls):
        """Terminate Redis server."""
        stop_process(cls.redis_process)


class TestRedisLeaderboard(LeaderboardTester, RedisLeaderboardTestMixin):
    class_under_test = mut.RedisLeaderboard


class MultiLeaderboardAdapter(mut.MultiLeaderboard):
    """Adapter class for the TestMultiLeaderboardNoReset, so it can use the
    tests for a single leaderboard in LeaderboardTester. We make it look like
    MultiLeaderboard returns a single leaderboard instead of a dict with multiple
    ones. This way we can reuse the testing code for the single leaderboard.

    Won't work when testing that e.g. the daily leaderboard is reset each
    day and therefore the multiple leaderboards returned are not identical.
    Use a different test class to test that aspect."""

    def get_top_scores(self, num_top_entries):
        results = mut.MultiLeaderboard.get_top_scores(self, num_top_entries)
        top_scores = results['global']
        for name in self.names:
            if name != 'global':
                assert results[name] == top_scores
        return top_scores


@mock.patch('pings.leaderboards_server.datetime.date', FakeConstantDate)
class TestMultiLeaderboardNoReset(TestRedisLeaderboard):
    """Test all the behaviors of MultiLeaderboard that don't involve
    the periodic reset of the non-global leaderboards."""
    class_under_test = MultiLeaderboardAdapter


@mock.patch('pings.leaderboards_server.datetime.date', FakeDate)
class TestMultiLeaderboardPeriodicReset(RedisLeaderboardTestMixin):
    """Test all the features of MultiLeaderboard that involve some
    of the leaderboards (weekly, daily) being reset periodically.
    (We can't reuse the tests from LeaderboardTester together
    with an adapter for this.)"""
    class_under_test = mut.MultiLeaderboard

    def test_pediodic_reset(self):
        # Change to today_date will be reflected in the date that
        # MultiLeaderboard sees.
        today_date = date(2012, 1, 2) # A Monday (start of ISO week).
        FakeDate.today = classmethod(lambda cls: today_date)

        lb = self.get_new_leaderboard_instance()

        lb.incr_score('foo', 10)
        lb.incr_score('bar', 20)
        top_scores_jan1 = lb.get_top_scores(10)
        assert all(single_lb == [('bar', 20), ('foo', 10)]
                   for single_lb in top_scores_jan1.itervalues())

        # Reset of daily leaderboard.
        today_date = date(2012, 1, 3)
        top_scores_jan2 = lb.get_top_scores(10)
        assert top_scores_jan1['global'] == top_scores_jan2['global']
        assert top_scores_jan1['weekly'] == top_scores_jan2['weekly']
        assert top_scores_jan2['daily'] == []

        # Reset of weekly leaderboard.
        today_date = date(2012, 1, 9)
        top_scores_jan8 = lb.get_top_scores(10)
        assert top_scores_jan1['global'] == top_scores_jan8['global']
        assert top_scores_jan8['weekly'] == []
        assert top_scores_jan2['daily'] == []

        # New addition reflected in all leaderboards.
        lb.incr_score('foo', 5)
        top_scores_jan8_part2 = lb.get_top_scores(10)
        assert top_scores_jan8_part2 == {'global': [('bar', 20), ('foo', 15)],
                                         'daily': [('foo', 5)],
                                         'weekly': [('foo', 5)]}
