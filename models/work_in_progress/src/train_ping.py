import graph


# default
data = ('iOS', 'iOS-wifi', 'AC', 'IC2012')
models = ('constant', 'distance', 'countries', 'full')
plot_dir = '../../report2/figures'
show_plots = False
oa_max_delay = [5000.]
save = False


## save model
#data = 'iOS',
#models = 'full',
#plot_dir = ''
#save = True

for d in data:
    print '#' * 70
    print '#' * 3, d
    print '#' * 70
    for m in models:
        print
        print '#' * 4, m
        graph.train_model(model=m,
                          data=d,
                          plot_dir=plot_dir,
                          plot_accuracy=True,
                          show_plots=show_plots,
                          oa_max_delay=oa_max_delay,
                          save=save)
