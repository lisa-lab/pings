#!/usr/bin/env python

import time, requests, json, optparse, random
from pprint import pprint

possible_userids = ['foo'] * 3 + ['bar'] * 2 + ['baz']
def choose_userid():
    return random.choice(possible_userids)


def main():
    option_parser = optparse.OptionParser()
    option_parser.add_option('--delay', default=0, type='float',
                             help='Delay (in seconds) between the request for pings and the submission of the (fake) results. [Default: %default]')
    options, args = option_parser.parse_args()

    server_url = 'http://localhost:6543/'

    get_pings_url = server_url + 'get_pings'
    submit_ping_results_url = server_url + 'submit_ping_results'

    r = requests.post(get_pings_url,
                      data=json.dumps({'myip': '10.0.10.10'}))
    json_results = json.loads(r.text)
    pprint(json_results)
    token = json_results['token']
    pings = json_results['pings']

    time.sleep(options.delay)

    ping_results = {'token': token,
                    'userid': choose_userid(),
                    'results': random.sample(pings,
                                             random.randrange(1, len(pings)))}

    r = requests.post(submit_ping_results_url,
                      data=json.dumps(ping_results))
    print r.text[:300]

if __name__ == '__main__':
    main()
