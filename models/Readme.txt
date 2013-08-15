All files located in this folder are tests and models to predict latency build around data collected by the Pings applet or by Ubisoft (which are not present here).

You probably don't want to use this files unless you have theses data and know what you are doing.

You need to add a symlink at work_in_progress/data/GeoLiteCity.dat to server
cd work_in_progress/data/
ln -s ../../../server/GeoLiteCity.dat

#Copy the trained model data/params in work_in_progress/data/sandbox2
mkdir work_in_progress/data/sandbox2
cp SOME_PATH/{train,valid,test,names,params} work_in_progress/data/sandbox2

To generate the training data, you need to:
- extract the .csv.bz2 and .rar files from /data/lisa_ubi/ping/ios (or the appropriate location) into data/ubi-data2
- run "python test_data.py" from src/ to generate data/sandbox2/set_0.pkl to set_???.pkl
- from src/, run "python -c 'import graph; graph.export_datasets()'"

To generate the model parameters:
python -c 'import graph; graph.train_model(save=True)'
