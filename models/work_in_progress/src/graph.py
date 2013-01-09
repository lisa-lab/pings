# coding=utf-8

import test_data
import utils

import numpy
import sys
import os
import bisect
import cPickle
from collections import defaultdict
import scipy.optimize
import pylab
from scipy.special import erf
from wsgiref.simple_server import make_server
from cgi import parse_qs
import json
from substitutions import all_substitutions


# prettier names
def prettify(record):
  for k, l in zip(['city', 'region_name', 'country_name'], ['City', 'Region', 'Country']):
    record[k] = all_substitutions.get(record[k], record[k])
    if record[k] == '':
      record[k] = '<i>Unknown %s</i>' % l


# load data
data = numpy.concatenate([cPickle.load(file('../data/sandbox2/' + t)) for t in 'train', 'valid', 'test'])
sizes = data.max(axis=0) + 1
names = cPickle.load(file('../data/sandbox2/names'))
params, terms = cPickle.load(file('../data/sandbox2/params'))
TARGET = 9

# compute stats (mean, var) for each location (country, region, city)
world_stats = data[:, TARGET].mean(), data[:, TARGET].var()**0.5
stats = []
for i, n in enumerate(names):
  stats_location = [[] for k in xrange(len(n))]
  for d in data:
    stats_location[d[i]].append(d[TARGET])
  for j, l in enumerate(stats_location):
    if len(l) >= 4:  # minimum number of examples for reliability
      stats_location[j] = numpy.mean(l), numpy.var(l)**0.5
    elif i > 0:
      parent = n[j][0] if i==1 else n[j][:-1]  # "parent" location
      index = bisect.bisect_left(names[i-1], parent)
      stats_location[j] = stats[-1][index]
    else:
      stats_location[j] = world_stats
  stats.append(stats_location)

# compute ranks for each location i within each ancestor location j
# order[i, j<=i]: i=0,1,2 (country, region, city) j=0,1,2 (world, country, region) => tuple(rank, out_of_N)
order = []
for i in xrange(3):
  order.append([])
  for j in xrange(i+1):
    order[i].append([None]*len(names[i]))
    last_index = 0
    while last_index < len(names[i]):  # 1 iteration = 1 j-location
      ancestor = names[i][last_index][:j]
      index = last_index + 1
      while index < len(names[i]) and names[i][index][:j] == ancestor:
        index += 1
      indices = numpy.argsort([s[0] for s in stats[i][last_index:index]])
      for k, l in enumerate(indices):
        order[i][j][last_index + l] = k+1, len(indices)
      last_index = index


# utility functions
def rank(i, n):
  return 50 * (n-i+1) / n

suffixes = 'st', 'nd', 'rd', 'th'
def pretty_rank(i):
  return '%i<sup>%s</sup>' % (i, suffixes[min(len(suffixes)-1, (i + 9) % 10)])

def percentile(value, mean, std):
  best_than = -erf((value-mean) / std)*0.5 + 0.5
  return best_than, int(50*best_than)

def sprint(*args):
  return ' '.join(map(unicode, args)) + '\n'

def stars_html(score):  # score from 0 to 50
  code = ''
  for i in xrange(5):
    c = (score >= i*10 + 5) + (score >= i*10 + 10)  # 2: full, 1: half, 0: empty
    code += '<img src="http://iconnect.iro.umontreal.ca/images/star%i.png">' % c
  return code

def bisect_find(array, what):  # search what in array assuming array is sorted, -1 if not found
  index = bisect.bisect_left(array, what)
  if array[index] == what: return index
  return -1


