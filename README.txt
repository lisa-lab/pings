This is the Pings server, to be used to crowdsource the mapping of network
delays throughout the Internet.

We implement two URLs that expect HTTP POST requests from clients. We use
JSON to send the replies. The client calls first the /get_pings URL to get
a list of IP addresses to ping, and a security token to be included with the
results. After the client has pinged all the IP addresses it was sent, it
calls /submit_ping_results to submit its results, including the security
token it was given. The results are written to text files, which can be
post-processed afterwards.

This code was tested under Python 2.7. We use Pyramid together with Gevent
for the web server, memcache for the security tokens and Zeromq to send the
results to a separate storage server, which takes care of writing them out
to disk.
