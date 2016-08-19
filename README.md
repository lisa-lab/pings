## Introduction

This is source code for the Internet Connectome Project, which has the goal of
mapping the networks delays throughout the Internet via crowdsourcing.

The goal of the survey was to map the topology of the Internet as well as measure,
as accurately as possible, the latency between any two end points of the Internet
where users could be. The topology is surveyed using the traceroute tool.
The latency is estimated using two types of pings.
The first ping method is the classical ping that uses the ICMP protocol to estimate
the round-trip time between two computers. The second ping method
is the non-traditional TCP ping that uses the TCP protocol to estimate the time
it takes to establish a TCP connection between the local host and the remote destination.

Volunteers could participate to the survey by going to our Internet survey web page
and launching a Java applet. The applet probes the topology of the Internet and
estimates the latency to a number of remote destinations as determined by our survey server.
The survey server determines which remote hosts will be probed by the volunteers
in order to ensure a good coverage of the Internet as well as reliable latency measures.

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

## License

The code and data are available under a BSD 3-clause style
[license](LICENSE.txt).