def display_stats(ip, measurements, html=True, full_page=True):
  # fake input for testing
  if ip is None:
    ip = '203.201.132.74'  # '70.82.10.129'
  if measurements == '':
    one = lambda : '.'.join(map(str, numpy.random.randint(256, size=4))) + ',' + str(max(51.0, numpy.random.normal(loc=400.0, scale=200.0)))
    measurements = '-'.join([one() for i in xrange(20)])


  # compute stats for request
  ip_int = utils.get_int_from_ip(ip)
  d = utils.get_geoip_data(ip)
  t = d['country_code'], d['region_name'], d['city']
  r = [bisect.bisect_left(names[i], t[:i+1] if i else t[0]) for i in xrange(3)]

  all_measurements = measurements.split('-')
  formatted = numpy.empty((len(all_measurements), 15), dtype=int)
  saved_geo_data = []
  for j, m in enumerate(all_measurements):
    try:
      ip2, latency = m.split(',')
      latency = float(latency)
      ip2_int = utils.get_int_from_ip(ip2)
    except:
      formatted = formatted[:j]
      break  # may be due to truncated URLs, stop here
    d2 = utils.get_geoip_data(ip2)
    saved_geo_data.append(d2)
    if d2 is not None:
      t2 = d2['country_code'], d2['region_name'], d2['city']
      r2 = [bisect_find(names[i], t2[:i+1] if i else t2[0]) for i in xrange(3)]
      distance = utils.geoip_distance(d, d2)
    else:
      t2 = '', '', ''
      r2 = -1, -1, -1
      distance = -1
    formatted[j, :] = r[0], r[1], r[2], -1, r2[0], r2[1], r2[2], -1, distance, latency, r[0]==r2[0], r[1]==r2[1], r[2]==r2[2], ip_int, ip2_int


  predictions = numpy.zeros(len(formatted))
  for (feature_indices, _), (p, n, _) in zip(terms, params):
    feature_indices = list(feature_indices)
    strides = numpy.array([numpy.prod(sizes[feature_indices[:i]]) for i in xrange(len(feature_indices) + 1)], dtype='uint64')
    features = formatted[:, feature_indices]
    known = (features != -1).all(axis=1)
    features = numpy.sum(features.astype('uint32') * strides[:-1], axis=1)
    regression = 8 in feature_indices  # hardcoded
    
    if regression:
      predictions[known] += p * features[known].astype(float) + n
    elif isinstance(p, numpy.ndarray):
      predictions[known] += p[features[known]]
    else:
      for j, f in enumerate(features):
        if known[j] and p.has_key(f):
          predictions[j] -= p[f]


  # online learning (adjust predictions)
  targets = formatted[:, TARGET]
  error = 0.0
  for i, (t, p) in enumerate(zip(targets, predictions)):
    if i > 0:
      predictions[i] += error / (i + 5.0)  # ad-hoc regularization
    error += t - p


  # shuffle for display
  indices = (formatted[:, 6] != -1).nonzero()[0]  # must have resolved city name to be displayed
  numpy.random.shuffle(indices)
  indices = indices[:5]


  # show stats for request (text)
  you_mean = targets.mean()
  if not html:
    response = u''
    response += sprint(d['country_name'], stats[0][r[0]], order[0][0][r[0]], rank(*order[0][0][r[0]]))
    response += sprint(d['region_name'], stats[1][r[1]], order[1][0][r[1]], order[1][1][r[1]], rank(*order[1][0][r[1]]))
    response += sprint(d['city'], stats[2][r[2]], order[2][0][r[2]], order[2][1][r[2]], order[2][2][r[2]], rank(*order[2][0][r[2]]))
    response += sprint('you', percentile(you_mean, *world_stats), '*', percentile(you_mean, *stats[0][r[0]]), percentile(you_mean, *stats[1][r[1]]), percentile(you_mean, *stats[2][r[2]]))

    response += sprint('average latency (predicted)', predictions.mean())
    response += sprint('average latency (measured)', you_mean)
    response += sprint('accuracy of predictions', abs(targets-predictions).mean())
    for i in indices:
      response += sprint(names[2][formatted[i, 6]][2], names[0][formatted[i, 4]], int(numpy.round(predictions[i])), '(pred)', targets[i], '(measured)')
    return response

  # show stats (html)
  region_term = {'CA': 'province', 'US': 'state'}.get(d['country_code'], 'region')
  prettify(d)
  
  response = u''

  if full_page: response += u"""<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
 "http://www.w3.org/TR/html4/loose.dtd">

<html lang="en">

 <head>
  <title>Analysis Results - The Internet Connectome project</title>
  <meta http-equiv="Content-Type" content="text/html;charset=utf-8">
  <link href="http://iconnect.iro.umontreal.ca/site.css" media="all" type="text/css" rel="stylesheet">
  <script type="text/javascript" src="http://code.jquery.com/jquery-1.8.3.min.js"></script>

<script>
function show(index, self) {
  tooltip = $('#tooltip' + index);
  tooltip.show();
  position = $(self).offset();
  position.top += 42;
  position.left += 14;
  tooltip.offset(position);
}
function hide(index) {
  $('#tooltip' + index).hide();
}
</script>

 </head>

 <body>

<center>
"""

  response += u"""<div class="main_div analysis">"""
  if not full_page:
    response += u"""<div class="close_button"><a target="_self" href="javascript: hide_analysis();">[X]</a></div>"""
  
  response += u"""
<div class="title">Analysis Results</div>

<div class="subtitle">Thank you for your participation !</div>

<h2>Your network location</h2>

<center><table cellspacing="0" cellpadding="0">

<tr><th style="text-align: left;">location</th>
<th>average<br />latency (ms) &dagger;</th>
<th></th></tr>

<tr onmouseover="show(1,this)" onmouseout="hide(1)" ><td class="left_column">""" + d['country_name'] + u"""</td>
<td class="middle_column">""" + str(int(numpy.round(float(stats[0][r[0]][0])))) + u"""</td>
<td style="min-width: 200px;">""" + stars_html(rank(*order[0][0][r[0]])) + u"""</td></tr>

<tr onmouseover="show(2,this)" onmouseout="hide(2)" ><td class="left_column">""" + d['region_name'] + u"""</td>
<td class="middle_column">""" + str(int(numpy.round(float(stats[1][r[1]][0])))) + u"""</td>
<td>""" + stars_html(rank(*order[1][0][r[1]])) + u"""</td></tr>

<tr onmouseover="show(3,this)" onmouseout="hide(3)" ><td class="left_column">""" + d['city'] + u"""</td>
<td class="middle_column">""" + str(int(numpy.round(float(stats[2][r[2]][0])))) + u"""</td>
<td>""" + stars_html(rank(*order[2][0][r[2]])) + u"""</td></tr>

<tr onmouseover="show(4,this)" onmouseout="hide(4)" style="background: #fec;"><td class="left_column">Overall</td>
<td class="middle_column">""" + str(int(numpy.round(you_mean))) + u"""</td>
<td>""" + stars_html(percentile(you_mean, *world_stats)[1]) + u"""</td></tr>

</table></center>

<p style="font-size: 0.9em;">&dagger; World average is estimated at """ + str(int(numpy.round(world_stats[0]))) + u""" ms.</p>

<div id="tooltip1" class="tooltip"><u>%s</u> is:<br />Ranked %s of %i countries.</div>""" % (d['country_name'], pretty_rank(order[0][0][r[0]][0]), order[0][0][r[0]][1]) + u"""
<div id="tooltip2" class="tooltip"><u>%s</u> is:<br />Ranked %s %s in %s.</div>""" % (d['region_name'], pretty_rank(order[1][1][r[1]][0]), region_term, d['country_name']) + u"""
<div id="tooltip3" class="tooltip"><u>%s</u> is:<br />Ranked %s of %i cities in %s.<br />Ranked %s of %i cities in %s.<br />Ranked %s of %i cities in our database.</div>""" % (d['city'], pretty_rank(order[2][2][r[2]][0]), order[2][2][r[2]][1], d['region_name'], pretty_rank(order[2][1][r[2]][0]), order[2][1][r[2]][1], d['country_name'], pretty_rank(order[2][0][r[2]][0]), order[2][0][r[2]][1]) + u"""
<div id="tooltip4" class="tooltip"><u>YOU</u> have an average latency of %i ms which is:<br />Faster than %i%% of the world.<br />Faster than %i%% of people in %s.<br />Faster than %i%% of people in %s.<br />Faster than %i%% of people in %s.</div>""" % (int(numpy.round(you_mean)), int(100*percentile(you_mean, *world_stats)[0]), int(100*percentile(you_mean, *stats[0][r[0]])[0]), d['country_name'], int(100*percentile(you_mean, *stats[1][r[1]])[0]), d['region_name'], int(100*percentile(you_mean, *stats[2][r[2]])[0]), d['city']) + u"""


<h2>Latency measurements</h2>

<center><table cellspacing="0" cellpadding="0">
<tr><td>Average latency (predicted)</td><td><b>%i ms</b></td></tr>
<tr><td>Average latency (measured)</td><td><b>%i ms</b></td></tr>
<tr><td style="width: 205px;">Accuracy of prediction</td><td><b>%i ms</b></td></tr>""" % (int(numpy.round(predictions.mean())), int(numpy.round(you_mean)), int(numpy.round(abs(targets-predictions).mean())))

  max_width = 220
  max_seen = max([max(int(numpy.round(predictions[i])), targets[i]) for i in indices])
  for i in indices:
    prettify(saved_geo_data[i])
    width_prediction = int(max_width * numpy.round(predictions[i]) / max_seen)
    width_target = int(max_width * float(targets[i]) / max_seen)
    response += u"""  
<tr><td>%s, %s</td><td>
<div style="width: %ipx;" class="bar top"></div><div class="label">%i ms</div><div style="clear: both;"></div>
<div style="width: %ipx;" class="bar"></div><div class="label bottom">%i ms</div><div style="clear: both;"></div>
</td></tr>""" % (saved_geo_data[i]['city'], saved_geo_data[i]['country_name'], width_prediction, int(numpy.round(predictions[i])), width_target, targets[i])

  response += u"""
<tr style="background: #fec;"><td colspan="2">
<div style="float: left; font-weight: bold;">Legend:</div>
<div style="width: 25px; height: 5px; background: #6D7D57; float: left; margin: 6px 5px 6px 40px;"></div><div style="float: left; font-size: 0.85em; color: #888;">Prediction</div>
<div style="width: 25px; height: 5px; background: #E35A00; float: left; margin: 6px 5px 6px 25px;"></div><div style="float: left; font-size: 0.85em; color: #888;">Measurement</div>
<div style="clear: both;"></div>
</td></tr>

</table></center>

</div>
"""

  if full_page: response += u"""
</center>

</body>
 
</html>
"""
  return response


