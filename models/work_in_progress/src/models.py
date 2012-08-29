'''
Created on Jun 27, 2012

@author: RaphaelBonaque <bonaque@crans.org>

The four currently in used models :
_ GeneralMLP is a general class to do pure online (no batch) learning : it is 
  quite customizable
_ BatchesMLP is almost the same model but it work with batches (which force to
  only use the encoded version of inputs and is thus a bit less customizable)
_ ProjectionMLP is a non-customizable MLP that projects (ip, localization, type) 
  into a pseudo metric space -metric space coordinate plus an altitude- and then 
  compute the latency using a pseudo-distance -sum of altitude plus usual 
  distance on the globe plus distance in the projection's metric space-
_ LocalizationModel is a model remotely inspired on Vivaldi [Frank Dabek, 
  Russ Cox, Frans Kaashoek, Robert Morris (2004). "Vivaldi: A Decentralized 
  Network Coordinate System". Proc. of the annual conference of the Special 
  Interest Group on Data Communication (SIGCOMM'04)]. It is basically a simpler
  version of ProjectionMLP that doesn't use a neural network and only actualize
  deterministically the altitude of the peer (these altitudes are stored for 
  each ip) and no extra coordinates are used.

All the models have a learn (or learn_batch) and a predict function.

You can find example of use of theses models in theano_play
'''
import math

import theano
from theano import tensor
import numpy
from numpy import random
from utils import get_geoip_data, geoip_distance
from test_data import ORIGIN_GEOIP, ORIGIN_IP, DESTINATION_GEOIP, DESTINATION_IP,\
    PEER_LATENCY, DISTANCE, ORIGIN_TYPE, DESTINATION_TYPE

