This is source code for the internet connectome project, which has the goal of
mapping the networks delays throughout the Internet via crowdsourcing.

## Data

The project is now finished, and the data can downloaded
[here](https://drive.google.com/open?id=0ByUKRdiCDK7-b0Y5ZDNOU1BGZ28).

## Reference

Some stuff to config:

server/pings/web_server/resources.py:

* `var always_up_addresses`: The references IP to ping
* `var probability_to_ping`: The probability to ping a reference ip
* `var _num_addresses`: the number of additional ip to ping

It can also try to ping others client.

* client/PingsApplet.java var `SERVER_HOSTNAME`, `SERVER_PORT`: The url/port of the ping server
* client/PingsClient.java var `hostname` and `port` in `main()`: The url/port of the ping server
* client/applet_jar*.html a reference to the leaderboard server

The server asks the client to ping up to `_num_addresses` (defined in
development.ini) ip per round.  Up to half of that could be from other
past client a server process saw. We limit this when the size of the
list of past client is low to not DDOS them.

## Technology used

Redis:

Redis is an open source, advanced key-value store. It is often referred to as a
data structure server since keys can contain strings, hashes, lists, sets and
sorted sets. It is in-memory, but can be saved:
[redis.io/topics/introduction](http://redis.io/topics/introduction).

Jinja:

Template for leaderboard.

## Acknowledgements 

The project was performed under the Ubisoft / NSERC industrial chair on Artificial
Intelligence at the DIRO, Université de Montréal. We thank all the participants who
helped collect the data.
