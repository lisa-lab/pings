import logging
import os
import socket
from time import time

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPForbidden, HTTPBadRequest

from pings.web_server import resources

logger = logging.getLogger(__name__)

# tested on maggie44 for 8 process (2 x Xeon E5430 @ 2.66Ghz)
target_get_pings_seconds = 1600
nb_process = 8
cpu_goal = 0.75  # We try to use 75% of a core for this process

# the / 2 is that we need to support get_pings and submit_* call.
expected_get_pings_process_seconds = (target_get_pings_seconds / nb_process /
                                      2 * cpu_goal)

nb_get_pings = 0
last_time = time()
last_nb_get_pings = 0

# The round delay table. We change of index when we go over/under the threshold
time_table = [i * 60 for i in range(1, 6) +
              range(7, 16, 2) + range(20, 241, 5)]
time_table_idx = 0
min_round_time = time_table[0]

#We will store stats information in that file.
hostname = socket.gethostname()
stats = open("ip_server_stats.%s.%d.txt" % (hostname, os.getpid()), "a")


@view_config(route_name='get_pings',
             renderer='json', request_method='POST')
def get_pings(request):
    """Called by the client to get a list of addresses to ping."""
    global nb_get_pings, min_round_time
    global last_time, last_nb_get_pings, time_table_idx
    client_addr = request.client_addr
    logger.debug('get_pings request client address: %s', client_addr)

    ip_addresses = resources.get_pings(client_addr)
    token = resources.get_token()
    uuid = request.json_body.get("uuid")
    nick = request.json_body.get('userid')
    info = ["GET_PINGS", "TOKEN=" + token, "UUID=" + uuid,
            "NICK=" + nick, ip_addresses]

    resources.store_results(info)
    nb_get_pings += 1

    if (nb_get_pings % (5 * expected_get_pings_process_seconds)) == 0:
        now = time()
        removed = resources.last_clients.remove_old(now)
        #number of pings per second since the last check
        p_s = (nb_get_pings - last_nb_get_pings) / (now - last_time)
        ratio_pings_on_expected = p_s / expected_get_pings_process_seconds

        if ratio_pings_on_expected > 1:
            time_table_idx = min(time_table_idx + 1,
                                 len(time_table))
        elif ratio_pings_on_expected < 0.5:
            time_table_idx = max(time_table_idx - 1, 0)

        size_clients_list = len(resources.last_clients)

        min_round_time = time_table[time_table_idx]
        print >>stats, ("nb_get_pings=%d, new_pings=%d,"
                        " time=%.2f, elapsed_time(s)=%.2f,"
                        " ping_per_second=%f, ratio_pings_on_expected=%f,"
                        " time_table_index=%d, min_round_time=%d"
                        " size_clients_list=%d, removed=%d"% (
                            nb_get_pings, nb_get_pings - last_nb_get_pings,
                            now, now - last_time,
                            p_s, ratio_pings_on_expected,
                            time_table_idx, min_round_time,
                            size_clients_list, removed))
        stats.flush()

        last_time = now
        last_nb_get_pings = nb_get_pings
    return {'token': token,
            'pings': ip_addresses,
            'geoip': resources.get_geoip_data(ip_addresses),
            'client_geoip': resources.get_geoip_data([client_addr])[0],
            'client_ip': client_addr,
            'min_round_time': min_round_time}  # in seconds


@view_config(route_name='submit_ping_results',
             renderer='json', request_method='POST')
def submit_ping_results(request):
    """Called by the client to submit the results of the addresses pinged."""
    client_addr = request.client_addr
    logger.debug('submit_ping_results request: %s', request.json_body)

    # Check that token is valid.  Always accept it even if it is bad
    # as otherwise this would block client Also, the "bad" token is
    # probably caused by a server problem, not people trying to push
    # wrong results.
    token = request.json_body.get('token')
    nick = request.json_body.get('userid')
    uuid = request.json_body.get('uuid')
    bad_token = resources.check_token(token)

    # Store results.
    results = request.json_body.get('results')
    if results is None:
        # FB: should return 400 client error
        raise HTTPBadRequest('No "results" field.')

    results.insert(0, client_addr)
    results.append("TOKEN=" + token)
    results.append("TOKEN_VALID=" + str(bad_token))
    results.append("NICK=" + nick)
    results.append("UUID=" + uuid)
    resources.store_results(results)

    # Update leaderboards if nick was passed.
    if nick is not None:
        resources.update_leaderboards(nick, results)

    return {'success': True}


from pyramid.response import Response
@view_config(route_name='hello')
def hello_world(request):
    return Response('Hello %(name)s!' % request.matchdict)


@view_config(route_name='main', renderer='main.jinja2')
def main(request):
    return resources.get_leaderboards()
