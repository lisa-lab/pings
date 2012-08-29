'''
Created on Jun 18, 2012

@author: RaphaelBonaque <bonaque@crans.org>

Some play around with the models defined in "models".
It is actually here that you will find tests on the models and learning over
the different data

You will find at the end of the documents some used of the function declared 
here but remember that this whole file is just scripts that is used often 
enough to save. They are work in progress and not assured to work.

I think you'd rather use your own scripts to build upon and 
extend models.
'''

from __future__ import division

import math
import numpy
import time

import matplotlib
import matplotlib.pyplot as plt

import theano
from theano import tensor

import utils
from test_data import *
from models import GeneralMLP,    BatchesMLP


#Some functions used for the basic online MLP

def latency_transposition(x):
    return (4./(1+math.exp(-x/1000))-2)

def latency_inverse_transpo(y):
    if y >= 2:
        return 2000.
    return -1000*math.log(4./(y+2)-1)

def pre_encoding(data):
    ips = (utils.get_int_from_ip(data[ORIGIN_IP]),utils.get_int_from_ip(data[DESTINATION_IP]))
    latency = latency_transposition(data[PEER_LATENCY])
    return ips,latency

def mean_distance(model, dataset):
    coef = 1./ len(dataset)
    mean = 0.
    quadratic_mean = 0.
    mean_of_prediction = 0.
    mean_of_data = 0.
    for entry in dataset:
        ips,_ = pre_encoding(entry)
        latency = entry[PEER_LATENCY]
        prediction = latency_inverse_transpo(model.predict(ips))
        dif = math.fabs(prediction-latency)
        mean += coef * dif
        quadratic_mean += coef * (math.pow(dif,2))
        mean_of_prediction +=coef * prediction
        mean_of_data += coef * latency
    quadratic_mean = math.sqrt(quadratic_mean)
    return mean,quadratic_mean,mean_of_prediction,mean_of_data

def mean_distance_to_constant(constant, dataset):
    coef = 1./ len(dataset)
    mean = 0.
    quadratic_mean = 0.
    mean_of_prediction = 0.
    mean_of_data = 0.
    for entry in dataset:
        latency = entry[PEER_LATENCY]
        dif = math.fabs(constant-latency)
        mean += coef* dif
        quadratic_mean += coef*(math.pow(dif,2))
        mean_of_prediction +=coef* constant
        mean_of_data += coef* latency
    quadratic_mean = math.sqrt(quadratic_mean)
    return mean,quadratic_mean,mean_of_prediction,mean_of_data

def add_thermo(array, offset, value):
    return tensor.set_subtensor(array[offset:offset+value],1)

def add_one_shot(array, offset, value):
    return tensor.set_subtensor(array[offset+value:offset+value+1],1)