class GeneralMLP:
    """
    A general multilayer perceptron.
    
    It is bug free AFAIK  but I've found the default learning rate -
    1/sqr(input_size)- often too high, you should explore the hyper parameters 
    correctly anyway.
    """
    def __init__(self,
                sizes, input_variables, target_variables,
                encoding_function, decoding_function,
                hyper_parameters=[],
                additional_target_encoding=None, additional_output_decoding=None,
                cost_functions=None, learning_rate=None, from_layers=None):
        
        float_type = theano.config.floatX
        
        if len(sizes) < 2:
            raise Exception("You need at least an input and an output")
        
        if from_layers is None:
            weights_layers = []
            bias = []
            for i in xrange(len(sizes)-1):
                input_size = sizes[i] 
                output_size = sizes[i+1]
                weights_layers.append(
                    numpy.array(
                        random.uniform(
                            low=-numpy.sqrt(6./(input_size+output_size)),
                            high=numpy.sqrt(6./(input_size+output_size)),
                            size=(input_size,output_size),
                            ),
                        dtype=float_type)
                    )
                bias.append(
                        numpy.zeros(shape=[output_size], dtype=float_type)
                    )
        else:
            weights_layers, bias = from_layers
        
        self.weights_layers = [theano.shared(layer, "weight layer %d" % i) for layer in weights_layers]
        self.bias = [theano.shared(layer, "bias %d" % i) for layer in bias]
        
        if learning_rate is None:
            learning_rate = 1./sizes[0]
        learning_rate = theano.shared(learning_rate,"learning rate")
        tensor.cast(learning_rate, float_type)
        
        bias_coeff = theano.shared(0.0,"bias coefficient")
        tensor.cast(bias_coeff, float_type)
        
        self.hyper_parameters = hyper_parameters[:]
        self.hyper_parameters.append(learning_rate)
        self.hyper_parameters.append(bias_coeff)
        
        if cost_functions is None:
            def usual_cost(output_variables,target_variables, current_layer_output):
                return (output_variables[0] - target_variables[0])**2
            cost_functions = [usual_cost for _ in xrange(len(sizes)-1)]
        
        def activation_function(x):
            return tensor.tanh(x)
        
        def process(input_variables):
            input_row = encoding_function(*input_variables)
            input_row.name = "input row"
            
            current_layer_row = input_row
            rows = []
            
            for i,weights_layer in enumerate(self.weights_layers):
                current_ouput = tensor.dot(current_layer_row,weights_layer) + self.bias[i]
                current_layer_row = activation_function(current_ouput)
                current_layer_row.name = "row %d" % (i + 1)
                rows.append(current_layer_row)
            
            output_row = current_layer_row
            output_row.name = "output row"
            
            output_variables = decoding_function(output_row)
            
            self.debug_var = output_row
            
            return (output_variables, rows)
        
        
        if additional_target_encoding is None:
            real_targets = target_variables
        else:
            real_targets = additional_target_encoding(*target_variables)    
        
        prediction,rows = process(input_variables)
        
        costs = [cost_functions[i](prediction,real_targets,current_row) for i,current_row in enumerate(rows)]
        
        updates = dict([
            (weight_layer, weight_layer - learning_rate*tensor.grad(costs[i], weight_layer))
            for i,weight_layer in enumerate(self.weights_layers)
        ])
        updates.update(dict([
            (a_bias, a_bias - learning_rate*bias_coeff*tensor.grad(costs[i], a_bias))
            for i,a_bias in enumerate(self.bias)
        ]))
        
        input_parameters = []
        input_parameters.extend(input_variables)
        input_parameters.extend(target_variables)
        
        if additional_output_decoding is None:
            decoded_output = prediction
        else:
            decoded_output = additional_output_decoding(*prediction)
        
        self.predict = theano.function(inputs=input_variables, outputs=decoded_output, allow_input_downcast=True)
        
        self.cost = theano.function(inputs=input_parameters, outputs=costs, allow_input_downcast=True)
        
        self.learn = theano.function(inputs=input_parameters, outputs=costs, updates=updates, allow_input_downcast=True)
        
        self.grad = theano.function(inputs=input_parameters, outputs=[tensor.grad(costs[i],weight_layer) for i,weight_layer in enumerate(self.weights_layers)], allow_input_downcast=True)
        
        self.bias_grad = theano.function(inputs=input_parameters, outputs=[tensor.grad(costs[i], a_bias) for i,a_bias in enumerate(self.bias)], allow_input_downcast=True)


