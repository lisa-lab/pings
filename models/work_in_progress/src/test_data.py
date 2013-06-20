'''
Created on Jun 8, 2012

@author: RaphaelBonaque <bonaque@crans.org>

This file deals with the Ubisoft data :
_ parse the files given by Ubisoft (see data_sets and 
  get_data_from_file in utils)
_ rework the data and add other coming from the Geoip (add_basic_data)
_ filter them (basic_sieve, clean_set)
_ save/load them using pickle (load_data_from_pkl,)

'''

from numpy import random
import cPickle as pickle

from utils import get_geoip_data, geoip_distance, is_geoip_accurate,\
    get_data_from_file, init_utils, get_sandbox_path


"""The global variables used by default when loading the database or creating 
the learning sets. It might not be good practice to use global variable but it
can prove useful here"""
merged = None
testing_set = []
validation_set = []
training_set = []


#Listed below are all the fields that an entry of the dataset may contain
#Currently containing strings, it could be something else as long as they are 
#unique

ORIGIN_GEOIP = 'origin_geoip'
ORIGIN_TYPE = 'origin_type'
ORIGIN_IP = 'origin_ip'

DESTINATION_GEOIP = 'destination_geoip'
DESTINATION_TYPE = 'destination_type'
DESTINATION_IP = 'destination_ip'

PEER_LATENCY = 'peer_latency' #in ms

DISTANCE = 'distance'

#Depending on the file :
TIME = 'time'
PM_TIME = 'pm_time'

"""
The data and format of the data files from Ubisoft 
the structure is (filename, [columns identifier], number of first lines to skip)

This is used because the files formats are not homogeneous.
"""
data_sets = [
    (
        "iplatency-2012-00-00-to-2012-05-07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY],
        1
    ),
    #The time the ping was recorded at (in the database ?) was added here
    (
        "iplatency-2012-05-08-to-2012-05-09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        1
    ),
    #They stopped using the first line as an header
    (
        "iplatency-2012-05-15-to-2012-05-17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        0
    ),(
        "iplatency-2012-05-18-to-2012-05-22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        0
    ),(
        "iplatency-2012-05-23-to-2012-05-24.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        0
    ),(
        "iplatency-2012-05-25-to-2012-05-28.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        0
    ),(
        "iplatency-2012-05-29-to-2012-05-30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        0
    ),(
        "iplatency-2012-05-31-to-2012-06-01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        0
    ),(
        "iplatency-2012-06-02-to-2012-06-04.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        0
    ),(
        "iplatency-2012-06-05-to-2012-06-06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,TIME],
        0
    ),
    #At this point Ubi started using time in PM notation and reused the first 
    #line as header
    (
        "iplatency-2012-06-08.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-10.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-21.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-29.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-06-30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-12.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-19.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-20.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-07-31.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-08-02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-08-03.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-08-05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-08-17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-08-23.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-08-24.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-08-25.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_09_06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_09_07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_09_09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-24.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-25.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-26.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-27.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-28.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-09-30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-03.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-04.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-08.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-10.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency-2012-10-11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_12.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_19.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_20.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_21.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_10_22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_12.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_19.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_20.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_29.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_11_30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_03.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_04.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_08.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_10.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_12.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_19.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_21.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_23.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_25.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_26.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_27.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_28.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_29.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2012_12_31.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_03.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_04.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_08.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_10.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_12.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_19.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_20.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_21.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_23.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_25.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_26.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_27.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_28.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_29.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_01_31.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_03.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_04.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_08.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_10.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_12.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_20.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_21.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_23.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_24.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_25.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_26.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_27.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_02_28.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_04.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_08.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_10.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_12.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_19.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_20.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_21.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_23.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_24.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_25.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_26.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_27.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_28.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_29.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_03_31.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_03.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_04.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_08.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_10.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_19.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_20.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_21.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_23.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_24.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_25.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_26.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_27.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_28.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_29.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_04_30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_03.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_04.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_05.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_06.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_07.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_08.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_09.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_10.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_11.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_12.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_13.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_14.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_15.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_16.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_17.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_18.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_19.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_20.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_21.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_22.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_23.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_24.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_25.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_26.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_27.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_28.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_29.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_30.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_05_31.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_06_01.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_06_02.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    ),(
        "iplatency_2013_06_03.csv",
        [ORIGIN_IP,ORIGIN_TYPE,DESTINATION_IP,DESTINATION_TYPE,PEER_LATENCY,PM_TIME],
        1
    )
]

