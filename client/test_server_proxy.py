"""Code snippet to test the Java ServerProxy interface to the Pings server."""

import ServerProxy, ClientInfo

sp = ServerProxy('localhost', 6543)

if False:
    # Need to change permissions on ServerProxy Java class for this to work.
    print 'Calling doJsonRequest directly...'
    r = sp.doJsonRequest('/get_pings', None)
    print r

print
info = ClientInfo()
pings = sp.getPings(info)

print 'Client Geoip', info.getGeoipData()
print 'Token', pings.token
print 'First address', pings.addresses[0]
print 'Geoip for first address', pings.geoip_info[0]

# Fill in results.
for i in range(len(pings.addresses)):
    pings.results[i] = 'FOO %d' % i

print
print 'Submitting results'
sp.submitResults(info, pings)