class BatchesMLP:
    '''
    A general multilayer perceptron that works with minibatches.
    
    Works but I didn't test it extensively as I mostly worked online and 
    then switched to Projection MLP.
    
    Warning : can be very long to compile on large data_set (there should be 
    something to do about that).
    '''
    def __init__(self,
                training_set, validation_set,
                batch_size, layers_sizes,
                encoding_function, decoding_function, extract_target,
                hyper_parameters = [],
                additional_target_encoding = None, additional_output_decoding = None,
                cost_functions = None, learning_rate = None, from_layers = None):
        
        float_type = theano.config.floatX
        
        self.training_set_inputs = theano.shared([encoding_function(*elem) for elem in training_set])
        self.training_set_target = theano.shared([extract_target(*elem) for elem in training_set])
        
        self.validation_set_inputs = theano.shared([encoding_function(*elem) for elem in validation_set])
        self.validation_set_target = theano.shared([extract_target(*elem) for elem in validation_set])
        
        index = tensor.iscalar("index")
        
        #Batch_size and n_batches should be read only
        self.batch_size = batch_size
        self.n_batches = len(training_set.get_value())/batch_size
        
        if len(layers_sizes) < 2:
            raise Exception("You need at least an input and an output")
        
        if from_layers is None:
            weights_layers = []
            bias = []
            for i in xrange(len(layers_sizes)-1):
                input_size = layers_sizes[i]
                output_size = layers_sizes[i+1]
                weights_layers.append(
                    numpy.array(
                        random.uniform(
                            low=-numpy.sqrt(6./(input_size+output_size)),
                            high=numpy.sqrt(6./(input_size+output_size)),
                            size=(input_size,output_size),
                            ),
                        dtype=float_type)
                    )
                bias.append(
                        numpy.zeros(shape=[output_size], dtype=float_type)
                    )
        else:
            weights_layers, bias = from_layers
        
        self.weights_layers = [theano.shared(layer, "weight layer %d" % i) for layer in weights_layers]
        self.bias = [theano.shared(layer, "bias %d" % i) for layer in bias]
        
        if learning_rate is None:
            learning_rate = 1./layers_sizes[0]
        learning_rate = theano.shared(learning_rate, "learning rate")
        tensor.cast(learning_rate,float_type)
        
        bias_coeff = theano.shared(0.01, "bias coefficient")
        tensor.cast(bias_coeff, float_type)
        
        self.hyper_parameters = hyper_parameters[:]
        self.hyper_parameters.append(learning_rate)
        self.hyper_parameters.append(bias_coeff)
        
        if cost_functions is None:
            def usual_cost(output_variables,target_variables,current_layer_output):
                return (output_variables[0]-target_variables[0])**2
            cost_functions = [usual_cost for _ in xrange(len(layers_sizes)-1)]
        
        def activation_function(x):
            return tensor.tanh(x)
        
        def process(input_rows):
            current_layer_row = input_rows
            rows = []
            
            for i,weights_layer in enumerate(self.weights_layers):
                current_ouput = tensor.dot(current_layer_row,weights_layer) + self.bias[i]
                current_layer_row = activation_function(current_ouput)
                current_layer_row.name = "row %d" %(i+1)
                rows.append(current_layer_row)
            
            output_row = current_layer_row
            output_row.name = "output row"
            
            output_variables = decoding_function(output_row)
            
            self.debug_var = output_row
            
            return (output_variables,rows)
        
        if additional_target_encoding is None:
            real_targets = self.training_set_target[index:index+self.batch_size]
        else:
            real_targets = additional_target_encoding(self.training_set_target[index:index+self.batch_size])
        
        prediction,rows = process(self.training_set_inputs[index:index+self.batch_size])
        
        costs = [tensor.mean(cost_functions[i](prediction, real_targets, current_row)) for i,current_row in enumerate(rows)]
        
        updates = {}
        
        for i,weight_layer in enumerate(self.weights_layers) :
            updates[weight_layer] = weight_layer - learning_rate*tensor.grad(costs[i], weight_layer)
        
        for i,a_bias in enumerate(self.bias):
            updates[a_bias] = a_bias - learning_rate*bias_coeff*tensor.grad(costs[i], a_bias)
                
        if additional_output_decoding is None:
            decoded_output = prediction
        else:
            decoded_output = additional_output_decoding(*prediction)
        
        self.predict = theano.function(inputs=[index], outputs=decoded_output, allow_input_downcast=True)
        
        self.cost = theano.function(inputs=[index], outputs=costs, allow_input_downcast=True)
        
        self.learn_batch = theano.function(inputs=[index], outputs=costs, updates=updates, allow_input_downcast=True)
        
        self.grad = theano.function(inputs=[index], outputs=[tensor.grad(costs[i], weight_layer) for i, weight_layer in enumerate(self.weights_layers)], allow_input_downcast=True)
        
        self.bias_grad = theano.function(inputs=[index], outputs=[tensor.grad(costs[i], a_bias) for i, a_bias in enumerate(self.bias)], allow_input_downcast=True)
        
        train_prediction,train_rows = process(self.training_set_inputs)
        validation_prediction,validation_rows = process(self.validation_set_inputs)
        
        if additional_target_encoding is None:
            train_targets = self.training_set_target
            validation_targets = self.validation_set_target
        else:
            train_targets = additional_target_encoding(self.training_set_target)
            validation_targets = additional_target_encoding(self.validation_set_target)
        
        test_loss = tensor.mean(cost_functions[-1](train_prediction, train_targets, train_rows[-1]))
        validation_loss = tensor.mean(cost_functions[-1](validation_prediction, validation_targets, validation_rows[-1]))
        
        self.test_loss = theano.function(inputs=[], outputs=test_loss)
        
        self.validation_loss = theano.function(inputs=[], outputs=validation_loss)
        
    def get_state_copy(self):
        weights_copy = [layer.get_value() for layer in self.weights_layers]
        bias_copy = [layer.get_value() for layer in self.bias]
        hypers_copy = [parameter.get_value() for parameter in self.hyper_parameters]
        return (weights_copy,bias_copy,hypers_copy)
    
    def restore_state(self, other_state):
        new_weights, new_bias, new_hypers = other_state
        for i,layer in enumerate(new_weights):
            self.weights_layers[i].set_value(layer)
        for i,layer in enumerate(new_bias):
            self.bias[i].set_value(layer)
        for i,layer in enumerate(new_hypers):
            self.hyper_parameters[i].set_value(layer)