def create_mlp_model(end_sizes =[400, 200, 75], max_output =2., learning_rate = None, from_layers = None):
    
    origin_ip = tensor.scalar('origin ip', dtype='uint32')
    destination_ip = tensor.scalar('origin ip', dtype='uint32')
    origin_latitude = tensor.fscalar('origin latitude')
    origin_longitude = tensor.fscalar('origin longitude')
    destination_latitude = tensor.fscalar('destination latitude')
    destination_longitude = tensor.fscalar('destination longitude')
    origin_type = tensor.iscalar('origin_type')
    destination_type = tensor.iscalar('destination_type')
    distance = tensor.fscalar('distance')
    input_variables = [origin_ip,destination_ip,origin_latitude,origin_longitude,destination_latitude,destination_longitude,origin_type,destination_type,distance]
    
    latency = tensor.fscalar('latency')
    target_variables = [latency]
    
    #An upper bound of the maximum distance between two pairs (used to encode the distance)
    max_earth_distance = 22000000
    
    ip_size = 4*256
    coordinate_size = 45
    dest_size = 100
    type_size = 6
    input_size = 2*ip_size + 4*coordinate_size + 2*type_size + dest_size 
    
    sizes = [input_size]
    sizes.extend(end_sizes)
    
    def input_row_from_variables(ori_ip,dest_ip,ori_lat,ori_long,dest_lat,dest_long,ori_type,dest_type,dist):
        '''Create an input row for the MLP from the inputs'''
        
        input_row = tensor.zeros([input_size])
        
        offset = 0
        
        ips = [ori_ip,dest_ip]
        for ip in ips:
            for _ in range(4):
                input_row = add_one_shot(input_row, offset, tensor.mod(ip,256))
                ip = tensor.int_div(ip,256)
                offset += 256
        
        for lat_,long_ in [(ori_lat,ori_long),(dest_lat,dest_long)]:
            translated_lat = tensor.iround((coordinate_size-1)*(lat_/180 + 0.5))
            input_row = add_thermo(input_row, offset,translated_lat)
            offset += coordinate_size
            
            translated_long = tensor.iround((coordinate_size-1)*(long_/360 + 0.5))
            input_row = add_thermo(input_row, offset,translated_long)
            offset += coordinate_size
        
        for type_ in [ori_type,dest_type]:
            add_one_shot(input_row, offset, type_ +1)
            offset += type_size
        
        translated_dist = tensor.iround((dest_size-1)*(tensor.minimum(1,dist/max_earth_distance)))
        input_row = add_thermo(input_row, offset,translated_dist)
        
        #could be useful if we want to add something
        offset +=dest_size
        
        return input_row
    
    def threshold_function(x):
        return theano.tensor.tanh(x)
    
    def number_from_output(vector):
            number = threshold_function(vector).sum()
            number.name = "non format output"
            output_number = max_output * number
            output_number.name = "output_number"
            return [output_number]
    
    def latency_encoding(x):
        return [4./(1+tensor.exp(-x/1000))-2]
    
    def latency_decoding(y):
        expr3 = -1000*tensor.log(4./(y+2)-1)
        expr4 = tensor.switch(tensor.ge(y,2),3000.,expr3)
        expr5 = tensor.switch(tensor.ge(0,y),100*tensor.exp(y),expr4)
        return expr5
    
    costs = []
    hypers = []
    
    def hyper_cost(hyper):
        def simple_cost(predictions,targets,current_row):
                predicted_latency = predictions[0]
                target_latency = targets[0]
                return hyper * (predicted_latency-target_latency)**2
        return simple_cost
    
    for i in xrange(len(sizes)-1):
        hyper_param = theano.shared(1., "layer %d coef" %(i),)
        hypers.append(hyper_param)
        costs.append(hyper_cost(hyper_param))
    
    model = GeneralMLP(
                sizes = sizes,
                input_variables = input_variables,
                target_variables = target_variables,
                encoding_function = input_row_from_variables,
                decoding_function = number_from_output,
                hyper_parameters = hypers,
                additional_target_encoding = latency_encoding,
                additional_output_decoding = latency_decoding,
                cost_functions = costs,
                learning_rate = learning_rate,
                from_layers = from_layers,
            )
    
    return model


def create_autoencoder(input_size, intermediate_size, input_variable, input_encoding, cost = None):
    """
    Create a basic autoencoder
    """
    sizes = [input_size,intermediate_size,input_size]
    
    def output_identity(output):
        return output
    
    def sparsity_cost(output,target,intermediate_raw):
        return tensor.sqrt(intermediate_raw).sum()
    
    if cost is None:
        def output_cost(output,target,output_raw):
            target_encoded = input_encoding(target)
            dif = output_raw - target_encoded
            return tensor.sqr(dif).sum()
        cost = output_cost
    
    targ = input_variable.clone()
    
    model = GeneralMLP(
            sizes = sizes,
            input_variables = [input_variable],
            target_variables = [targ],
            encoding_function = input_encoding,
            decoding_function = output_identity,
            cost_functions = [sparsity_cost,cost]
        )
    
    return model


def quick_analyse(model, dataset):
    """
    Do a very basic evaluation of how a model performs over a dataset.
    
    The set format should be (input_var_1,input_var_2, ... ,input_var_n, target) 
    """
    coef = 1./ len(dataset)
    mean = 0.
    quadratic_mean = 0.
    mean_of_prediction = 0.
    mean_of_data = 0.
    if type(model) == float or type(model) == int:
        prediction = model
        for entry in dataset:
            target = entry[-1]
            dif = math.fabs(prediction-target)
            mean += coef*dif
            quadratic_mean += coef*(math.pow(dif,2))
            mean_of_prediction +=coef*prediction
            mean_of_data += coef* target
    else:
        for entry in dataset:
            target = entry[-1]
            prediction = model.predict(*entry[:-1])
            dif = math.fabs(prediction-target)
            mean += coef* dif
            quadratic_mean += coef*(math.pow(dif,2))
            mean_of_prediction +=coef*prediction
            mean_of_data += coef* target
    
    quadratic_mean = math.sqrt(quadratic_mean)
    
    print_pattern = (
        "mean : %f\n"
        "quadratic mean %f\n"
        "mean of prediction %f\n"
        "mean of dataset %f"
        )
    results = (mean,quadratic_mean,mean_of_prediction,mean_of_data)
    print print_pattern % results
    return results