# web server
def application(environment, start_response):
  if environment['PATH_INFO'] != '/feedback.py':
    start_response('404 Not Found', [])
    return []
  
  parameters = parse_qs(environment['QUERY_STRING'])
  ip = environment['REMOTE_ADDR']
  ip = parameters.get('ip', [ip])[0]  # override real IP for testing
  measurements = parameters.get('measurements', [''])[0]
  html = not bool(parameters.get('text_only', [''])[0])
  full_page = bool(parameters.get('full_page', [''])[0])
  response = display_stats(ip, measurements, html, full_page)
  if full_page:
    response = response.encode('utf-8')
    content_type = 'text/html' if html else 'text/plain'
  else:
    response = json.dumps({'content': response})  # already utf-8 encoded
    response = parameters.get('jsoncallback', ['jsoncallback'])[0] + '(' + response + ')'  # JSONP in fact
    content_type = 'application/json'
  headers = [('Content-Type', content_type), ('Content-Length', str(len(response))), ('charset', 'utf-8')]
  start_response('200 OK', headers)
  return [response]


if __name__ == '__main__':
  server = make_server('', 6660, application)
  print 'Server running ...'
  sys.stdout.flush()
  try:
    server.serve_forever()
  except KeyboardInterrupt:
    print 'Server shutdown'



