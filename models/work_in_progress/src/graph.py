
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


sets = [cPickle.load(file('../data/sandbox2/' + t)) for t in 'train', 'valid', 'test']

for i, s in enumerate(sets):
  s[:, 3] += 1  # avoid -1
  s[:, 7] += 1
  
  s = numpy.hstack((s, numpy.zeros((len(s), 3), dtype=int)))  # add "same X" features
  s[:, -3] = s[:, 0] == s[:, 4]  # same country
  s[:, -2] = s[:, 1] == s[:, 5]  # same region
  s[:, -1] = s[:, 2] == s[:, 6]  # same city
  sets[i] = s


sizes = numpy.maximum(*[s.max(axis=0) for s in sets]) + 1

terms = [((), -1), ((8,), -1), ((3,), -1), ((7,), -1), ((3, 7), -1), ((0,), -1), ((4,), -1), ((10,), -1), ((0, 4), -1), ((1,), -1), ((5,), -1), ((11,), -1), ((1, 5), -1), ((2,), -1), ((6,), -1), ((12,), -1), ((2, 6), -1)]
names = 'country1', 'region1', 'city1', 'type1', 'country2', 'region2', 'city2', 'type2', 'distance', 'latency', 'same_country', 'same_region', 'same_city'

TARGET = names.index('latency')

residuals = [s[:, TARGET].astype(float) for s in sets]
print [numpy.mean(r**2)**0.5 for r in residuals]
sys.stdout.flush()

for feature_indices, regularization in terms:
  feature_indices = list(feature_indices)
  strides = numpy.array([numpy.prod(sizes[feature_indices[:i]]) for i in xrange(len(feature_indices) + 1)], dtype=int)
  features = [numpy.sum(s[:, feature_indices] * strides[:-1], axis=1) for s in sets]
  regression = 8 in feature_indices
  
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
        for i, f in enumerate(features):
          residuals[i] -= m * f + b
      return result
  
  elif strides[-1] < 5000:  # dense
    params = numpy.zeros(strides[-1], dtype='float32')
    number = numpy.zeros(strides[-1], dtype='int32')
    for i, f in enumerate(features[0]):  # training set
      params[f] += residuals[0][i]
      number[f] += 1
    def regularize(coefficient, update=False):
      params_adjusted = params / (number + coefficient)
      result = residuals[1] - params_adjusted[features[1]]
      if update:
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
        for i, f in enumerate(features):
          for j, ff in enumerate(f):
            if params_adjusted.has_key(ff):
              residuals[i][j] -= params_adjusted[ff]
      return residual_valid

  if regularization == -1:
    def minimize(coefficient):
      residual = regularize(coefficient)
      error = numpy.mean(residual**2)**0.5
      #print coefficient, error
      #sys.stdout.flush()
      return error      
    regularization = scipy.optimize.fmin(minimize, 1.0, xtol=1e-1, ftol=1e-3, disp=False)[0]
  regularize(regularization, update=True)

  print [numpy.mean(r**2)**0.5 for r in residuals], numpy.array(names)[feature_indices], regularization
  sys.stdout.flush()


a = numpy.array([sets[2][:, TARGET], sets[2][:, TARGET] - residuals[2]])
numpy.random.shuffle(a.T)
b = a.shape[1]
print numpy.mean(abs(a[0, :] - a[0, :].mean())), numpy.mean(abs(residuals[2]))  # L1 error
print numpy.mean([(lambda i, j : (a[0, i] > a[0, j]) == (a[1, i] > a[1, j]))(numpy.random.randint(b), numpy.random.randint(b)) for k in xrange(500000)])  # ordering error
examples = a[:, :200].T
#examples = sorted(examples, key=lambda x: x[1])
pylab.plot(examples)
pylab.legend(('target', 'prediction'))
pylab.xlabel('test example')
pylab.ylabel('latency (ms)')
pylab.show()


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
             d['distance'], d['peer_latency']]
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

  for i, r in enumerate(rows):
    print '\r%i/%i' % (i+1, len(rows)),
    sys.stdout.flush()
    t = tuple(r)
    r[0] = bisect.bisect_left(countries, t[0])
    r[1] = bisect.bisect_left(regions, t[0:2])
    r[2] = bisect.bisect_left(cities, t[0:3])
    r[4] = bisect.bisect_left(countries, t[4])
    r[5] = bisect.bisect_left(regions, t[4:6])
    r[6] = bisect.bisect_left(cities, t[4:7])
    r[-2] = int(numpy.round(t[-2]))
    r[-1] = int(numpy.round(t[-1]))

  data = numpy.array(rows, dtype='int32')
  numpy.random.shuffle(data)
  bounds = 0, 0.8*len(data), 0.9*len(data), len(data)
  train, valid, test = [data[bounds[i]:bounds[i+1]] for i in 0, 1, 2]
  for t in 'train', 'valid', 'test':
    filename = os.path.join(os.path.dirname(__file__), '..', 'data', 'sandbox2', t)
    cPickle.dump(locals()[t], file(filename, 'w'), cPickle.HIGHEST_PROTOCOL)



