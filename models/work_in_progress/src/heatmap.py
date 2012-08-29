'''
Created on Jul 3, 2012

@author: RaphaelBonaque <bonaque@crans.org>

Used to create some heatmap to represent the data or some MLP weight layers.

Some examples are provided as comments at the end of the file.
'''
from __future__ import division

import math
import Image
import numpy

from utils import get_int_from_ip
from test_data import DISTANCE, DESTINATION_GEOIP, ORIGIN_IP, DESTINATION_IP,\
    PEER_LATENCY, ORIGIN_GEOIP

class Heatmap:
    """
    The main class -others are based on it-.
    
    You initialize it a to a certain size and then add (using the add method) 
    points by giving there coordinates and value.
    """
    
    def __init__(self,x_size,y_size,y_symmetry=False):
        self.map = numpy.zeros([x_size,y_size])
        self.frequency = numpy.zeros([x_size,y_size])
        self.value = []
        self.x_size = x_size
        self.y_size = y_size
        self.y_symmetry = y_symmetry
        self.old_maps = []
        self.nb_points = 0
        
        
    def add(self,i,j,v):
        """Adds a point to the heatmap at coordinate (i,j) with value v."""
        if i >= self.x_size or i < 0 or j >= self.y_size or j < 0:
            print 'Out of bounds %d %d %f' %(i,j,v)
            return
        i = int(i)
        j = int(j)
        self.map[i,j] += v
        self.frequency[i,j] += 1
        self.nb_points += 1
    
    def get(self,i,j):
        """Gets the average value at coordinate (i,j)."""
        return self.map[i,j] / self.frequency[i,j]
    
    def get_frequency(self,i,j):
        """Gets the number of points added to (i,j)."""
        return self.frequency[i,j]
    
    def get_color(self,i,j):
        (r,g,b) =(0,0,0)
        if self.frequency[i,j]== 0: return (r,g,b)
        if self.map[i,j] == 0: return (r,g,b)
        coeff = 20000 * self.map[i,j] / self.frequency[i,j] 
        if coeff < 2:
            g = min(255,int(255 / coeff))
        if coeff > 1:
            r = min(255,int(2000 / coeff))
        return (r,g,b)
    
    def to_image(self,threshold = 0.5):
        """
        Returns an image of the average value of each cell. (If you find the 
        orientation counter-intuitive don't hesitate to use change the 'symetry'
        variable).
        """
        value_img = Image.new("RGB",(self.x_size,self.y_size))
        value_obj = value_img.load()
        for i in xrange(self.x_size):
            for j in xrange(self.y_size):
                if self.frequency[i,j] > threshold :
                    if self.y_symmetry :
                        value_obj[i,self.y_size-1-j] = self.get_color(i,j)
                    else:
                        value_obj[i,j] = self.get_color(i,j)
        return value_img
    
    def density_image(self,factor=100000):
        """
        Returns an image of the frequency each cell was used (and not it's 
        average value)
        
        The color scheme is the same as in to_image but instead of the frequency
        you get the frequency divided by 'factor' (this allows to correct the 
        color scale by hand).
        """
        old_freq = self.frequency
        old_value = self.map
        
        self.frequency = numpy.ones([self.x_size,self.y_size])
        self.map = old_freq / float(factor)
        
        try:
            value_img = self.to_image()
        finally:
            self.frequency = old_freq
            self.map = old_value
        
        return value_img
            
    def save(self):
        self.old_maps.append((self.map,self.frequency))
    
    def diffuse(self,n):
        """
        Use this method to average the point on there neighbors in a square of
        size 2n x 2n (but the diffusion coefficient is actually proportional to
        the usual distance.
        
        By nature this method becomes very slow when n and the size of the 
        heatmap increases.
        """
        new_map = numpy.zeros([self.x_size,self.y_size])
        new_frequency = numpy.zeros([self.x_size,self.y_size])
        
        for u in xrange(self.x_size):
            for v in xrange(self.y_size):
                for i in xrange(max(0,u - n),min(self.x_size -1, u + n)):
                    for j in xrange(max(0,v - n),min(self.y_size -1, v + n)):
                        dist = math.sqrt((i-u)**2 +(j-v)**2)
                        new_map[i,j] += self.map[u,v] / dist
                        new_frequency[i,j] += self.frequency[u,v] / dist
        
        self.save()
        self.map = new_map
        self.frequency = new_frequency
    
    def cross_diffuse(self,n=1,diffuse=1./4):
        """
        Work like diffuse but in a cross pattern (not in a square one).
        Does n passes where each point diffuse the fraction 'diffuse' of its 
        frequency to his four 'cross-neighbors'.
        """
        for _ in xrange(n):
            new_map = numpy.zeros([self.x_size,self.y_size])
            new_frequency = numpy.zeros([self.x_size,self.y_size])
            
            def add_factor(u,v,a,b,factor):
                new_map[a,b] += self.map[u,v] * factor
                new_frequency[a,b] += self.frequency[u,v] * factor
            
            for u in xrange(1,self.x_size-1):
                for v in xrange(1,self.y_size-1):
                        add_factor(u,v,u-1,v,diffuse/4.)
                        add_factor(u,v,u,v-1,diffuse/4.)
                        add_factor(u,v,u+1,v,diffuse/4.)
                        add_factor(u,v,u,v+1,diffuse/4.)
                        add_factor(u,v,u,v,1-diffuse)
            
            self.save()
            self.map = new_map
            self.frequency = new_frequency

