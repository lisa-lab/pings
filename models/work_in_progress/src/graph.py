# coding=utf-8

import bisect
from cgi import parse_qs
from collections import defaultdict
import cPickle
import json
import os
import sys
from wsgiref.simple_server import make_server

import numpy
from scipy.special import erf
try:
    #The following import are needed only for training
    import pylab
    import scipy.optimize

    # needed for training with KT
    import scipy.stats
except ImportError:
    pass

from substitutions import all_substitutions
import test_data
import utils


# prettier names
def prettify(record):
  for k, l in zip(['city', 'region_name', 'country_name'], ['City', 'Region', 'Country']):
    record[k] = all_substitutions.get(record[k], record[k])
    if record[k] == '':
      record[k] = '<i>Unknown %s</i>' % l

LOAD = True
PLOT_GEOIP = False
PLOT_ACCURACY = False


if LOAD:
    # load data
    dataf = os.path.join(os.path.dirname(__file__), '../data/sandbox2/')
    data = numpy.concatenate([cPickle.load(file(os.path.join(dataf, t))) for t in 'train', 'valid', 'test'])
    sizes = data.max(axis=0) + 1
    namesf = os.path.join(os.path.dirname(__file__), '../data/sandbox2/names')
    names = cPickle.load(file(namesf))
    paramsf = os.path.join(os.path.dirname(__file__), '../data/sandbox2/params')
    params, terms = cPickle.load(file(paramsf))
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
    ip = '132.204.22.30'  # tikuanyin
  if measurements == '':
    one = lambda : '.'.join(map(str, numpy.random.randint(256, size=4))) + ',' + str(max(51.0, numpy.random.normal(loc=400.0, scale=200.0)))
    measurements = '-'.join([one() for i in xrange(20)])


  # compute stats for request
  ip_int = utils.get_int_from_ip(ip)
  d = utils.get_geoip_data(ip)
  if d is None:
    d = defaultdict(unicode)
    d['area_code'] = 0
    d['dma_code'] = 0
    d['latitude'] = 0
    d['longitude'] = 0
  t = d['country_code'], d['region_name'], d['city']
  r = [bisect.bisect_left(names[i], t[:i+1] if i else t[0]) for i in xrange(3)]

  all_measurements = measurements.split('-')
  all_measurements = [m for m in all_measurements if m]  # remove empty section.
  formatted = numpy.empty((len(all_measurements), 15), dtype=int)
  saved_geo_data = []
  for j, m in enumerate(all_measurements):
    try:
      ip2, latency = m.split(',')
      latency = float(latency)
      ip2_int = utils.get_int_from_ip(ip2)
    except Exception, e:
      print "In display_stats, skip the measurements '%s' and following as we got this error:" % m
      print e
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
      #predictions[i] += error / (i + 5.0)  # ad-hoc regularization
      predictions[i] = max(30, predictions[i])
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

  response += u"""<div class="main_div analysis">

<div class="title">Analysis Results</div>

<div class="subtitle">Thank you for your participation!<br>
Please, let the applet run longer to help us collect more data.<br>
Your score in the <a href="leaderboard.html">leaderboard</a> will continue to raise.</div>

<h2>Your network location</h2>

<center><table cellspacing="0" cellpadding="0">

<tr><th style="text-align: left;">location</th>
<th>average<br />latency (ms) &dagger;</th>
<th>Rank (mouse over stars for details)</th></tr>

<tr onmouseover="show(1,this)" onmouseout="hide(1)" ><td class="left_column">""" + d['country_name'] + u"""</td>
<td class="middle_column">""" + str(int(numpy.round(float(stats[0][r[0]][0])))) + u"""</td>
<td style="min-width: 200px;">""" + stars_html(rank(*order[0][0][r[0]])) + u"""</td></tr>

<tr onmouseover="show(2,this)" onmouseout="hide(2)" ><td class="left_column">""" + d['region_name'] + u"""</td>
<td class="middle_column">""" + str(int(numpy.round(float(stats[1][r[1]][0])))) + u"""</td>
<td>""" + stars_html(rank(*order[1][0][r[1]])) + u"""</td></tr>

<tr onmouseover="show(3,this)" onmouseout="hide(3)" ><td class="left_column">""" + d['city'] + u"""</td>
<td class="middle_column">""" + str(int(numpy.round(float(stats[2][r[2]][0])))) + u"""</td>
<td>""" + stars_html(rank(*order[2][0][r[2]])) + u"""</td></tr>

<tr onmouseover="show(4,this)" onmouseout="hide(4)" style="background: #fec;"><td class="left_column">YOU</td>
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
<tr><td style="width: 205px;">Mean prediction error</td><td><b>%i ms</b></td></tr>""" % (int(numpy.round(predictions.mean())), int(numpy.round(you_mean)), int(numpy.round(abs(targets-predictions).mean())))