class ProjectionMLP:
    """ An MLP that works online (no minibatch) and projects (ip, localization, 
    type) into (h,(z1,z2, ... zn)). We want to minimize :
    \alpha*d(loc1,loc2) + d(zi1,zi2) + h1 + h2 - latency
    
    n is specified by n_extra_coord
    alpha by the hyperparameter alpha (stored in self.hyper_parameters)
    
    This model is loosely based on the GeneralMLP.
    """
        
    def __init__(self, middle_sizes=[1000,1000], n_extra_coord=3, learning_rate=10e-5, training_set=None, validation_set=None, batch_size=100, from_layers=None):
        
        float_type = theano.config.floatX
        
        #We define the variables we'll need to create the theano functions    
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
        
        one_ip_variables = [origin_ip, origin_latitude, origin_longitude, destination_type]
        
        input_variables = [origin_ip, destination_ip, origin_latitude, 
            origin_longitude, destination_latitude, destination_longitude, 
            origin_type, destination_type, distance]
        
        learn_variables = input_variables[:]
        learn_variables.append(latency)
        
        #Some sized used to determined input and output size
        
        ip_size = 4 * 256
        #The number of bits to encode latitude and longitude size on
        coordinate_size = 45
        type_size = 6
        
        input_size = ip_size + 2 * coordinate_size + type_size
        
        altitude_output_size = 100
        
        coord_output_size = 100
        
        output_size = altitude_output_size + (n_extra_coord * coord_output_size)
        
        #sizes is the list of sizes of every layer (including input and output)
        sizes = [input_size]
        sizes.extend(middle_sizes)
        sizes.append(output_size)
        
        self.hyper_parameters = []
        
        if from_layers is None:
            weights_layers = []
            bias = []
            for i in xrange(len(sizes)-1):
                input_size_ = sizes[i] 
                output_size_ = sizes[i+1]
                weights_layers.append(
                    numpy.array(
                        random.uniform(
                            low=-numpy.sqrt(6./(input_size_+output_size_)),
                            high=numpy.sqrt(6./(input_size_+output_size_)),
                            size=(input_size_, output_size_),
                            ),
                        dtype=float_type)
                    )
                bias.append(
                        numpy.zeros(shape=[output_size_], dtype=float_type)
                    )
        else:
            weights_layers, bias = from_layers
        
        self.weights_layers = [theano.shared(layer,"weight layer %d" % i) for layer in weights_layers]
        self.bias = [theano.shared(layer,"bias %d" % i) for layer in bias]
        
        if learning_rate is None:
            learning_rate = 1./sizes[0]
        learning_rate = theano.shared(learning_rate, "learning rate")
        tensor.cast(learning_rate, float_type)
        self.hyper_parameters.append(learning_rate)
        
        bias_coeff = theano.shared(0.01, "bias coefficient")
        tensor.cast(bias_coeff, float_type)
        self.hyper_parameters.append(bias_coeff)
        
        
        def add_thermo(array, offset, value):
            return tensor.set_subtensor(array[offset:offset+value], 1)
        
        def add_one_shot(array, offset, value):
            return tensor.set_subtensor(array[offset+value:offset+value+1], 1)
        
        def input_row_from_variables(ip_, lat_, long_, type_):
            '''Create an input row for the MLP from the inputs'''
            
            input_row = tensor.zeros([input_size])
            offset = 0
            
            for _ in range(4):
                input_row = add_one_shot(input_row, offset, tensor.mod(ip_, 256))
                ip_ = tensor.int_div(ip_, 256)
                offset += 256
            
            translated_lat = tensor.iround((coordinate_size-1) * (lat_/180 + 0.5))
            input_row = add_thermo(input_row, offset, translated_lat)
            offset += coordinate_size
            
            translated_long = tensor.iround((coordinate_size-1) * (long_/360 + 0.5))
            input_row = add_thermo(input_row, offset, translated_long)
            offset += coordinate_size
            
            input_row = add_one_shot(input_row, offset, type_ +1)
            offset += type_size
            
            return input_row
        
        def threshold_function(x):
            return theano.tensor.tanh(x)
        
        def activation_function(x):
            return tensor.tanh(x)
        
        def process(input_row): 
            current_layer_row = input_row
            rows = []
            
            for i,weights_layer in enumerate(self.weights_layers):
                current_ouput = tensor.dot(current_layer_row, weights_layer) + self.bias[i]
                current_layer_row = activation_function(current_ouput)
                current_layer_row.name = "row %d" %(i+1)
                rows.append(current_layer_row)
            
            output_row = current_layer_row
            output_row.name = "output row"
            
            return output_row
        
        def coord_from_output(vector):
            coord = tensor.alloc(tensor.cast(0,dtype=float_type), 1 + n_extra_coord)
            
            altitude_vector = vector[0:altitude_output_size]
            altitude = tensor.sum(tensor.nnet.softplus(altitude_vector))
            coord = tensor.set_subtensor(coord[0], altitude)
            
            offset = altitude_output_size
            for i in xrange(n_extra_coord):
                coord_vector = vector[offset:offset+coord_output_size]
                extra_coord = tensor.sum(coord_vector)
                coord = tensor.set_subtensor(coord[1+i], extra_coord)
                offset += coord_output_size
            
            return coord
        
        def projection(ip_, lat_, long_, type_):
            in_row = input_row_from_variables(ip_, lat_, long_, type_)
            out_row = process(in_row)
            return coord_from_output(out_row)
        
        alpha = theano.shared(1.65e-5,"alpha")
        self.hyper_parameters.append(alpha)
        
        def pseudo_distance(usual_distance, proj1, proj2):
            distance = alpha * usual_distance
            
            altitude1 = proj1[0]
            altitude2 = proj2[0]
            distance += altitude1 + altitude2
            
            coord1 = proj1[1:]
            coord2 = proj2[1:]
            distance += tensor.sum((coord1 - coord2)**2)
            
            return distance
        
        def prediction(ori_ip, dest_ip, ori_lat, ori_long, dest_lat, dest_long, 
                    ori_type, dest_type, dist):
            proj1 = projection(ori_ip, ori_lat, ori_long, ori_type)
            proj2 = projection(dest_ip, dest_lat, dest_long, dest_type)
            
            return pseudo_distance(dist, proj1, proj2)
        
        costs = []
        
        def hyper_cost(hyper):
            def simple_cost(prediction,target):
                    return hyper * (prediction - target)**2
            return simple_cost
        
        for i in xrange(len(sizes)-1):
            hyper_param = theano.shared(1., "layer %d coef" %(i),)
            self.hyper_parameters.append(hyper_param)
            costs.append(hyper_cost(hyper_param))
        
        #For pure online (no batch)
        online_prediction = prediction(*input_variables)
        
        online_costs = [cost(online_prediction, latency) for cost in costs]
        
        online_updates = {}
        for i,weight_layer in enumerate(self.weights_layers):
            online_updates[weight_layer] =  weight_layer - learning_rate * tensor.grad(online_costs[i], weight_layer)
        for i,a_bias in enumerate(self.bias):
            online_updates[a_bias] = a_bias - learning_rate*bias_coeff * tensor.grad(online_costs[i], a_bias)
        
        self.project = theano.function(inputs=one_ip_variables, outputs=projection(*one_ip_variables), allow_input_downcast=True)
        
        self.predict = theano.function(inputs=input_variables, outputs=online_prediction, allow_input_downcast=True)
        
        self.learn = theano.function(inputs=learn_variables, outputs=online_costs, updates=online_updates, allow_input_downcast=True)
        
        self.cost = theano.function(inputs=learn_variables, outputs=online_costs, allow_input_downcast=True)
        
        self.grad = theano.function(inputs=learn_variables, outputs=[tensor.grad(online_costs[i],weight_layer) for i,weight_layer in enumerate(self.weights_layers)], allow_input_downcast=True)
        
        self.bias_grad = theano.function(inputs=learn_variables, outputs=[tensor.grad(online_costs[i],a_bias) for i,a_bias in enumerate(self.bias)], allow_input_downcast=True)
        
        #For minibatches
        
        if training_set is None or validation_set is None:
            return 
        
        index = tensor.iscalar("index")
        
        input_encoding = theano.function(inputs = one_ip_variables, outputs = input_row_from_variables(*one_ip_variables), allow_input_downcast=True)
        
        train_set_ori = theano.shared(
            numpy.array(
                [input_encoding(entry[ORIGIN_IP],
                    entry[ORIGIN_GEOIP]['latitude'],
                    entry[ORIGIN_GEOIP]['longitude'],
                    entry[ORIGIN_TYPE])
                for entry in training_set]
                )
            )
        train_set_dest = theano.shared(
            numpy.array(
                [input_encoding(entry[DESTINATION_IP],
                    entry[DESTINATION_GEOIP]['latitude'],
                    entry[DESTINATION_GEOIP]['longitude'],
                    entry[DESTINATION_TYPE])
                for entry in training_set]
                )
            )
        train_set_dist = theano.shared(
            numpy.array([entry[DISTANCE] for entry in training_set])
            )
        
        train_set_lat = theano.shared(
            numpy.array([entry[PEER_LATENCY] for entry in training_set])
            )
        
