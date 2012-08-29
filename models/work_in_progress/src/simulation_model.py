'''
Created on Jul 30, 2012

@author: Raphael Bonaque <bonaque@crans.org>

A model that intends to simulate (determinstically) pingd between ip addresses
'''
from utils import coord_distance, get_ip_from_int
import numpy
import numpy.random as random
from test_data import ORIGIN_IP, DESTINATION_IP, PEER_LATENCY, ORIGIN_GEOIP,\
    DESTINATION_GEOIP, DISTANCE, split_data_from_dict_array
from heatmap import BoundedMap

class determinist_latency_simulation():
    def __init__(self):
        
        self.big_geoip_noise_fraction = 0.2
        self.big_geoip_noise_distance = 10
        
        self.wifi_fraction = 0.10
        
        self.wifi_latency_mu = 10
        self.wifi_latency_sigma = 200
        self.wifi_granurality = 18
        self.max_normal_wifi_latency = 900
        
        self.router_router_speed = 7.5e4
        self.peer_router_speed = 2.0e3
        
        self.wi_latency_mu = 5
        self.wi_latency_sigma = 10
        
        self.isp_number = 7
        self.end_router_per_isp = 2000
        #self.global_router_number = 1000
        
        self.end_router_map = numpy.zeros([self.isp_number * self.end_router_per_isp,self.isp_number * self.end_router_per_isp])
        self.router_coordinates = []
        
        for _ in xrange(self.isp_number * self.end_router_per_isp):
            lat_ = 180 * random.random() - 90
            long_ = 360 * random.random() -180
            self.router_coordinates.append((lat_,long_))
    
    def is_wifi(self,ip):
        return (100*self.wifi_fraction > (49 * ip + 36) % 100)
    
    def get_isp(self,ip):
        return ((3*(ip/255))+ 4) % self.isp_number
    
    def get_end_node_latency(self,ip):
        if self.is_wifi(ip) :
            wifi_quality = self.max_normal_wifi_latency * float((53 * ip + 22) % self.wifi_granurality) / self.wifi_granurality
            noise = random.normal(self.wifi_latency_mu,self.wifi_latency_sigma)
            return max(0,noise + wifi_quality)
        else:
            noise = max(0,random.normal(self.wi_latency_mu,self.wi_latency_sigma))
            return noise
    
    def get_ip_coordinate(self,ip):
        lat_ = ((47 *(ip/255) + 91) % 179) -89 
        long_ =  ((68 *(ip/255) + 23) % 359) -179 
        return (lat_,long_)
    
    def get_noisy_coordinate(self,ip):
        
        if self.big_geoip_noise_fraction * 631 >= (103 * (ip/255) + 139)% 631:
        
            lat_pre_noise = (151*(ip/255) + 59) % 233 - 117
            long_pre_noise = (29*(ip/255) + 89) % 239 - 119
            
            total_pre_noise = 0.1 + float(abs(lat_pre_noise) + abs(long_pre_noise))
            
            lat_noise = self.big_geoip_noise_distance * lat_pre_noise / total_pre_noise
            long_noise = self.big_geoip_noise_distance * lat_pre_noise / total_pre_noise
            
            lat_ = ((47 *(ip/255) + 91) % 179) -89 + lat_noise
            long_ =  ((68 *(ip/255) + 23) % 359) -179 + long_noise
            return (lat_,long_)
        else:
            return self.get_ip_coordinate(ip)
    
    def get_noisy_distance(self,ip1,ip2):
        ip1_lat,ip1_long = self.get_noisy_coordinate(ip1)
        ip2_lat,ip2_long = self.get_noisy_coordinate(ip2)
        return coord_distance(ip1_lat,ip1_long,ip2_lat,ip2_long)
    
    def get_nearest_router(self,ip):
        isp = self.get_isp(ip)
        
        ip_lat,ip_long = self.get_ip_coordinate(ip)
        
        argmin_router = isp * self.end_router_per_isp
        min_distance = coord_distance(
                ip_lat,ip_long,
                *self.router_coordinates[argmin_router]
            )
        for r in xrange(isp * self.end_router_per_isp +1,(isp+1) * self.end_router_per_isp):
            val = coord_distance(
                ip_lat,ip_long,
                *self.router_coordinates[r]
            )
            if val < min_distance:
                argmin_router = r
                min_distance = val
        return argmin_router
    
    def get_router_router_latency(self,r1,r2):
        l1,lg1 = self.router_coordinates[r1]
        l2,lg2 = self.router_coordinates[r2]
        return coord_distance(l1,lg1,l2,lg2)/self.router_router_speed
        
    def get_ip_router_latency(self,ip,router):
        l1,lg1 = self.get_ip_coordinate(ip)
        l2,lg2 = self.router_coordinates[router]
        return coord_distance(l1,lg1,l2,lg2)/self.peer_router_speed
    
    def predict(self,ip1,ip2):
        """
        Predicts in ms
        """
        end_nodes_latency = self.get_end_node_latency(ip1) + self.get_end_node_latency(ip2)
        r1 = self.get_nearest_router(ip1)
        r2 = self.get_nearest_router(ip2)
        routers_latency = self.get_router_router_latency(r1,r2)
        peers_routers_latency = self.get_ip_router_latency(ip1, r1) + self.get_ip_router_latency(ip2, r2)
        return end_nodes_latency + routers_latency + peers_routers_latency
    
    def get_ip_distance(self,ip1,ip2):
        ip1_lat,ip1_long = self.get_ip_coordinate(ip1)
        ip2_lat,ip2_long = self.get_ip_coordinate(ip2)
        return coord_distance(ip1_lat,ip1_long,ip2_lat,ip2_long)
    
    def print_stats(self,ip1,ip2):
        ip1_lat,ip1_long = self.get_ip_coordinate(ip1)
        ip2_lat,ip2_long = self.get_ip_coordinate(ip2)
        
        ip_distance = coord_distance(ip1_lat,ip1_long,ip2_lat,ip2_long)
        
        print "ips %f,%f to %f,%f : %f m" % (ip1_lat,ip1_long,ip2_lat,ip2_long,ip_distance)
        
        r1 = self.get_nearest_router(ip1)
        r2 = self.get_nearest_router(ip2)
        r1_lat,r1_long = self.router_coordinates[r1]
        r2_lat,r2_long = self.router_coordinates[r2]
        
        r_distance = coord_distance(r1_lat,r1_long,r2_lat,r2_long)
        
        print "routers %f,%f to %f,%f  : %f m" % (r1_lat,r1_long,r2_lat,r2_long,r_distance)
        
        print "%f , %f , %f" % (self.get_ip_router_latency(ip1,r1),self.get_router_router_latency(r1,r2),self.get_ip_router_latency(ip2,r2))
    
    def create_dict_from_ip(self,ip1,ip2):
        ip1_str = get_ip_from_int(ip1)
        ip2_str = get_ip_from_int(ip2)
        distance = self.get_noisy_distance(ip1,ip2)
        lat1,long1 = self.get_noisy_coordinate(ip1)
        lat2,long2 = self.get_noisy_coordinate(ip2) 
        latency = self.predict(ip1,ip2)
        return {
                ORIGIN_IP : ip1_str,
                DESTINATION_IP : ip2_str,
                PEER_LATENCY : latency,
                ORIGIN_GEOIP : {'latitude':lat1, 'longitude':long1},
                DESTINATION_GEOIP : {'latitude':lat2, 'longitude':long2},
                DISTANCE : distance
            }
    
    def create_random_batch(self,size):
        batch = []
        for _ in xrange(size):
            ip1 = random.randint(2**32)
            ip2 = random.randint(2**32)
            batch.append(self.create_dict_from_ip(ip1, ip2))
            
        return batch
    
    def create_slightly_connected_batch(self,size,servers_nb):
        batch = []
        servers = [random.randint(2**32) for _ in xrange(servers_nb)]
        for _ in xrange(size/servers_nb):
            ip = random.randint(2**32)
            for s in servers:
                batch.append(self.create_dict_from_ip(ip, s))
            ip2 = random.randint(2**32)
            batch.append(self.create_dict_from_ip(ip, ip2))
        
        return batch
    


"""

dls = determinist_latency_simulation()

distance,latency = split_data_from_dict_array(dls.create_random_batch(1000),[DISTANCE,PEER_LATENCY])

density = BoundedMap([])
for i,dist in enumerates(distance):
    density.add(dist,latency[i])

"""


"""
import simulation_model as sm
from theano_play import *

dls = sm.determinist_latency_simulation()
plot_distance_latency(dls.create_random_batch(5000))
plt.show()

"""