def plot_distance_latency(distance,latency = None):
    """
    Plot (using matplotlib) a graph showing the latency as a function of the
    distance.
    """
    if latency is None:
        distance,latency = split_data_from_dict_array(distance,[DISTANCE,PEER_LATENCY])
    
    bbox = matplotlib.transforms.Bbox.from_bounds(0,0,math.pi*6500000,2000)
    plt.plot(distance,latency,',',clip_box=bbox)
    plt.axis([0,math.pi*6500000 , 0, 2000])

#Just a example of what the data contained in the data_set can be like.
test_sample = [{
        ORIGIN_IP : "132.204.25.184 ",
        DESTINATION_IP:"8.8.8.8",
        PEER_LATENCY : 44,
        ORIGIN_GEOIP : {'latitude':13.37, 'longitude':42.42},
        DESTINATION_GEOIP : {'latitude':42.42, 'longitude':13.37},
        DISTANCE : 4000000
    }]



def create_batch_mlp_model(training_set=training_set, validation_set=validation_set, batch_size=100, end_sizes=[1000, 1000, 100], max_output=2., learning_rate=None, from_layers=None):
    """
    Create an MLP working with batch.
    
    One or two things still need to be fixed (you can make it work but won't 
    work directly : I'll try to fix that in the next few days).
    """
    
    origin_ip = tensor.scalar('origin ip', dtype='uint32')
    destination_ip = tensor.scalar('origin ip', dtype='uint32')
    origin_latitude = tensor.fscalar('origin latitude')
    origin_longitude = tensor.fscalar('origin longitude')
    destination_latitude = tensor.fscalar('destination latitude')
    destination_longitude = tensor.fscalar('destination longitude')
    origin_type = tensor.iscalar('origin_type')
    destination_type = tensor.iscalar('destination_type')
    distance = tensor.fscalar('distance')
    latency = tensor.fscalar('latency')
    
    input_variables = [origin_ip,destination_ip,origin_latitude,origin_longitude,destination_latitude,destination_longitude,origin_type,destination_type,distance,latency]
    
    #An upper bound of the maximum distance between two pairs (used to encode the distance)
    max_earth_distance = 22000000
    
    ip_size = 4*256
    coordinate_size = 45
    dist_size = 100
    small_dist_size = 10
    type_size = 6
    input_size = 2*ip_size + 4*coordinate_size + 2*type_size + dist_size + small_dist_size
    
    sizes = [input_size]
    sizes.extend(end_sizes)
    
    def input_row_from_variables(ori_ip,dest_ip,ori_lat,ori_long,dest_lat,dest_long,ori_type,dest_type,dist,latency):
        '''Create an input row for the MLP from the inputs'''
        
        input_row = tensor.zeros([input_size])
        
        offset = 0
        
        ips = [ori_ip,dest_ip]
        for ip in ips:
            for _ in range(4):
                input_row = add_one_shot(input_row, offset, tensor.mod(ip,256))
                ip = tensor.int_div(ip,256)
                offset += 256
        
        for lat_,long_ in [(ori_lat,ori_long),(dest_lat,dest_long)]:
            translated_lat = tensor.iround((coordinate_size-1)*(lat_/180 + 0.5))
            input_row = add_thermo(input_row, offset,translated_lat)
            offset += coordinate_size
            
            translated_long = tensor.iround((coordinate_size-1)*(long_/360 + 0.5))
            input_row = add_thermo(input_row, offset,translated_long)
            offset += coordinate_size
        
        for type_ in [ori_type,dest_type]:
            input_row = add_one_shot(input_row, offset, type_ +1)
            offset += type_size
        
        translated_dist = tensor.iround((dist_size-1)*(tensor.minimum(1,dist/max_earth_distance)))
        input_row = add_thermo(input_row, offset,translated_dist)
        offset +=dist_size
        
        translated_dist = tensor.iround((small_dist_size-1)*(tensor.minimum(1,dist/max_earth_distance)))
        input_row = add_thermo(input_row, offset,translated_dist)
        
        #could be useful if we want to add something
        offset +=small_dist_size
        
        return input_row
       
    input_encoding = theano.function(inputs = input_variables, outputs = input_row_from_variables(*input_variables), on_unused_input='warn', allow_input_downcast=True)
    
    def extract_target(x):
        return x[-1]
    
    def threshold_function(x):
        return theano.tensor.tanh(x)
    
    def number_from_output(vector):
            number = threshold_function(vector).sum()
            number.name = "non format output"
            output_number = max_output * number
            output_number.name = "output_number"
            return [output_number]
    
    def latency_encoding(x):
        return [4./(1+tensor.exp(-x/1000))-2]
    
    def latency_decoding(y):
        expr3 = -1000*tensor.log(4./(y+2)-1)
        expr4 = tensor.switch(tensor.ge(y,2),3000.,expr3)
        expr5 = tensor.switch(tensor.ge(0,y),100*tensor.exp(y),expr4)
        return expr5
    
    costs = []
    hypers = []
    
    def hyper_cost(hyper):
        def simple_cost(predictions,targets,current_row):
                predicted_latency = predictions[0]
                target_latency = targets[0]
                return hyper * (predicted_latency-target_latency)**2
        return simple_cost
    
    for i in xrange(len(sizes)-1):
        hyper_param = theano.shared(1., "layer %d coef" %(i),)
        hypers.append(hyper_param)
        costs.append(hyper_cost(hyper_param))
    
    model = BatchesMLP(
                training_set = training_set,
                validation_set = validation_set,
                batch_size = batch_size,
                layers_sizes = sizes,
                encoding_function = input_encoding,
                decoding_function = number_from_output,
                extract_target = extract_target,
                hyper_parameters = hypers,
                additional_target_encoding = latency_encoding,
                additional_output_decoding = latency_decoding,
                cost_functions = costs,
                learning_rate = learning_rate,
                from_layers = from_layers,
            )
    
    return model