#TODO : finish coding the batch version (the online version work so you can 
#always use it to emulate batch : "
# def learn_batch(model,n):
#    for n in xrange(n*batch_size,(n+1)*batch_size):
#        model.learn(*training_set[n])
#"
#
#        batch_projection_ori = 
#        batch_projection_dest =
#        
#        batch_prediction = pseudo_distance(train_set_dist[index:index+batch_size],batch_projection_ori,batch_projection_dest)
#        
#        batch_costs = [tensor.mean(cost(batch_prediction, train_set_lat[index:index+batch_size])) for cost in costs]
#        
#        batch_updates = {}
#        for i,weight_layer in enumerate(self.weights_layers):
#            batch_updates[weight_layer] =  weight_layer - learning_rate * tensor.grad(online_costs[i], weight_layer)
#        for i,a_bias in enumerate(self.bias):
#            batch_updates[a_bias] = a_bias - learning_rate*bias_coeff * tensor.grad(online_costs[i], a_bias)
#        
#        self.learn_batch = theano.function(inputs=[index], outputs =[batch_cost], updates = batch_updates)
#        
#        test_loss =
#        self.test_loss = theano.function(inputs=[], outputs=test_loss)
#        
#        validation_loss =
#        self.validation_loss = theano.function(inputs=[], outputs=validation_loss)
#        
        
    def get_state_copy(self):
        weights_copy = [layer.get_value() for layer in self.weights_layers]
        bias_copy = [layer.get_value() for layer in self.bias]
        hypers_copy = [parameter.get_value() for parameter in self.hyper_parameters]
        return (weights_copy,bias_copy,hypers_copy)
    
    def restore_state(self, other_state):
        new_weights, new_bias, new_hypers = other_state
        for i,layer in enumerate(new_weights):
            self.weights_layers[i].set_value(layer)
        for i,layer in enumerate(new_bias):
            self.bias[i].set_value(layer)
        for i,layer in enumerate(new_hypers):
            self.hyper_parameters[i].set_value(layer)


