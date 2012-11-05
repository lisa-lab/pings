import logging

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPForbidden, HTTPBadRequest

from pings.web_server import resources

logger = logging.getLogger(__name__)


@view_config(route_name='get_pings',
             renderer='json', request_method='POST')
def get_pings(request):
    """Called by the client to get a list of addresses to ping."""
    client_addr = request.client_addr
    logger.debug('get_pings request client address: %s', client_addr)

    ip_addresses = resources.get_pings(client_addr)
    return {'token': resources.get_token(),
            'pings': ip_addresses,
            'geoip': resources.get_geoip_data(ip_addresses),
            'client_geoip': resources.get_geoip_data([client_addr])[0]}


@view_config(route_name='submit_ping_results',
             renderer='json', request_method='POST')
def submit_ping_results(request):
    """Called by the client to submit the results of the addresses pinged."""
    client_addr = request.client_addr
    logger.debug('submit_ping_results request: %s', request.json_body)

    # Check that token is valid.
    token = request.json_body.get('token')
    if not resources.check_token(token):
        # FB: should return 403 client error
        raise HTTPForbidden('Invalid or absent token value.')

    # Store results.
    results = request.json_body.get('results')
    results.insert(0, client_addr)
    if results is None:
        # FB: should return 400 client error
        raise HTTPBadRequest('No "results" field.')
    resources.store_results(results)

    # Update leaderboards if userid was passed.
    userid = request.json_body.get('userid')
    if userid is not None:
        resources.update_leaderboards(userid, results)

    return {'success': True}


@view_config(route_name='main', renderer='main.jinja2')
def main(request):
    return resources.get_leaderboards()