def early_stoping(model, initial_patience = 10000, max_epoch=100, verbose =True):
    """
    Train a batch model using early stopping.
    """
    patience = initial_patience
    improvement_threshold = 0.995
    patience_increase_ratio = 2
    
    validation_frequency = min(model.n_batches, patience/4)
    
    best_params = None
    best_validation_loss = numpy.inf
        
    done_looping = False
    epoch = 0
    iteration = 0
    
    start_time = time.clock()
    
    while (epoch < max_epoch) and (not done_looping):
        if verbose:
            print "New epoch."
        
        epoch += 1
        for minibatch_index in xrange(model.n_batches):
            iteration += 1
            
            model.learn_batch(minibatch_index)
            
            if iteration % validation_frequency == 0 :
                train_loss = model.train_loss()
                validation_loss = model.validation_loss()
                if verbose:
                    print "train loss : %f\nvalidation loss : %f" % (train_loss, validation_loss)
                
                if validation_loss < best_validation_loss:
                    
                    best_params = model.get_state_copy()
                    best_validation_loss = validation_loss
                    
                    # improve patience if loss improvement is good enough
                    if validation_loss < best_validation_loss * improvement_threshold:
                        patience = max(patience, iteration * patience_increase_ratio)
            
            if patience <= iteration:
                done_looping = True
    
    end_time = time.clock()
    if verbose :
        print "Total time elapsed %f s." %(1000*(end_time - start_time))
    
    model.restore_state(best_params)
    
    return best_validation_loss

def explore_hyper_parameters(hyper_list, creation_function, retry=10, verbose=True):
    """
    Explore a list of hyperparameters and returns the best model and parameters
    """
    best_model = None
    best_param = None
    best_loss = numpy.inf
    
    for params in hyper_list:
        if verbose:
            print "Using new parameters :"
            print params
        
        for _ in xrange(retry):
            model = creation_function(params)
            loss = early_stoping(model)
            
            if loss < best_loss:
                best_model = model
                best_param = params
    
    return (best_param,best_model,best_loss)



