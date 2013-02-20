This is source code for the internet connectome project, which has the goal of
mapping the networks delays throughout the Internet via crowdsourcing.

LISA http server: iconnect.iro.umontreal.ca  132.204.25.179
LISA ip server: iconnect2.iro.umontreal.ca  132.204.24.234

Some stuff to config:
server/pings/web_server/resources.py var always_up_addresses: The references IP to ping
                                     var probability_to_ping: The probability to ping a reference ip
                                     var _num_addresses: the number of additional ip to ping

It also try to ping others client.
client/PingsApplet.java var SERVER_HOSTNAME, SERVER_PORT: The url/port of the ping server
client/PingsClient.java var hostname and port in main(): The url/port of the ping server
client/applet_jar*.html a reference to the leaderboard server

Technology used:

Redis:
Redis is an open source, advanced key-value store. It is often referred to as a data structure server since keys can contain strings, hashes, lists, sets and sorted sets. It is in-memory, but can be saved: http://redis.io/topics/introduction

Jinja:
template pour le leaderboard


The server ask the client to pings up to _num_addresses (defined in
development.ini) ip per round.  Up to half of that could be from other
past client a server process saw. We limit this when the size of the
list of past client is low to don't ddos them.