#  print predictions, targets, indices
  if len(indices) > 0:
      # This can happen if all ip can't be resolved
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
def train_model(save=False,
                # Ignore outlier data with delay bigger than this threshold in the train and valid data
                train_outlier_threshold=5000,  # keep everything
                objective='LAD',  # least absolute deviation
                model='full',
                ):
  # Possible value for objective:
  # - 'LS': least squares, minimize the L2-norm of the difference between objective and prediction
  # - 'LAD': least absolute deviation, minimize the L1-norm of that difference
  # - 'KT': Kendall's tau coefficient, minimized by Theil's estimator

  # Possible value for model:
  # - 'full': full graphical model
  # - 'constant': constant (average) only
  # - 'distance': GeoIP distance only (IP/Location)
  # - 'countries': country-country only

  sets = [cPickle.load(file('../data/sandbox2/' + t)) for t in 'train', 'valid', 'test']

  def filter_outliers(dataset, threshold, column):
    """
    Remove lines in dataset where the value of column is above threshold.
    """
    return numpy.asarray([row for row in dataset if row[column] <= threshold])

  names = 'country1', 'region1', 'city1', 'type1', 'country2', 'region2', 'city2', 'type2', 'distance', 'latency', 'same_country', 'same_region', 'same_city', 'ip1', 'ip2'
  TARGET = names.index('latency')

  # Remove outliers on train and valid, but keep them in the test set
  sets[0] = filter_outliers(sets[0], outlier_threshold, TARGET)
  sets[1] = filter_outliers(sets[1], outlier_threshold, TARGET)

  sizes = numpy.maximum(*[s.max(axis=0) for s in sets]) + 1

  model_to_terms = {
          'full': [((), 0),             # constant (average)
                   ((8,), 0),           # distance
                   ((3,), -1),          # type1
                   ((7,), -1),          # type2
                   ((3, 7), -1),        # type1, type2
                   ((0,), -1),          # country1
                   ((4,), -1),          # country2
                   ((10,), -1),         # same_country
                   ((0, 4), -1),        # country1, country2
                   ((0, 4, 3, 7), -1),  # country1, country2, type1, type2
                   ((1,), -1),          # region1
                   ((5,), -1),          # region2
                   ((11,), -1),         # same_region
                   ((1, 5), -1),        # region1, region2
                   ((2,), -1),          # city1
                   ((6,), -1),          # city2
                   ((12,), -1),         # same_city
                   ((2, 6), -1),        # city1, city2
                   ((13,), -1),         # ip1
                   ((14,), -1),         # ip2
                   ((13, 14), -1)],     # ip1, ip2
          'constant': [((), 0)],  # constant (average)
          'distance': [((), 0),     # constant (average)
                       ((8,), 0)],  # distance
          'countries': [((), 0),        # constant (average)
                        ((0, 4), -1)],  # (country1, country2)
          }
  terms = model_to_terms[model]

  residuals = [s[:, TARGET].astype(float) for s in sets]
  if objective == 'LS':
      print [numpy.mean(r**2)**0.5 for r in residuals]
  elif objective == 'LAD':
      print [numpy.abs(r).mean() for r in residuals]
  elif objective == 'KT':
      print [1.0 for r in residuals]
  else:
      raise NotImplementedError
  sys.stdout.flush()

  #bottom = sets[0][:, TARGET].min()  # never allow predictions lower than this
  saved_params = []

  for feature_indices, regularization in terms:
    feature_indices = list(feature_indices)
    # strides of an array with ndim=len(feature_indices), that would contain
    # all combinations of features
    strides = numpy.array([numpy.prod(sizes[feature_indices[:i]]) for i in xrange(len(feature_indices) + 1)], dtype='uint64')
    # Represent the features of each example by a single integer,
    # by multiplying the values of features by the corresponding
    # stride, so that a different combination will give a different
    # total.
    features = [numpy.sum(s[:, feature_indices] * strides[:-1], axis=1) for s in sets]
    regression = 8 in feature_indices  # hardcoded (only on distance)

    if regression:  # ridge regression
      assert len(feature_indices) == 1, "cannot combine regression with something else"
      # Features from the training set
      floats = features[0].astype(float)

      if objective == 'LS':
        ATA = numpy.empty((2, 2))
        ATA[0, 0] = (floats**2).sum()
        ATA[1, 1] = len(floats)
        ATA[0, 1] = ATA[1, 0] = floats.sum()
        ATb = numpy.empty((2, 1))
        ATb[0, 0] = (floats*residuals[0]).sum()
        ATb[1, 0] = residuals[0].sum()

        def regularize(coefficient, update=False):
          m, b = numpy.linalg.solve(ATA + numpy.diag([float(coefficient)]*2), ATb)
          #print 'm=%f, b=%f' % (m, b)
          result = residuals[1] - m * features[1] - b
          if update:
            saved_params.append((m, b, coefficient))
            for i, f in enumerate(features):
              residuals[i] -= m * f + b
          return result
      elif objective == 'LAD':
        def train_err(m_b):
            m, b = m_b
            err = numpy.abs(floats * m + b - residuals[0]).mean()
            #print 'm=%f, b=%f, err=%f' % (m, b, err)
            return err

        m, b = scipy.optimize.fmin(train_err, [3e-5, 0], xtol=1e-5, ftol=1e-5, disp=False)

        def regularize(coefficient, update=False):
            reg_m = m / (1 + coefficient)
            result = residuals[1] - reg_m * features[1] - b

            if update:
                saved_params.append((m, b, coefficient))
                for i, f in enumerate(features):
                    residuals[i] -= reg_m * f + b

            return result
      elif objective == 'KT':
        # Try to use Theil-Sen to compute it
        m, b, lo_slope, up_slope = scipy.stats.mstats.theilslopes(
                y=residuals[0][:10000], x=floats[:10000])

        #print 'm:', m, 'b:', b
        #print 'train error:', scipy.stats.kendalltau(
        #        residuals[0][:10000] - (m * floats[:10000] + b),
        #        floats[:10000])

        def regularize(coefficient, update=False):
            reg_m = m / (1 + coefficient)
            result = residuals[1] - reg_m * features[1] - b
            if update:
                saved_params.append((m, b, coefficient))
                for i, f in enumerate(features):
                    residuals[i] -= reg_m * f + b
            return result
      else:
        raise NotImplementedError

    elif strides[-1] < 2000:  # dense
      params = numpy.zeros(strides[-1], dtype='float32')
      number = numpy.zeros(strides[-1], dtype='int32')

      if objective == 'LS':
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
      elif objective in  ('LAD', 'KT'):
        # list of residuals corresponding to each value of features
        values = [[] for f in range(strides[-1])]
        for i, f in enumerate(features[0]):  # training set
            values[f].append(residuals[0][i])
            number[f] += 1
        for f in range(strides[-1]):
            if values[f]:
                params[f] = numpy.median(values[f])

        def regularize(coefficient, update=False):
            # TODO: make sure that's what we want
            params_adjusted = (params * number) / (number + coefficient)
            result = residuals[1] - params_adjusted[features[1]]
            if update:
                saved_params.append((params_adjusted, number, coefficient))
                for i, f in enumerate(features):
                    residuals[i] -= params_adjusted[f]
            return result
      else:
          raise NotImplementedError

    else:  # sparse
      params = defaultdict(float)
      number = defaultdict(int)

      if objective == 'LS':
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
      elif objective in ('LAD', 'KT'):
        # contains the residuals corresponding ot each value of features
        values = defaultdict(list)  # default is an empty list
        for i, f in enumerate(features[0]):
            values[f].append(residuals[0][i])
            number[f] += 1
        for f in values.keys():
            params[f] = numpy.median(values[f])

        def regularize(coefficient, update=False):
            params_adjusted = params.copy()
            for f, n in number.iteritems():
                params_adjusted[f] = (params[f] * n) / (n + coefficient)
            result = residuals[1].copy()
            for j, ff in enumerate(features[1]):
                if ff in params_adjusted:
                    result[j] -= params_adjusted[ff]
            if update:
                saved_params.append((params_adjusted, number, coefficient))
                for i, f in enumerate(features):
                    for j, ff in enumerate(f):
                        if ff in params_adjusted:
                            residuals[i][j] -= params_adjusted[ff]
            return result
      else:
          raise NotImplementedError

    if regularization == -1:
      if objective == 'LS':
        def minimize(coefficient):
            if coefficient <= 1e-5: return 1e100
            residual = regularize(coefficient)
            error = numpy.mean(residual**2)**0.5
            #print coefficient, error
            #sys.stdout.flush()
            return error

      elif objective in ('LAD', 'KT'):
        def minimize(coefficient):
            if coefficient <= 1e-5:
                return 1e100
            residual = regularize(coefficient)
            error = numpy.abs(residual).mean()
            return error
      else:
          raise NotImplementedError

      regularization = scipy.optimize.fmin(minimize, 10.0, xtol=1e-1, ftol=1e-3, disp=False)[0]
    regularize(regularization, update=True)

    if objective == 'LS':
        print [numpy.mean(r**2)**0.5 for r in residuals], numpy.array(names)[feature_indices], regularization
    elif objective in ('LAD', 'KT'):
        print [numpy.abs(r).mean() for r in residuals], numpy.array(names)[feature_indices], regularization
    elif objective == 'KT':
        errs = []
        rng = numpy.random.RandomState(('2013', '05', '29'))
        for s, r in zip(sets, residuals):
            # Select only 50k samples, otherwise it takes too long,
            # and integers overflow into long int, which confuses numpy
            idx = rng.permutation(s.shape[0])[:50000]
            target = s[idx, TARGET]
            res = r[idx]
            pred = target - res
            err, pval = scipy.stats.kendalltau(pred, target)
            errs.append(err)
        print errs, numpy.array(names)[feature_indices], regularization
    else:
        raise NotImplementedError
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
  pylab.figure()
  pylab.plot(examples)
  pylab.legend(('target', 'prediction'))
  pylab.xlabel('test example')
  pylab.ylabel('latency (ms)')

  examples = a[:, :500]
  pylab.figure()
  pylab.scatter(*examples, s=5, c='black')
  limit = 0, examples.max()*1.1
  pylab.xlim(limit)
  pylab.ylim(limit)
  pylab.xlabel('target (ms)')
  pylab.ylabel('prediction (ms)')

  # plot prediction/target heat map on test data
  x = numpy.arange(30, 680, 10)
  y = numpy.arange(30, 680, 10)
  x_grid = numpy.resize(x, (len(y), len(x))).flatten()
  y_grid = numpy.resize(y, (len(x), len(y))).T.flatten()
  sigma = 18
  distribution = sum(numpy.exp(-0.5*((x_grid - v)**2 + (y_grid - w)**2)/sigma**2) for v, w in a.T)
  pylab.figure()
  pylab.imshow(distribution.reshape((len(y), len(x))), origin='lower', aspect='auto', extent=(x[0], x[-1], y[0], y[-1]))
  pylab.xlabel('target (ms)')
  pylab.ylabel('prediction (ms)')

  # plot prediction/target heat map on train data
  distribution = sum(numpy.exp(-0.5*((x_grid - v)**2 + (y_grid - w)**2)/sigma**2) for v, w in zip(sets[0][:, TARGET], sets[0][:, TARGET] - residuals[0]))
  pylab.figure()
  pylab.imshow(distribution.reshape((len(y), len(x))), origin='lower', aspect='auto', extent=(x[0], x[-1], y[0], y[-1]))
  pylab.xlabel('TRAIN target (ms)')
  pylab.ylabel('TRAIN prediction (ms)')

  def plot_distribution(domain, sigma, values, xlabel):
    distribution = sum(numpy.exp(-0.5*(domain - v)**2/sigma**2) for v in values)
    distribution /= distribution.sum() * (domain[1] - domain[0])
    pylab.figure()
    pylab.plot(domain, distribution)
    pylab.xlabel(xlabel)
    pylab.ylabel('P(error)')

  plot_distribution(numpy.arange(-800, 800, 0.5), 20.0, residuals[2], 'error (ms)')
  relative = 100.0 * abs(residuals[2]) / a[0, :]
  plot_distribution(numpy.arange(0, 350, 0.5), 10.0, relative, 'relative error (%)')

  # more stats
  ranges = [0, 100, 200, 300, 500, 1000, 5001]
  classes = numpy.array([[bisect.bisect_right(ranges, j) - 1 for j in i] for i in a])
  num_classes = len(ranges) - 1
  print 'L1, L2, relative errors (train)', numpy.mean(abs(residuals[0])), numpy.mean(residuals[0]**2)**0.5, 100 * numpy.mean(abs(residuals[0] / sets[0][:, TARGET]))
  print 'L1, L2, relative errors (test)', numpy.mean(abs(residuals[2])), numpy.mean(residuals[2]**2)**0.5, numpy.mean(relative)
  print 'class-wise:\n', numpy.array([numpy.mean([[abs(r), r**2] for r, c in zip(residuals[2], classes[0, :]) if c==k], axis=0)**[1, 0.5] for k in xrange(num_classes)])

  print 'classification accuracy', (classes[0, :] == classes[1, :]).mean()
  confusion = numpy.array([[((classes[0, :]==c1) & (classes[1, :]==c2)).sum() for c2 in xrange(num_classes)] for c1 in xrange(num_classes)])
  print 'confusion matrix:\n', confusion  # row = target, column = prediction
  pylab.figure()
  pylab.imshow((confusion.astype(float).T/confusion.sum(axis=1)).T, aspect='auto', interpolation='nearest', cmap=pylab.cm.gray, extent=(0.5, num_classes + 0.5, 0.5 + num_classes, 0.5))
  pylab.xlabel('predicted class')
  pylab.ylabel('target class')

  if PLOT_GEOIP:
      # plot distance (col. 8) vs target
      ex_target = sets[2][:500, TARGET]
      ex_dist = sets[2][:500, 8]
      pylab.figure()
      pylab.scatter(ex_target, ex_dist, s=5, c='black')
      pylab.xlim((0, ex_target.max()*1.1))
      pylab.ylim((0, ex_dist.max()*1.1))
      pylab.xlabel('target (ms)')
      pylab.ylabel('GeoIP distance (m)')

      # plot distance (col. 8) vs target (all samples)
      ex_target = sets[2][:, TARGET]
      ex_dist = sets[2][:, 8]
      pylab.figure()
      pylab.scatter(ex_target, ex_dist, s=5, c='black')
      pylab.xlim((0, ex_target.max()*1.1))
      pylab.ylim((0, ex_dist.max()*1.1))
      pylab.xlabel('target (ms)')
      pylab.ylabel('GeoIP distance (m)')

      # Same, as heat map, with more data
      # Try to get around 100 grid divisions
      tgt = sets[2][:, TARGET]
      dist = sets[2][:, 8]
      tgt_max = tgt.max() * 1.1
      dist_max = dist.max() * 1.1
      x = numpy.arange(0, tgt_max, tgt_max / 100)
      y = numpy.arange(0, dist_max, dist_max / 100)
      x_grid = numpy.resize(x, (len(y), len(x))).flatten()
      #print 'len(x):', len(x)
      #print 'x_grid.shape:', x_grid.shape
      y_grid = numpy.resize(y, (len(x), len(y))).T.flatten()
      #print 'len(y):', len(y)
      #print 'y_grid.shape:', y_grid.shape
      sigma_x = 1.8 * tgt_max / 100
      sigma_y = 1.8 * dist_max / 100
      distribution = sum(numpy.exp(-0.5*((x_grid - x_)**2/sigma_x**2 + (y_grid - y_)**2/sigma_y**2)) for x_, y_ in zip(tgt, dist))
      print 'distribution.shape:', distribution.shape
      pylab.figure()
      pylab.imshow(distribution.reshape((len(y), len(x))), origin='lower', aspect='auto', extent=(x[0], x[-1], y[0], y[-1]))
      pylab.xlabel('target (ms)')
      pylab.ylabel('GeoIP distance (m)')

      # Closer zoom on the interesting part
      # Reuse the same parameters for target as the first heat map
      x = numpy.arange(30, 680, 10)
      sigma_x = 18
      x_grid = numpy.resize(x, (len(y), len(x))).flatten()
      y_grid = numpy.resize(y, (len(x), len(y))).T.flatten()
      distribution = sum(numpy.exp(-0.5*((x_grid - x_)**2/sigma_x**2 + (y_grid - y_)**2/sigma_y**2)) for x_, y_ in zip(tgt, dist))
      pylab.figure()
      pylab.imshow(distribution.reshape((len(y), len(x))), origin='lower', aspect='auto', extent=(x[0], x[-1], y[0], y[-1]))
      pylab.xlabel('target (ms)')
      pylab.ylabel('GeoIP distance (m)')

  print 'ordering accuracy', numpy.mean([correct_order(*numpy.random.randint(num_examples, size=2)) for k in xrange(500000)])
  choices = [(classes[0, :]==c).nonzero()[0] for c in xrange(num_classes)]
  choice = lambda x: x[numpy.random.randint(len(x))]
  symmetrize = lambda A: (A + A.T) * 0.5
  print 'class-wise:\n', symmetrize(numpy.array([[numpy.mean([correct_order(choice(choices[c1]), choice(choices[c2])) for k in xrange(100000)]) for c2 in xrange(num_classes)] for c1 in xrange(num_classes)]))

  if 1:
    oa_means = []
    n_considered = 0
    n_ignored = 0
    # Records all ips contacted by ip1
    ip1_to_idx = {}
    ip1_idx = names.index('ip1')
    ip2_idx = names.index('ip2')
    for i, ex in enumerate(sets[2]):
        cur_ip1 = ex[ip1_idx]
        if cur_ip1 not in ip1_to_idx:
            ip1_to_idx[cur_ip1] = []
        ip1_to_idx[cur_ip1].append(i)

    for cur_ip1 in sorted(ip1_to_idx.keys()):
        idx_list = ip1_to_idx[cur_ip1]
        if len(set(idx_list)) == 1:
            continue

        ip1_oa = []
        for i in range(len(idx_list)):
            ip2_i = sets[2][idx_list[i], ip2_idx]
            tgt_i = sets[2][idx_list[i], TARGET]
            # round to make sure there is no information from tgt_i
            # still in pred_i, because of the way its computed.
            pred_i = (tgt_i - residuals[2][idx_list[i]]).round()
            for j in range(i + 1, len(idx_list)):
                ip2_j = sets[2][idx_list[j], ip2_idx]
                if ip2_i == ip2_j:
                    # This case does not matter, because the same IP
                    # will always be chosen, whatever the algorithm.
                    n_ignored += 1
                    continue

                n_considered += 1
                tgt_j = sets[2][idx_list[j], TARGET]
                pred_j = (tgt_j - residuals[2][idx_list[j]]).round()

                if tgt_i == tgt_j:
                    # Both answers are OK, it does not matter which IP
                    # is selected. This case will not appear when there
                    # is a minimal delay.
                    ip1_oa.append(1)
                elif pred_i == pred_j:
                    # If we reverse i and j, we should have the same result,
                    # so 0.5
                    ip1_oa.append(0.5)
                else:
                    ip1_oa.append(cmp(pred_i, pred_j) == cmp(tgt_i, tgt_j))
            if ip1_oa:
                oa_means.append(numpy.mean(ip1_oa))
    #print '%d samples, %d sources, %d sources with > 1 dest, %d pairs of samples considered' % (len(sets[2]), len(ip1_to_idx), len(oa_means), n_considered)
    #print '(%d pairs of samples ignored because same source and dest IP)' % n_ignored
    print 'ordering accuracy normalized by source IP: %f' % numpy.mean(oa_means)

    #import ipdb; ipdb.set_trace()

  ## Ordering accuracy limited to data pairs where abs(a[0, i] - a[0, j]) <= x
  if PLOT_ACCURACY:
    def sample_pair_from(data, min_delay=0.):
        ex1 = choice(data)
        ex2 = choice(data)
        if abs(ex1[0] - ex2[0]) <= min_delay:
            return (None, None)
        else:
            return ex1, ex2

    plot_data = []
    for min_delay in xrange(0, 1000, 10):
        accuracies = [cmp(ex1[0], ex2[0]) == cmp(ex1[1], ex2[1])
                      for ex1, ex2 in [sample_pair_from(a.T, min_delay=min_delay)
                                       for i in xrange(500000)]
                      if ex1 is not None]
        plot_data.append(numpy.mean(accuracies))
    pylab.figure()
    pylab.plot(numpy.arange(0, 1000, 10), plot_data)
    pylab.xlabel('min delay considered (ms)')
    pylab.ylabel('ordering accuracy')

  try:
    pylab.show()
  except:
    pass