""" Create a 2 hidden layers mlp model based on ip, location, type and distance

model = create_mlp_model()

def extract_learning_data(entry):
    ori_geo = entry[ORIGIN_GEOIP]
    dest_geo = entry[DESTINATION_GEOIP]
    return (get_int_from_ip(entry[ORIGIN_IP]), get_int_from_ip(entry[DESTINATION_IP]),
        ori_geo['latitude'], ori_geo['longitude'],
        dest_geo['latitude'], dest_geo['longitude'],
        entry[ORIGIN_TYPE], entry[DESTINATION_TYPE],
        entry[DISTANCE],
        entry[PEER_LATENCY])

pass_merged()

create_learning_sets(extract_learning_data)

nice_learn(test_data.training_set,test_data.validation_set)

"""

""" Create a single layer MLP (without bias) based on ip, location and distance

model = create_ip_location_model(75,2.,0.01)

pass_merged()

def extract_learning_data(entry):
    ori_geo = entry[ORIGIN_GEOIP]
    dest_geo = entry[DESTINATION_GEOIP]
    return (get_int_from_ip(entry[ORIGIN_IP]), get_int_from_ip(entry[DESTINATION_IP]),
        ori_geo['latitude'], ori_geo['longitude'],
        dest_geo['latitude'], dest_geo['longitude'],
        entry[DISTANCE],entry[PEER_LATENCY])

create_training_sets(extract_training)

for entry in learning_set:
    model.learn(*entry)

"""

""" Create an auto encoder for ips
def input_row_from_ip(ip):
        '''Create an input row from an ip'''
        
        input_row = tensor.zeros([4*256])
        input_row.name = 'input row'
        
        offset = 0
        
        for _ in range(4):
            input_row = add_thermo(input_row, offset, tensor.mod(ip,256))
            ip = tensor.int_div(ip,256)
            offset += 256
        
        return input_row

ip_inp = tensor.scalar('ip','uint32')

model = create_autoencoder(4*256,50,ip_inp,input_row_from_ip)


"""


""" Compare theano optimized and half-python half-theano model in term of speed

model = create_two_ip_from_general(50,10.,0.0005)
model2 = create_two_ip_singlemultip(50,10.,0.0005)
t1 = time.time()
nice_learn_on_set(data_sets[0], model)
t2 = time.time()
learn_on_set(data_sets[0], model2)
t3 = time.time()
(t3-t2) / (t2-t1)



model = create_two_ip_from_general(50,10.,0.0005)

def get_random_ip():
    return random.randint(0,2**32-1)

def learn_constant(model,constant=100, number = 100000):
    for i in range(number):
        ip1 = get_random_ip()
        ip2 = get_random_ip()
        model.learn(ip1,ip2,constant)


learn_constant(model)

"""

"""
Trying to play with autoencoder for ip


def create_ip_auto(middle_size=300, max_output=2., learning_rate=None, from_layers=None):
    
    in_ip = tensor.scalar('input ip', dtype='uint32')
    input_variables = [in_ip]
    
    out_ip = tensor.scalar('output ip', dtype='uint32')
    target_variables = [out_ip]
    
    input_size = 4*256
    
    sizes = [input_size,middle_size,input_size]
    
def input_row_from_variables(input_ip):        
    input_row = tensor.zeros([input_size])
    offset = 0
    for _ in range(4):
        input_row = add_thermo(input_row, offset, tensor.mod(input_ip,256))
        input_ip = tensor.int_div(input_ip,256)
        offset += 256
    return input_row
    
    def number_from_output(vector):
        return [vector.sum()]
    
    def decoding(y):
        return y
    
    def output_cost(predictions,target,current_row):
        diff = current_row - target
        return (diff ** 2).sum()
    
    middle_hyper = theano.shared(0.5, "middle hyper")
    def middle_cost(predictions,target,current_row):
        return middle_hyper*tensor.sqrt(current_row).sum()
    
    costs = [middle_cost,output_cost]
    hypers = [middle_hyper]
    
    model = GeneralMLP(
                sizes = sizes,
                input_variables = input_variables,
                target_variables = target_variables,
                encoding_function = input_row_from_variables,
                decoding_function = number_from_output,
                hyper_parameters = hypers,
                additional_target_encoding = input_row_from_variables,
                additional_output_decoding = decoding,
                cost_functions = costs,
                learning_rate = learning_rate,
                from_layers = from_layers,
            )
    
    return model
"""
