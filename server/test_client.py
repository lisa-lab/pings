#!/usr/bin/env python

import time, requests, json, optparse

def main():
    option_parser = optparse.OptionParser()
    option_parser.add_option('--delay', default=0, type='float',
                             help='Delay (in seconds) between the request for pings and the submission of the (fake) results. [Default: %default]')
    options, args = option_parser.parse_args()

    server_url = 'http://localhost:6543/'

    get_pings_url = server_url + 'get_pings'
    submit_ping_results_url = server_url + 'submit_ping_results'

    r = requests.post(get_pings_url, data={'myip': '10.0.10.10'})
    json_results = json.loads(r.text)
    print json_results
    token = json_results['token']

    time.sleep(options.delay)

    r = requests.post(submit_ping_results_url, data={'token': token,
                                                     'results': 'xxx'})
    print r.text[:300]

if __name__ == '__main__':
    main()
