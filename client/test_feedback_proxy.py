#!/usr/bin/env python
#import httplib
import json
import os
import sys
import time
import urllib2

import ipaddr

nb_ip = 250
#nb_ip = 20
nb_test = 500
server = "iconnect2.iro.umontreal.ca"
port = 80

print sys.argv
if len(sys.argv) > 1:
    server = sys.argv[1]
if len(sys.argv) > 2:
    port = int(sys.argv[2])
print server, port
host = "%s:%s" % (server, port)

measurements = ""
for i in range(nb_ip):
    measurements += '-132.204.26.%s,29000' % str(i % 255)

#con = httplib.HTTPConnection(host)
#con.connect()
#url = "feedback.py?jsoncallback=lll&measurements=" + measurements
#print url

t0 = time.time()
for i in range(nb_test):
#    os.system("wget -O feedback.output %s/%s" % (host, url))
#    con.request("GET", url)
#    rep = con.getresponse()
    out = urllib2.urlopen('http://%s/feedback.py?jsoncallback=lll&measurements=%s' % (host, measurements))
    assert out.getcode() == 200

t1 = time.time()

print "time per request with %d ip %fs" % (nb_ip, (t1 - t0) / nb_test)
print "request/seconds", 1 / ((t1 - t0) / nb_test)
#print out.read()