class BoundedMap(Heatmap):
    """
    A heatmap that automatically do an affirm transform from the original 
    coordinate to the heatmap coordinate.
    """
    
    def __init__(self,mapbounds,arraybounds,y_symmetry=False):
        Heatmap.__init__(self, arraybounds[0], arraybounds[1],y_symmetry)
        self.mapsbounds = mapbounds
    
    def add(self,x,y,v):
        bounds = self.mapsbounds
        new_x = self.x_size * (float(x) - bounds[0])  / (bounds[2]-bounds[0])
        new_y = self.y_size * (float(y) - bounds[1])  / (bounds[3]-bounds[1]) 
        Heatmap.add(self, new_x, new_y, v)


class LatencyMap(Heatmap):
    """
    Useful to show the average latency/distance depending on the location.
    """
    def __init__(self,x_size,y_size,):
        Heatmap.__init__(self, x_size, y_size, True)
    
    def add(self,entry):
        try:
            coeff = float(entry[PEER_LATENCY])/entry[DISTANCE]
        except ZeroDivisionError:
            return
        for geoip in [entry[ORIGIN_GEOIP],entry[DESTINATION_GEOIP]]:
            i = int((self.x_size-1) *(geoip['longitude']/360. + 0.5))
            j = int((self.y_size-1) *(geoip['latitude']/180. + 0.5))
            Heatmap.add(self, i, j, coeff)

class IpMap(Heatmap):
    """
    Shows the average latency between ip zones. (density show the more active 
    ones)
    """
    def add(self,entry):
        ip1 = get_int_from_ip(entry[ORIGIN_IP])
        i = ip1 * (self.x_size-1) / math.pow(256.,4)
        ip2 = get_int_from_ip(entry[DESTINATION_IP])
        j = ip2 * (self.y_size-1) / math.pow(256.,4)
        value = entry[PEER_LATENCY]
        Heatmap.add(self,i,j,value)

    def get_color(self,i,j):
        if self.frequency[i,j]== 0: return 0
        value = self.get(i,j)
        return self.color_scale(value)
    
    def density_image(self):
        old_freq = self.frequency
        old_value = self.map
        
        self.frequency = numpy.ones([self.x_size,self.y_size])
        self.map = old_freq * 75.
        
        try:
            value_img = self.to_image()
        finally:
            self.frequency = old_freq
            self.map = old_value
        
        return value_img
    
    
    def color_scale(self,value):
        
        #Blue perturbation of high frequency : T = 25
        blue_perturbation = numpy.array([0,0,2.5])
        high_frequency_blue = (value % 20) * blue_perturbation
        
        #Blue Green scale : - infinity = super blue 0 = black + infinity = super green
        if value < -1:
            blue_green_scale = numpy.log(-value) * numpy.array([0,0,4])
        elif value <= 1:
            blue_green_scale = numpy.array([0,0,0])
        else:
            blue_green_scale = numpy.log(value) * numpy.array([0,4,0])
        
        #A yellow/red pulse
        bounds = [0.,1500.]
        red_prop_at_bounds = [0.1,2.3]
        if value > bounds[0] and value < bounds[1]:
            red_prop = red_prop_at_bounds[0] + ((red_prop_at_bounds[1]-red_prop_at_bounds[0])*(value - bounds[0])/(bounds[1]-bounds[0]))
            yellow_prop = max(0,1 - red_prop)
            intensity = 1 - value/bounds[1]
            redish_pulse = 255* intensity * numpy.array([red_prop + yellow_prop,yellow_prop,0])
        else:
            redish_pulse = numpy.array([0,0,0])
        
        #
        brute_color = high_frequency_blue + blue_green_scale + redish_pulse
        
        #Desaturate
        if brute_color.max() > 255:
            brute_color = brute_color*255./brute_color.max()
        return tuple(numpy.cast['uint8'](brute_color))



"""
heat = ht.BoundedMap([0,0,2e7,2000],[2048,2048],True)

for entry in merged:
    dist = entry[DISTANCE]
    lat = entry[PEER_LATENCY]
    heat.add(dist,lat,1)

"""

"""
scale = heatmap.IpMap(4000,10)
for i in xrange(4000):
    for j in xrange(10):
        scale.map[i,j] = i
        scale.frequency[i,j] = 1
scale_img = scale.to_image()
scale_img.show()
"""


"""
hmap = LatencyMap(1024,780)
for entry in get_merged():
    hmap.add(entry)

img = hmap.to_image()
img.show()
"""        