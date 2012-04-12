"""Code snippet to test the Java ServerProxy interface to the Pings server."""

import ServerProxy, Client_Info

sp = ServerProxy('localhost', 6543)

if False:
    # Need to change permissions on ServerProxy Java class for this to work.
    print 'Calling doJsonRequest directly...'
    r = sp.doJsonRequest('/get_pings', None)
    print r

print
info = Client_Info()
pings = sp.getPings(info)
print pings.token
print pings.addresses[0]
print pings.geoip_info[0]

print
print 'Submitting results'
sp.submitResults(info, pings)
