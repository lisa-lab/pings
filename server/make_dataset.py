#!/usr/bin/env python
# This script convert the server logs to a dataset file.
# It also print some stats at the end of the dataset.

import ipaddr
import sys

no_good = []
test_only = []
total_good_icmp_lines = 0
total_good_tcp_lines = 0
total_not_test_lines = 0
total_nb_test_lines = 0
total_long_ping = 0  # per ping
total_bad_time = 0
total_good_icmp = 0  # per ping
total_good_tcp = 0  # per ping
nb_private_client_lines = 0
nb_private_dest = 0
nb_udem_client_line = 0
good_client_ips = set()  # clients ip that gave good results.
all_client_ips = set()  # all clients ip
all_dest_ips = set()
good_dest_ips = set()
ips_icmp_not_firewall = {}
ips_icmp_firewall = set()
total_nb_lines = 0
total_get_pings = 0
for filename in sys.argv[1:]:
    f = open(filename)
    lines = f.readlines()
    f.close()
    total_nb_lines += len(lines)
    good_icmp_lines = set()
    good_tcp_lines = set()
    not_test_lines = 0
    nb_test_lines = 0
    good_icmp = 0  # per ping
    good_tcp = 0  # per ping
    for line in lines:
        if "FOO" in line:
            nb_test_lines += 1
            continue
        else:
            not_test_lines += 1
            sp = line.split()
            if "GET_PINGS" in sp[1]:
                total_get_pings += 1
                continue
            client_ip = sp[1].replace('"', '').replace('[', '').replace(',', '')
            if isinstance(client_ip, basestring):
                try:
                    client_ip = ipaddr.IPv4Address(client_ip)
                except Exception, e:
                    import pdb;pdb.set_trace()
                    pass
            if not str(client_ip).startswith("132.204") and not client_ip.is_private:
                all_client_ips.add(client_ip)
            elif str(client_ip).startswith("132.204"):
                nb_udem_client_line += 1
            elif client_ip.is_private:
                nb_private_client_lines += 1
                continue

            ping = ""
            dest_ip = None
            icmp_line_success = False
            for i, part in enumerate(sp):
                part = part.replace('"', '').replace('[', '').replace(',', '')
                if "ICMP" == part:
                    ping = "ICMP"
                    dest_ip = sp[i + 1]
                elif "TCP" == part:
                    ping = "TCP"
                    dest_ip = sp[i + 1]
                if dest_ip and str(dest_ip).endswith(":80"):
                    dest_ip = dest_ip[:-3]
                if dest_ip and isinstance(dest_ip, basestring):
                    dest_ip = ipaddr.IPv4Address(dest_ip)
                if dest_ip and dest_ip.is_private:
                    nb_private_dest += 1
                    continue

                if dest_ip and not str(dest_ip).startswith("132.204") and not dest_ip.is_private:
                    all_dest_ips.add(dest_ip)
                if part.endswith("ms") and part != "?ms":
                    if part.startswith("!"):
                        part = part[1:]
                    elif part.startswith("?"):
                        part = part[1:]
                    elif part == '<1ms':  # Too short time, we round to 1ms
                        part = '1ms'
                    if ',' in part:
                        part = part.replace(',', '.')
                    try:
                        ms = float(part[:-2])
                    except Exception, e:
#                        print part
                        total_bad_time += 1
                        #TODO find/fix those problems
                        if not part.startswith("czas=") and not part == "ms":
                            import pdb;pdb.set_trace()
                        continue
                    if ms < 2000:
                        if ping == "ICMP":
                            icmp_line_success = True
                            good_icmp += 1
                            good_icmp_lines.add(line)
                        elif ping == "TCP":
                            good_tcp += 1
                            good_tcp_lines.add(line)
                        else:
                            raise Exception("!!!")
                        print ping, str(client_ip), dest_ip, ms
                        if not str(dest_ip).startswith("132.204") and not dest_ip.is_private:
                            good_dest_ips.add(dest_ip)1
                    else:
                        total_long_ping += 1
                if "TROUTE" in part:
                    break
            if (good_icmp or good_tcp) and not str(client_ip).startswith("132.204") and not client_ip.is_private:
                good_client_ips.add(client_ip)
            if icmp_line_success and not str(client_ip).startswith("132.204") and not client_ip.is_private:
                ips_icmp_not_firewall.setdefault(client_ip, 0)
                ips_icmp_not_firewall[client_ip] += 1
            elif not icmp_line_success and not str(client_ip).startswith("132.204") and not client_ip.is_private:
                ips_icmp_firewall.add(client_ip)

#    print filename, "nb good lines icmp/tcp/not_test", len(good_icmp_lines), len(good_tcp_lines), not_test_lines
    if len(good_tcp_lines) == 0 and len(good_icmp_lines) == 0:
        no_good.append(filename)
    if not_test_lines == 0:
        test_only.append(filename)
    total_good_icmp_lines += len(good_icmp_lines)
    total_good_tcp_lines += len(good_tcp_lines)
    total_good_icmp += good_icmp
    total_good_tcp += good_tcp
    total_not_test_lines += not_test_lines
    total_nb_test_lines += nb_test_lines

#print good_ips
#print all_ips
print "total_good_icmp_lines", total_good_icmp_lines
print "total_good_icmp", total_good_icmp
print "total_good_tcp_lines", total_good_tcp_lines
print "total_good_tcp", total_good_tcp
print "total_nb_test_lines", total_nb_test_lines
print "total_long_ping (per ping try)", total_long_ping
print "total_bad_time (per ping try)", total_bad_time
print "total_nb_lines", total_nb_lines
print "total_get_pings (lines)", total_get_pings
print "nb_udem_client_line", nb_udem_client_line
print "uniq dest ip", len(all_dest_ips)
print "uniq dest ip that gave good results", len(good_dest_ips)
print "uniq client ip", len(all_client_ips)#, [str(ip) for ip in sorted(all_client_ips)]
print "uniq client ip that gave good results", len(good_client_ips)#, [str(ip) for ip in sorted(good_client_ips)]
print "File with no good data:", no_good

print "File with only test lines:", test_only
print "client ips not firewall", len(ips_icmp_not_firewall)#, [str(ip) for ip in sorted(ips_icmp_not_firewall)]
print "client ips firewall", len(ips_icmp_firewall), [str(ip) for ip in sorted(ips_icmp_firewall)]
print "client with runs detected as with and without icmp firewal", len(ips_icmp_firewall.intersection(ips_icmp_not_firewall))
print "nb_private_client_lines", nb_private_client_lines
print "nb_private_dest", nb_private_dest
