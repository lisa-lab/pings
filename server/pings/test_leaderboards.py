import subprocess

from pings.leaderboards_server import *


def start_redis_process(port):
    redis_process = subprocess.Popen(['redis-server', '-'],
                                            stdin=subprocess.PIPE)
    # Write out configuration file.
    redis_process.stdin.write('''port %d
daemonize no
bind 127.0.0.1
''' % port)
    redis_process.stdin.close()

    return redis_process

def stop_process(popen_instance):
    popen_instance.terminate()
    popen_instance.wait()


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
        return SimpleLeaderboard()


class TestRedisLeaderboard(LeaderboardTester):
    port = 14678

    @classmethod
    def get_new_leaderboard_instance(cls):
        r = RedisLeaderboard('localhost', port=cls.port)
        # Need to reset the leaderboard, as the state of the leaderboard is
        # stored in the Redis backend, not in the leaderboard object.
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