###########################
def train_model(save=False):
  sets = [cPickle.load(file('../data/sandbox2/' + t)) for t in 'train', 'valid', 'test']

  sizes = numpy.maximum(*[s.max(axis=0) for s in sets]) + 1
  names = 'country1', 'region1', 'city1', 'type1', 'country2', 'region2', 'city2', 'type2', 'distance', 'latency', 'same_country', 'same_region', 'same_city', 'ip1', 'ip2'
  TARGET = names.index('latency')

  terms = [((), 0), ((8,), 0), ((3,), -1), ((7,), -1), ((3, 7), -1), ((0,), -1), ((4,), -1), ((10,), -1), ((0, 4), -1), ((0, 4, 3, 7), -1), ((1,), -1), ((5,), -1), ((11,), -1), ((1, 5), -1), ((2,), -1), ((6,), -1), ((12,), -1), ((2, 6), -1), ((13,), -1), ((14,), -1), ((13, 14), -1)]
  #terms = [((), 0)]  # constant (average) only
  #terms = [((), 0), ((0, 4), -1)]  # country-country only

  residuals = [s[:, TARGET].astype(float) for s in sets]
  print [numpy.mean(r**2)**0.5 for r in residuals]
  sys.stdout.flush()

  #bottom = sets[0][:, TARGET].min()  # never allow predictions lower than this
  saved_params = []

  for feature_indices, regularization in terms:
    feature_indices = list(feature_indices)
    strides = numpy.array([numpy.prod(sizes[feature_indices[:i]]) for i in xrange(len(feature_indices) + 1)], dtype='uint64')
    features = [numpy.sum(s[:, feature_indices] * strides[:-1], axis=1) for s in sets]
    regression = 8 in feature_indices  # hardcoded (only on distance)
    
    if regression:  # ridge regression
      floats = features[0].astype(float)
      
      ATA = numpy.empty((2, 2))
      ATA[0, 0] = (floats**2).sum()
      ATA[1, 1] = len(floats)
      ATA[0, 1] = ATA[1, 0] = floats.sum()
      ATb = numpy.empty((2, 1))
      ATb[0, 0] = (floats*residuals[0]).sum()
      ATb[1, 0] = residuals[0].sum()

      def regularize(coefficient, update=False):
        m, b = numpy.linalg.solve(ATA + numpy.diag([float(coefficient)]*2), ATb)
        result = residuals[1] - m * features[1] - b
        if update:
          saved_params.append((m, b, coefficient))
          for i, f in enumerate(features):
            residuals[i] -= m * f + b
        return result
    
    elif strides[-1] < 2000:  # dense
      params = numpy.zeros(strides[-1], dtype='float32')
      number = numpy.zeros(strides[-1], dtype='int32')
      for i, f in enumerate(features[0]):  # training set
        params[f] += residuals[0][i]
        number[f] += 1
      def regularize(coefficient, update=False):
        params_adjusted = params / (number + coefficient)
        result = residuals[1] - params_adjusted[features[1]]
        if update:
          saved_params.append((params_adjusted, number, coefficient))
          for i, f in enumerate(features):
            residuals[i] -= params_adjusted[f]
        return result

    else:  # sparse
      params = defaultdict(float)
      number = defaultdict(int)
      for i, f in enumerate(features[0]):
        params[f] += residuals[0][i]
        number[f] += 1
      def regularize(coefficient, update=False):
        params_adjusted = params.copy()
        for f, n in number.iteritems():
          params_adjusted[f] /= n + coefficient
        residual_valid = residuals[1].copy()
        for j, ff in enumerate(features[1]):
          if params_adjusted.has_key(ff):
            residual_valid[j] -= params_adjusted[ff]
        if update:
          saved_params.append((params_adjusted, number, coefficient))
          for i, f in enumerate(features):
            for j, ff in enumerate(f):
              if params_adjusted.has_key(ff):
                residuals[i][j] -= params_adjusted[ff]
        return residual_valid

    if regularization == -1:
      def minimize(coefficient):
        if coefficient <= 1e-5: return 1e100
        residual = regularize(coefficient)
        error = numpy.mean(residual**2)**0.5
        #print coefficient, error
        #sys.stdout.flush()
        return error      
      regularization = scipy.optimize.fmin(minimize, 10.0, xtol=1e-1, ftol=1e-3, disp=False)[0]
    regularize(regularization, update=True)

    print [numpy.mean(r**2)**0.5 for r in residuals], numpy.array(names)[feature_indices], regularization
    sys.stdout.flush()


  if save:
    filename = os.path.join(os.path.dirname(__file__), '..', 'data', 'sandbox2', 'params')
    cPickle.dump((saved_params, terms), file(filename, 'w'), cPickle.HIGHEST_PROTOCOL)

  a = numpy.array([sets[2][:, TARGET], sets[2][:, TARGET] - residuals[2]])
  numpy.random.shuffle(a.T)
  num_examples = a.shape[1]
  correct_order = lambda i, j : (a[0, i] > a[0, j]) == (a[1, i] > a[1, j])

  examples = a[:, :200].T
  #examples = sorted(examples, key=lambda x: x[0])
  pylab.plot(examples)
  pylab.legend(('target', 'prediction'))
  pylab.xlabel('test example')
  pylab.ylabel('latency (ms)')
  pylab.show()

  # more stats
  ranges = [0, 100, 200, 300, 500, 1000, 5001]
  classes = numpy.array([[bisect.bisect_right(ranges, j) - 1 for j in i] for i in a])
  num_classes = len(ranges) - 1

  print 'L1, L2 errors', numpy.mean(abs(residuals[2])), numpy.mean(residuals[2]**2)**0.5
  print 'class-wise:\n', numpy.array([numpy.mean([[abs(r), r**2] for r, c in zip(residuals[2], classes[0, :]) if c==k], axis=0)**[1, 0.5] for k in xrange(num_classes)])

  print 'classification accuracy', (classes[0, :] == classes[1, :]).mean()
  print 'confusion matrix:\n', numpy.array([[((classes[0, :]==c1) & (classes[1, :]==c2)).sum() for c2 in xrange(num_classes)] for c1 in xrange(num_classes)])  # row = target, column = prediction

  print 'ordering accuracy', numpy.mean([correct_order(*numpy.random.randint(num_examples, size=2)) for k in xrange(500000)])
  choices = [(classes[0, :]==c).nonzero()[0] for c in xrange(num_classes)]
  choice = lambda x: x[numpy.random.randint(len(x))]
  symmetrize = lambda A: (A + A.T) * 0.5
  print 'class-wise:\n', symmetrize(numpy.array([[numpy.mean([correct_order(choice(choices[c1]), choice(choices[c2])) for k in xrange(100000)]) for c2 in xrange(num_classes)] for c1 in xrange(num_classes)]))



