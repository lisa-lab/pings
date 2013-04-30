#TODO once to make a virtual env in the pings project directory
BASE_DIR=/Tmp/$USER/
BASE_DIR=/opt/ping/
export WORKON_HOME=$BASE_DIR/.virtualenvs
export PROJECT_HOME=$BASE_DIR/pings_proj
mkdir -p $WORKON_HOME $PROJECT_HOME

source virtualenvwrapper.sh;
workon pings
cd server

${BASE_DIR}/nginx/sbin/nginx & sleep 5;
redis-server redis.conf & sleep 5;
./storage_server production.ini & sleep 5;
./leaderboards_server production.ini & sleep 5;
for i in 44 45 46 47 48 49 50 51; do echo $i; paster serve production.ini http_port=65$i &>> serve$i.$HOSTNAME& done
