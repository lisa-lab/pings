'''
Created on Jun 11, 2012

@author: RaphaelBonaque <bonaque@crans.org>

This file gather several small functions and glue like the functions to go from
int to ip or the geoilocalisation.
'''
import os, math, pygeoip

geoip_data = None
inited = False

def init_utils():
    global inited
    if inited == False:
        init_geoip()
        inited = True

def init_geoip():
    """
    Initializes the GeoIP component. We expect the GeoLiteCity.dat file to be
    located in the '../data' directory.
    """
    global geoip_data
    geoip_data = pygeoip.GeoIP(
        os.path.join(os.path.dirname(__file__),'..','data', 'GeoLiteCity.dat'),
        )#pygeoip.MEMORY_CACHE)


def get_geoip_data(ip_address):
    init_utils()
    global geoip_data
    try:
        geoip_result = geoip_data.record_by_addr(ip_address)
        
        if geoip_result is not None:
            # The pygeoip library doesn't use Unicode for string values
            # and returns raw Latin-1 instead (at least for the free
            # GeoLiteCity.dat data). This makes other module (like json)
            # that expect Unicode to be used for non-ASCII characters
            # extremely unhappy. The relevant bug report is here: 
            # https://github.com/appliedsec/pygeoip/issues/1
            #
            # As a workaround, convert all string values in returned
            # dict from Latin-1 to Unicode.
            for key, value in geoip_result.iteritems():
                if isinstance(value, str):
                    geoip_result[key] = value.decode('latin-1')
        
    except Exception, e:
        print "Exception", e
        geoip_result = None
    
    return(geoip_result)

def geoip_distance(geoip_1, geoip_2):
    """Returns a rather accurate distance in meters between the two given geoip."""
    phi_1 = geoip_1['latitude'] * math.pi /180
    phi_2 = geoip_2['latitude'] * math.pi /180
    lambda_1 = geoip_1['longitude'] * math.pi /180
    lambda_2 = geoip_2['longitude'] * math.pi /180
    
    mb1 = math.sin(phi_1) * math.sin(phi_2)
    mb2 = math.cos(phi_1) * math.cos(phi_2)*math.cos(lambda_1 - lambda_2)
    
    earth_radius = 6372800.
    try:
        approximate_dist = earth_radius * math.acos(mb1 + mb2)
    except:
        if mb1 + mb2 > 1 and mb1 + mb2 < 1.00000001:
            approximate_dist = 0
        else:
            raise Exception("This math module is too inaccurate please find another one ...")
    return approximate_dist;

def coord_distance(lat1, long1, lat2, long2):
    """Same as geoip_distance but with the coordinate only."""
    phi_1 = lat1 * math.pi /180
    phi_2 = lat2 * math.pi /180
    lambda_1 = long1 * math.pi /180
    lambda_2 = long2 * math.pi /180
    
    mb1 = math.sin(phi_1) * math.sin(phi_2)
    mb2 = math.cos(phi_1) * math.cos(phi_2)*math.cos(lambda_1 - lambda_2)
    
    earth_radius = 6372800.
    try:
        approximate_dist = earth_radius * math.acos(mb1 + mb2)
    except:
        if mb1 + mb2 > 1 and mb1 + mb2 < 1.00000001:
            approximate_dist = 0
        else:
            raise Exception("This math module is too inaccurate please find another one ...")
    return approximate_dist;

def is_geoip_accurate(geoip):
    """Tells if a geoip is accurate by checking it provides a city."""
    if geoip is None:
        return False
    if 'city' not in geoip:
        return False
    return (geoip['city'] <> None and geoip['city'] <> '')


def get_data_path(filename):
    return os.path.join(os.path.dirname(__file__),'..','data','ubi-data2',filename)

def get_sandbox_path(filename):
    return os.path.join(os.path.dirname(__file__),'..','data','sandbox2',filename)

def get_int_from_ip(ip):
    if type(ip) == list:
        return [get_int_from_ip(elem) for elem in ip]
    parts = ip.split('.')
    int_parts = [int(str_) for str_ in parts]
    return int_parts[3] + 256 * (int_parts[2] + 256 * (int_parts[1] + 256 * int_parts[0]))

def get_ip_from_int(entry):
    if type(entry) == list:
        return [get_ip_from_int(elem) for elem in entry]
    parts = []
    for _ in range(4):
        parts.insert(0,entry % 256)
        entry /= 256
    return ("%d.%d.%d.%d") %tuple(parts)

def get_data_from_file((filename, columns, skip_first), add_data, sieve):
    """Reads a file according to a preset columns scheme."""
    file_path = get_data_path(filename)
    sieved = []
    others = []
    
    with open(file_path,'r') as csv_file:
        try:
            for _ in range(skip_first):
                csv_file.next()
        except:
            raise Exception("Not enough lines in the file to skip the first %d ones." % (skip_first))
        line_n = 0
        for line in csv_file:
            line_n += 1
            entry = {}
            for j,column_content in enumerate(line.split(',')):
                entry[columns[j]] = column_content
            
            add_data(entry)
            
            if sieve(entry):
                sieved.append(entry)
            else : others.append(entry)
    return sieved,others
