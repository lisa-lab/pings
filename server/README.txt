This is the Pings server, to be used to crowdsource the mapping of network
delays throughout the Internet.

We implement two URLs that expect HTTP POST requests from clients. We use
JSON to send the replies. The client calls first the /get_pings URL to get
a list of IP addresses to ping, and a security token to be included with
the results. After the client has pinged all the IP addresses it was sent,
it calls /submit_ping_results to submit its results, including the security
token it was given. The results are written in an append-only way to text
files, which can be post-processed afterwards into whatever format is most
useful.

This code was tested under Python 2.7, with Fedora Core 14 and Ubuntu
11.10. We use Pyramid together with Gevent for the web server, Memcached
for the security tokens and Zeromq to send the results to a separate
storage server, which takes care of writing them out to disk. For the
leaderboards, we use Redis and its sorted set implementation.

You need to run the ``download_geoip_db`` script to download the free
city-level GeoIP database from MaxMind before starting the server For the
GeoIP component (which is used by the Java client to display the pings in
action on a world map).
