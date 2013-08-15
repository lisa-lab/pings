import json
import logging
import os
import socket
import sys
from time import time

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPForbidden, HTTPBadRequest
from pyramid.response import Response

from pings.web_server import resources

try:
    sys.path[0:0] = [os.path.join(os.path.dirname(__file__),
                                  '../../../models/work_in_progress/src')]
    from graph import display_stats, load_model
except Exception, e:
    print "Exception was caught during the import of the graph module."
    print "This will make the feedback part of the system not work"
    print e

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
nb_submited_requests = 0
nb_submited_results = 0
nb_feedback = 0
last_nb_feedback = 0
last_nb_submited_requests = 0

# The round delay table. We change of index when we go over/under the threshold
time_table = [i * 60 for i in range(1, 6) +
              range(7, 16, 2) + range(20, 241, 5)]
time_table_idx = 0
min_round_time = time_table[0]

#We will store stats information in that file.
hostname = socket.gethostname()
server_ip = socket.gethostbyname(socket.gethostname())
stats = open("ip_server_stats.%s.%d.txt" % (hostname, os.getpid()), "a")

# Load the predictor and data set to be able to display statistics
# in feedback
load_model()


def get_client_ip(request):
    """
    This fct return the client ip. If not private in the request use it.
    Otherwise, use the ip provided by the client itself.
    """

    client_addr = request.client_addr
    # If the ip_addresses is private, check if the client provided one.
    int_ip = resources.get_int_from_ip(client_addr)
    if ((167772160 <= int_ip <= 184549375) or
        (2886729728 <= int_ip <= 2887778303) or
        (3232235520 <= int_ip <= 3232301055)):  # is_private
        ip = request.json_body.get("ip", "")
        if ip:
            int_ip = resources.get_int_from_ip(ip)
            if not ((167772160 <= int_ip <= 184549375) or
                    (2886729728 <= int_ip <= 2887778303) or
                    (3232235520 <= int_ip <= 3232301055)):  # is_private
                client_addr = ip
    return client_addr


@view_config(route_name='get_pings',
             renderer='json', request_method='POST')
def get_pings(request):
    """Called by the client to get a list of addresses to ping."""
    global nb_get_pings, min_round_time
    global last_time, last_nb_get_pings, time_table_idx
    global nb_submited_requests, nb_submited_results, last_nb_submited_requests
    global nb_feedback, last_nb_feedback

    client_addr = get_client_ip(request)
    logger.debug('get_pings request client address: %s', client_addr)

    ip_addresses = resources.get_pings(client_addr)
    token = resources.get_token()
    uuid = request.json_body.get("uuid")
    nick = request.json_body.get('userid')
    info = ["GET_PINGS", "TOKEN=" + token, "UUID=" + uuid,
            "NICK=" + nick, ip_addresses]

    resources.store_results(info)
    nb_get_pings += 1
    now = time()
    spent = now - last_time

    if spent > 5:
        removed = resources.last_clients.remove_old(now)
        #number of pings per second since the last check
        p_s = (nb_get_pings - last_nb_get_pings) / spent
        submit_s = (nb_submited_requests - last_nb_submited_requests) / spent
        feedback_s = (nb_feedback - last_nb_feedback) / spent
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
                        " time_table_index=%d, min_round_time=%d,"
                        " size_clients_list=%d, removed=%d,"
                        " nb_submit_requests=%d, nb_submit_results=%d,"
                        " submit_req_per_second=%d,"
                        " nb_feedback=%d, nb_feedback_per_second=%d"% (
                            nb_get_pings, nb_get_pings - last_nb_get_pings,
                            now, now - last_time,
                            p_s, ratio_pings_on_expected,
                            time_table_idx, min_round_time,
                            size_clients_list, removed,
                            nb_submited_requests, nb_submited_results,
                            submit_s, nb_feedback, feedback_s))
        stats.flush()

        last_time = now
        last_nb_get_pings = nb_get_pings
        last_nb_submited_requests = nb_submited_requests
        last_nb_feedback = nb_feedback

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
    global nb_submited_requests, nb_submited_results

    client_addr = get_client_ip(request)
    logger.debug('submit_ping_results request: %s', request.json_body)

    # Check that token is valid.  Always accept it even if it is bad
    # as otherwise this would block client Also, the "bad" token is
    # probably caused by a server problem, not people trying to push
    # wrong results.
    token = request.json_body.get('token')
    nick = request.json_body.get('userid')
    uuid = request.json_body.get('uuid')

    # Store results.
    results = request.json_body.get('results')
    if results is None:
        # FB: should return 400 client error
        raise HTTPBadRequest('No "results" field.')

    results.insert(0, client_addr)
    results.append("TOKEN=" + token)
    results.append("NICK=" + nick)
    results.append("UUID=" + uuid)
    resources.store_results(results)
    resources.store_known_pignable(results)
    nb_submited_requests += 1
    nb_submited_results += len(results)

    # Update leaderboards if nick was passed.
    if nick is not None:
        resources.update_leaderboards(nick, results)

    return {'success': True}


@view_config(route_name='hello')
def hello_world(request):
    return Response('Hello %(name)s!' % request.matchdict)


@view_config(route_name='main', renderer='main.jinja2')
def main(request):
    return resources.get_leaderboards()


@view_config(route_name='feedback')
def feedback(request):
    global nb_feedback

    if not request.path_qs.startswith("/feedback.py"):
        raise HTTPBadRequest('Malformed input')

    parameters = request.params
    # override real IP for testing
    ip = parameters.get('ip', request.client_addr)

    # If the client have a private ip, it is on the same network as
    # the server. So we use the server ip as there isn't have geoip
    # info for private ip.
    int_ip = resources.get_int_from_ip(ip)
    if ((167772160 <= int_ip <= 184549375) or
        (2886729728 <= int_ip <= 2887778303) or
        (3232235520 <= int_ip <= 3232301055)):  # is_private
        ip = server_ip

    # Get parameters
    measurements = parameters.get('measurements', '')
    html = not bool(parameters.get('text_only', ''))
    full_page = bool(parameters.get('full_page', ''))

    # Limit the number of ip used in the analysis to limit the time.
    if len(measurements) == '':
        raise HTTPBadRequest('Malformed input. No measurements received.')
    ms = [m for m in measurements.split('-') if m]
    if time_table_idx == 0:
        nb = 20
    elif time_table_idx == 1:
        nb = 15
    else:
        nb = 10
    measurements = '-'.join(ms[-nb:])

    response = display_stats(ip, measurements, html, full_page)
    if full_page:
        response = response.encode('utf-8')
        content_type = 'text/html' if html else 'text/plain'
    else:
        response = json.dumps({'content': response})  # already utf-8 encoded
        # wrap the reponse in jsonp
        response = parameters.get('jsoncallback',
                                  'jsoncallback') + '(' + response + ')'
        content_type = 'application/json'
    res = Response(response,
                   content_type=content_type,
                   charset='utf-8')
    nb_feedback += 1
    return res