def export_datasets(extra_proportion=0):
  rows = []
  # Keep (ip1, ip2, latency) triplets already seen in a set,
  # to remove duplicates
  already_seen = set()
  n_duplicates = 0

  for i, s in enumerate(test_data.data_sets):
    print '\r%i/%i' % (i+1, len(test_data.data_sets)),
    sys.stdout.flush()
    for d in test_data.load_data_from_pkl(i):
      origin = d['origin_geoip']
      destination = d['destination_geoip']
      _id = (d['origin_ip'], d['destination_ip'], d['peer_latency'])
      if _id in already_seen:
          n_duplicates += 1
          continue

      already_seen.add(_id)
      row = [origin['country_code'], origin['region_name'], origin['city'], d['origin_type'],
             destination['country_code'], destination['region_name'], destination['city'], d['destination_type'],
             d['distance'], d['peer_latency'], 0, 0, 0,
             utils.get_int_from_ip(d['origin_ip']), utils.get_int_from_ip(d['destination_ip'])]
      rows.append(row)

  print 'n_duplicates: %d / %d' % (n_duplicates, n_duplicates + len(rows))

  extra_rows = []
  if extra_proportion > 0:
    for i in 'AC3', 'ACR':
      n_extra_duplicates = 0
      for l in file('../data/ubi-data2/%s.csv' % i).readlines():
        print '\r%i' % len(extra_rows),
        sys.stdout.flush()
        fields = l.rstrip().split(',')
        ip1, ip2, ping12, ping21 = fields[4], fields[14], float(fields[24]), float(fields[25])
        geo1 = utils.get_geoip_data(ip1)
        geo2 = utils.get_geoip_data(ip2)
        try:
          distance = utils.geoip_distance(geo1, geo2)
        except:
          distance = 8927011  # average distance
        if geo1 is None: geo1 = defaultdict(unicode)
        if geo2 is None: geo2 = defaultdict(unicode)
        try:
          int_ip1, int_ip2 = map(utils.get_int_from_ip, (ip1, ip2))
        except:
          continue
        type1, type2 = 3, 3  # different from mobile data

        _id = (ip1, ip2, ping12)
        if _id not in already_seen:
            already_seen.add(_id)
            row = [geo1['country_code'], geo1['region_name'], geo1['city'], type1,
                   geo2['country_code'], geo2['region_name'], geo2['city'], type2,
                   distance, ping12, 0, 0, 0, int_ip1, int_ip2]
            extra_rows.append(row)  # connection 1->2
        else:
            n_extra_duplicates += 1

        _id = (ip2, ip1, ping21)
        if _id not in already_seen:
            already_seen.add(_id)
            row = [geo2['country_code'], geo2['region_name'], geo2['city'], type2,
                   geo1['country_code'], geo1['region_name'], geo1['city'], type1,
                   distance, ping21, 0, 0, 0, int_ip2, int_ip1]
            extra_rows.append(row)  # connection 2->1
        else:
            n_extra_duplicates += 1
      print 'in extra (%s), n_duplicates: %d / %d' % (
              i,
              n_extra_duplicates,
              n_extra_duplicates + len(extra_rows))
    print 'ok'

  countries, regions, cities = set(), set(), set()

  for rows_ in rows, extra_rows:
    for r in rows_:
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

  for rows_ in rows, extra_rows:
    for i, r in enumerate(rows_):
      print '\r%i/%i' % (i+1, len(rows_)),
      sys.stdout.flush()
      t = tuple(r)
      r[0] = bisect.bisect_left(countries, t[0])    # country1
      r[1] = bisect.bisect_left(regions, t[0:2])    # region1
      r[2] = bisect.bisect_left(cities, t[0:3])     # city1
      r[3] += 1                                     # type1 (avoid -1)
      r[4] = bisect.bisect_left(countries, t[4])    # country2
      r[5] = bisect.bisect_left(regions, t[4:6])    # region2
      r[6] = bisect.bisect_left(cities, t[4:7])     # city2
      r[7] += 1                                     # type2 (avoid -1)
      r[8] = int(numpy.round(t[8]))                 # distance
      r[9] = int(numpy.round(t[9]))                 # latency
      r[10] = int(r[0]==r[4])  # same country
      r[11] = int(r[1]==r[5])  # same region
      r[12] = int(r[2]==r[6])  # same city
      # r[13]: ip1
      # r[14]: ip2
    print 'ok'

  # Split data when the data is still ordered form older to newer
  data = numpy.array(rows, dtype='uint32')
  bounds = 0, 0.8*len(data), 0.9*len(data), len(data)
  train, valid, test = [data[bounds[i]:bounds[i+1]] for i in 0, 1, 2]
  # Then, shuffle valid and test
  numpy.random.seed([2013, 05, 27, 1])
  numpy.random.shuffle(valid)
  numpy.random.shuffle(test)

  if extra_proportion > 0:
    extra_data = numpy.array(extra_rows, dtype='uint32')
    numpy.random.shuffle(extra_data)
    number = int(len(train) * extra_proportion)
    train = numpy.concatenate((train, extra_data[:number]))

  # Then, shuffle train
  numpy.random.shuffle(train)

  for t in 'train', 'valid', 'test':
    #numpy.random.shuffle(locals()[t])
    filename = os.path.join(os.path.dirname(__file__), '..', 'data', 'sandbox2', t)
    cPickle.dump(locals()[t], file(filename, 'w'), cPickle.HIGHEST_PROTOCOL)
