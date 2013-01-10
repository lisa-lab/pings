
import commands
import cPickle
import glob
import numpy
import sys
import re



def merge():
  union = set.union(*[cPickle.load(file(f)) for f in glob.glob('addresses.*.pickle')])
  cPickle.dump(union, file('addresses.pickle', 'w'), cPickle.HIGHEST_PROTOCOL)
  return union



def main():
  try:
    THREAD_ID = sys.argv[1]
  except:
    print 'Usage: %s THREAD_ID' % __file__
    return
  FILENAME = 'addresses.%s.pickle' % THREAD_ID


  try:
    addresses = cPickle.load(file(FILENAME))
    print 'Loaded %i addresses' % len(addresses)
    sys.stdout.flush()
  except:
    addresses = set()


  while len(addresses) < 10000:
    candidate = '.'.join([str(numpy.random.randint(256)) for i in xrange(4)])
    if candidate in addresses: continue
    status, output = commands.getstatusoutput('ping -c 1 -w 2 %s' % candidate)
    if status: continue
    try:
      time = re.search('time=([0-9.]+) ms', output).groups()[0]
      assert float(time) > 10
    except:
      continue
    addresses.add(candidate)
    print 'added #', len(addresses), candidate, time, 'ms'
    sys.stdout.flush()

    if len(addresses) % 10 == 0:
      cPickle.dump(addresses, file(FILENAME, 'w'), cPickle.HIGHEST_PROTOCOL)
      print 'saved to', FILENAME
      sys.stdout.flush()


if __name__ == '__main__':
  main()

