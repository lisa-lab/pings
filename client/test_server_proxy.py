"""Code snippet to test the Java ServerProxy interface to the Pings server."""
import sys
import time

import ClientInfo
import ServerProxy

nb_iter = 1000
server = "iconnect.iro.umontreal.ca"
port = 80
sleep_time = 0

print sys.argv
if len(sys.argv) > 1:
    server = sys.argv[1]
if len(sys.argv) > 2:
    port = int(sys.argv[2])
if len(sys.argv) > 3:
    sleep_time = float(sys.argv[3])
print "server", server, "port", port, "sleep_time", sleep_time
sp = ServerProxy(server, port)


if False:
    # Need to change permissions on ServerProxy Java class for this to work.
    print 'Calling doJsonRequest directly...'
    r = sp.doJsonRequest('/get_pings', None)
    print r

print

info = ClientInfo("", "yoda")

pings_queue = [None] * nb_iter
t0 = time.time()
for i in range(nb_iter):
    if sleep_time > 0:
        time.sleep(sleep_time)
    pings_queue[i] = sp.getPings(info)
t1 = time.time()
print "Time get_results r/s:",  nb_iter / (t1 - t0)
print

if True:
    d = {}
    for i in range(len(pings_queue)):
        pings = pings_queue[i]
        assert len(pings.addresses) > 0
        assert len(pings.geoip_info) == len(pings.addresses)
        #Check that we have at least half of ip with geoip info.
        assert len([n for n in pings.geoip_info if n is not None]) >= (len(pings.geoip_info) / 2.)
        d.setdefault(pings.min_round_time, 0)
        d[pings.min_round_time] += 1
        #print 'Client Geoip', info.getGeoipInfo()
        #print 'Token', pings.token
        #print 'First address', pings.addresses[0]
        #print 'Geoip for first address', pings.geoip_info[0]
    print "list(min_round_time: nd round):",
    keys = d.keys()
    keys.sort()
    for key in keys:
        print "(%d: %d)" % (key, d[key]),

# Fill in results.
for it in range(len(pings_queue)):
    pings = pings_queue[it]
    for i in range(len(pings.addresses)):
        pings.results[i] = 'FOO %d' % (i * it)

print
print 'Submitting results'
try:
    try:
        t0 = time.time()
        for i in range(nb_iter):
            sp.submitResults(info, pings_queue[i])
    except Exception, e:
        print e
finally:
    t1 = time.time()

print "Time submitResults r/s:",  nb_iter / (t1 - t0)