def export_datasets():
  rows = []
  for i, s in enumerate(test_data.data_sets):
    print '\r%i/%i' % (i+1, len(test_data.data_sets)),
    sys.stdout.flush()
    for d in test_data.load_data_from_pkl(i):
      origin = d['origin_geoip']
      destination = d['destination_geoip']
      row = [origin['country_code'], origin['region_name'], origin['city'], d['origin_type'],
             destination['country_code'], destination['region_name'], destination['city'], d['destination_type'],
             d['distance'], d['peer_latency'], 0, 0, 0,
             utils.get_int_from_ip(d['origin_ip']), utils.get_int_from_ip(d['destination_ip'])]
      rows.append(row)

  countries, regions, cities = set(), set(), set()

  for r in rows:
    t = tuple(r)
    countries.add(t[0])
    regions.add(t[0:2])
    cities.add(t[0:3])
    countries.add(t[4])
    regions.add(t[4:6])
    cities.add(t[4:7])

  countries, regions, cities = map(sorted, (countries, regions, cities))
  filename = os.path.join(os.path.dirname(__file__), '..', 'data', 'sandbox2', 'names')
  cPickle.dump((countries, regions, cities), file(filename, 'w'), cPickle.HIGHEST_PROTOCOL)

  for i, r in enumerate(rows):
    print '\r%i/%i' % (i+1, len(rows)),
    sys.stdout.flush()
    t = tuple(r)
    r[0] = bisect.bisect_left(countries, t[0])
    r[1] = bisect.bisect_left(regions, t[0:2])
    r[2] = bisect.bisect_left(cities, t[0:3])
    r[3] += 1  # avoid -1
    r[4] = bisect.bisect_left(countries, t[4])
    r[5] = bisect.bisect_left(regions, t[4:6])
    r[6] = bisect.bisect_left(cities, t[4:7])
    r[7] += 1  # avoid -1
    r[8] = int(numpy.round(t[8]))
    r[9] = int(numpy.round(t[9]))
    r[10] = int(r[0]==r[4])  # same country
    r[11] = int(r[1]==r[5])  # same region
    r[12] = int(r[2]==r[6])  # same city

  data = numpy.array(rows, dtype='uint32')
  numpy.random.shuffle(data)
  bounds = 0, 0.8*len(data), 0.9*len(data), len(data)
  train, valid, test = [data[bounds[i]:bounds[i+1]] for i in 0, 1, 2]
  for t in 'train', 'valid', 'test':
    #numpy.random.shuffle(locals()[t])
    filename = os.path.join(os.path.dirname(__file__), '..', 'data', 'sandbox2', t)
    cPickle.dump(locals()[t], file(filename, 'w'), cPickle.HIGHEST_PROTOCOL)