default_altitude = 120.

class Position:
    def __init__(self, ip, geoip=None, altitude=default_altitude):
        self.ip = ip
        if geoip is None:
            geoip = get_geoip_data(ip)
        if geoip is None:
            print "None geoip " + ip
            self.latitude = 0
            self.longitude = 0
            self.altitude = 0
            self.record = [altitude]
            self.distance_to = lambda x: 0            
            return
        self.geoip = geoip
        self.latitude = geoip['latitude']
        self.longitude = geoip['longitude']
        self.altitude = altitude
        self.record = [altitude]
    
    def predict(self,other_position):
        total_altitude = self.altitude + other_position.altitude
        estimated_latency = self.distance_to(other_position) * 1.65e-5 + total_altitude
        return estimated_latency
        
    def distance_to(self,other_position):
        return geoip_distance(self.geoip, other_position.geoip)
    
    def add(self,to_add):
        self.record.append(self.altitude + to_add)
        self.altitude = float(self.altitude*(len(self.record)-1) + to_add)/len(self.record)


class LocalizationModel:
    """
    This is a small pseudo Vivaldi-like model
    
    """
    def __init__(self):
        self.positions = {}
    
    def predict(self,entry):
        ip1 = entry[ORIGIN_IP]
        ip2 = entry[DESTINATION_IP]
        try:
            position1 = self.positions[ip1]
        except:
            position1 = Position(ip1, entry[ORIGIN_GEOIP])
            self.positions[ip1] = position1
        try:
            position2 = self.positions[ip2]
        except:
            position2 = Position(ip2, entry[DESTINATION_GEOIP])
            self.positions[ip2] = position2
            
        predicted_latency = position1.predict(position2)
        
        return predicted_latency
    
    def get_pos(self, ip, geoip=None):
        try:
            pos = self.positions[ip]
        except:
            pos = Position(ip, geoip)
            self.positions[ip] = pos
        return pos
    
    def add(self,entry):
        position1 = self.get_pos(entry[ORIGIN_IP], entry[ORIGIN_GEOIP])
        position2 = self.get_pos(entry[DESTINATION_IP], entry[DESTINATION_GEOIP])
        predicted_latency = position1.predict(position2)
        to_add = entry[PEER_LATENCY] - predicted_latency
        coef1 = (1. + len(position1.record)) /(len(position1.record) + len(position2.record) + 2)
        coef2 = 1. - coef1
        position1.add(coef1 * to_add /2)
        position2.add(coef2 * to_add /2)
    
    def average_distance(self, set_):
        total = 0
        for entry in set_:
            position1 = self.get_pos(entry[ORIGIN_IP], entry[ORIGIN_GEOIP])
            position2 = self.get_pos(entry[DESTINATION_IP], entry[DESTINATION_GEOIP])
            total += abs(position1.predict(position2) - entry[PEER_LATENCY])
        return total/len(set_)


#Calculate the average altitude over a set for the localization model
def average_altitude(set):
    sum_ = 0
    for entry in set:
        sum_ += (entry[PEER_LATENCY] - entry[DISTANCE]*1.65e-5)
    
    return sum_ /(2*len(set))
