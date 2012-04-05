from pings.leaderboards_server import SimpleLeaderboard

class LeaderboardTester:
    """Unit tests for leaderboard classes. To use, create a derived class
    and set leaderboard_class to the class you want to test."""
    leaderboard_class = None

    @classmethod
    def get_new_leaderboard_instance(cls):
        return cls.leaderboard_class()

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
    leaderboard_class = SimpleLeaderboard

