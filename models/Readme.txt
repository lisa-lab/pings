All files located in this folder are tests and models to predict latency build around data collected by the Pings applet or by Ubisoft (which are not present here).

You probably don't want to use this files unless you have theses data and know what you are doing.

You need to add a symlink at work_in_progress/data/GeoLiteCity.dat to server
cd work_in_progress/data/
ln -s ../../../server/GeoLiteCity.dat

#Copy the trained model data/params in work_in_progress/data/sandbox2
mkdir work_in_progress/data/sandbox2
cp SOME_PATH/{train,valid,test,names,params} work_in_progress/data/sandbox2