def add_basic_data(entry):
    """
    Add and format some basic data on a dict
    """
    entry[ORIGIN_TYPE] = int(entry[ORIGIN_TYPE])
    entry[DESTINATION_TYPE] = int(entry[DESTINATION_TYPE])
    entry[PEER_LATENCY] = float(entry[PEER_LATENCY])
    entry[ORIGIN_GEOIP] = get_geoip_data(entry[ORIGIN_IP])
    entry[DESTINATION_GEOIP] =  get_geoip_data(entry[DESTINATION_IP])
    try:
        entry[DISTANCE] = geoip_distance(entry[ORIGIN_GEOIP],entry[DESTINATION_GEOIP])
    except:
        entry[DISTANCE] = None

def basic_sieve(entry):
    """
    A very basic filter to determine which entries are useful and which aren't
    """
    return (
            entry[PEER_LATENCY] >= 0
        and
            entry[PEER_LATENCY] <= 5000
        and
            is_geoip_accurate(entry[ORIGIN_GEOIP])
        and
            is_geoip_accurate(entry[DESTINATION_GEOIP])
        )

def get_basic_data_from_set(set_num):
    """
    Load the Ubisoft provided file for a set, add some basic data to it and 
    split it between useful and useless according to basic_sieve.
    """
    global data_sets
    return get_data_from_file(data_sets[set_num], add_basic_data, basic_sieve)

def save_data_in_pkl(data, output_name):
    """
    Will erase any existing file with the same name
    """
    file_path = get_sandbox_path(output_name+".pkl")
    with open(file_path,'wb+') as output_file :
            pickle.dump(data, output_file, pickle.HIGHEST_PROTOCOL)

def load_data_from_pkl(filename):
    """
    Load the data saved using save_data_in_pkl. Can load several time the file
    from the data_sets without additional cost (if called using the set number)
    """
    global data_sets
    if type(filename) == int :
        if type(data_sets[int(filename)]) == tuple:
            return load_data_from_pkl("set_%d" % int(filename))
        else:
            return data_sets[int(filename)]
    file_path = get_sandbox_path(filename+".pkl")
    with open(file_path,'rb') as input_file :
        return pickle.load(input_file)

def rebuild_pkl(start=0):
    """
    Rebuild the pickle files using the specifications provide in data_sets.
    The data filtered as useful get saved as "set_X.pkl" where X is the index of
    the set. The other data are stored in "useless_X.pkl" (in the sandbox folder
    provided to utils).
    """
    global data_sets
    init_utils()
    for i,_ in enumerate(data_sets):
        if i>= start :
            print "Using set %d" %i
            usefull,useless = get_basic_data_from_set(i)
            save_data_in_pkl(usefull, "set_%d" %i)
            save_data_in_pkl(useless, "useless_%d" %i)

def replace_sets_from_pkl():
    """
    Load the saved pickle file and replace the global data_set with the loaded
    version. Can be call several time on the same without additional cost.
    """
    global data_sets
    for i,_ in enumerate(data_sets):
        print "loading set %d" %i
        data_sets[i] = load_data_from_pkl(i)

def split_data_from_dict_array(data,columns=[ORIGIN_IP,DESTINATION_IP,PEER_LATENCY]):
    """
    Filter data to only keep the specified columns. Doesn't work inplace but 
    output one list per column.(Return a tuple of list)
    """
    result = [[] for _ in columns]
    for entry in data:
        for i,title in enumerate(columns):
            result[i].append(entry[title])
    
    return tuple(result)

def split_types(data):
    """
    Split a set by couple (origin_type,destination_type). Return a dict
    """
    result = {}
    for i in xrange(-1,5):
        for j in xrange(-1,5):
            result[(i,j)] = []
    
    for entry in data:
        a = entry[ORIGIN_TYPE]
        b = entry[DESTINATION_TYPE]
        result[(a,b)].append(entry)
    
    return result


def get_ip_dict(data, add_symetry=True):
    """
    Return a dict where data is stored by ip
    """
    result = {}
    
    def add(ip1,ip2,val):
        try:
            e1 = result[ip1]
        except:
            result[ip1] = {}
            e1 = result[ip1]
        try:
            e1[ip2].append(val)
        except:
            e1[ip2] = [val]
        
    for entry in data:
        ip1 = entry[ORIGIN_IP]
        ip2 = entry[DESTINATION_IP]
        val = entry
        add(ip1,ip2,val)
        if add_symetry:
            add(ip2,ip1,val)
    
    return result

def sort_by_same_peer_ping(ip_dict):
    result = {}
    for ip1 in ip_dict.itervalues():
        for val_list in ip1.itervalues():
            try:
                result[len(val_list)].append(val_list)
            except:
                result[len(val_list)] = [val_list]
    return result



def clean_set(data_set):
    """
    Provide a subset of the original set which remove duplicated entry with 
    different timestamps.
    """    
    print "Cleaning data"
    
    seen = {}
    result = []
    
    for entry in data_set:
        ip1 = entry[ORIGIN_IP]
        ip2 = entry[DESTINATION_IP]
        latency = entry[PEER_LATENCY]
        no_duplicate = True
        if ip1 in seen:
            seen_ip1 = seen[ip1]
            if ip2 in seen_ip1:
                for previous_entry in seen_ip1[ip2]:
                    if previous_entry[PEER_LATENCY] == latency:
                        no_duplicate = False
                        break
                if no_duplicate:
                    seen_ip1[ip2].append(entry)
            else:
                seen_ip1[ip2] = [entry]
        else:
            seen[ip1] = {ip2:[entry]}
        
        if no_duplicate:
            result.append(entry)
    
    return result

def get_merged():
    """
    Load the data from file and return them one cleaned list
    """
    global merged
    
    if merged is None:
        replace_sets_from_pkl()
        
        part = []
        for set_ in data_sets:
            part.extend(set_)
        
        merged = clean_set(part)
    
    return merged

def pass_merged():
    #Returning the whole dataset might prove too much for python to print nicely
    #in interactive shell
    get_merged()
    return None

def get_at_least_n_pings_set(n):
    """
    Return the maximal subset of the original data set where each entry's origin
    ip appears at least n times.
    """
    
    merged = get_merged()
    
    by_ip = {}
    for entry in merged:
        try:
            by_ip[entry[ORIGIN_IP]] += 1
        except:
            by_ip[entry[ORIGIN_IP]] = 1
    
    results = []
    for entry in merged:
        if by_ip[entry[ORIGIN_IP]] > n:
            results.append(entry)
        
        return results


def create_learning_sets(extracting_function, dataset=None):
    """
    Creates the three set using respectively 10% 10% and the remaining 80% of 
    the data. The content of each set doesn't change between two executions 
    (unless you changed the data_set ...) but the order of their element do.
    """
    global testing_set
    global validation_set
    global training_set
    
    if dataset is None: 
        dataset = get_merged()
    
    testing_bound = int(0.1 * len(dataset))
    testing_set = [extracting_function(elem) for elem in dataset[0:testing_bound]]
    random.shuffle(testing_set)
    
    validation_bound = int(0.2* len(dataset))
    validation_set =  [extracting_function(elem) for elem in dataset[testing_bound:validation_bound]]
    random.shuffle(validation_set)
    
    training_set =  [extracting_function(elem) for elem in dataset[validation_bound:len(dataset)]]
    random.shuffle(training_set)


#If you directly load this file in python it rebuilds the data set.
if __name__ == '__main__':
    rebuild_pkl()
