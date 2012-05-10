"""Fabric file for deployment of Pings server to Ubuntu instances on Amazon
Web Services. Tested with Fabric 1.4.1."""

import os.path
from StringIO import StringIO
from fabric.api import *
from fabric.contrib.files import exists

# Loads env.key_filename and env.roledefs settings.
from fabconfig import *

from setup import version as pings_version
here = os.path.dirname(__file__)

# TODO Create (in prepare_host) and use a different user for all the pings
# servers, as the 'ubuntu' user can sudo to root without a password.
PINGS_USER = 'ubuntu'

@task
def prepare_source():
    """Runs tests and packages the source."""
    local('py.test')
    local('python setup.py sdist')

@task
def upload_source():
    """Uploads and unpacks the Pings server source code. Returns the remote
    path where the source was unpacked."""
    put(os.path.join(here, 'dist', 'pings-%s.tar.gz' % pings_version),
        '/tmp/pings-server.tar.gz')
    with cd('/tmp'):
        run('tar xavf /tmp/pings-server.tar.gz')
    return '/tmp/pings-%s' % pings_version

@runs_once
def update_system_packages_repos():
    sudo('apt-get update')

def install_system_packages(package_list):
    """Helper function. Install system package (i.e. .debs)."""
    update_system_packages_repos()
    sudo('apt-get install --assume-yes ' + ' '.join(package_list))

@task
def install_system_base_packages():
    """Installs system packages (i.e. .debs) used by all roles."""
    system_packages = ['libevent-dev', 'libzmq-dev',
                       'wget', 'most', 'htop', 'mmv',
                       'python-dev', 'python-distribute', 'cython']
    install_system_packages(system_packages)

@task
def bootstrap_python_install():
    """Install system-wide Python packages that will allow the rest of the
    required Python packages to be installed via pip in a virtualenv."""
    run('wget http://pypi.python.org/packages/source/p/pip/pip-1.1.tar.gz -O pip-1.1.tar.gz')
    run('tar xavf pip-1.1.tar.gz')
    with cd('pip-1.1'):
        sudo('python setup.py install')
    sudo('pip install virtualenv')

@task
def prepare_memcache_role():
    """Setup a computer for the memcache server role."""
    # A reinstall should not restart the Memcache server needlessly, otherwise
    # we will lose the tokens in production every time this is run.
    install_system_packages(['memcached'])

@task
def prepare_leaderboard_role():
    """Setup a computer for the leaderboard server role."""
    # A reinstall must not restart the Redis server needlessly, otherwise
    # we will lose the leaderboards in production every time this is run.
    install_system_packages(['redis-server'])

@task
def prepare_host():
    """Install all system base packages, basic Python environment, etc."""
    install_system_base_packages()
    bootstrap_python_install()

@task
def setup_virtualenv(rootdir):
    """Creates the virtualenv if needed."""
    # Running virtualenv when said virtualenv already exists and a process
    # is using it fails with a "text file busy" error on the python
    # executable. So check beforehand.
    if not exists(os.path.join(rootdir), 'lib'):
        sudo('virtualenv --distribute ' + rootdir)

@task
@roles('test')
def prepare_test_host():
    """Installs all required packages for all roles, to prepare a host for
    being used as a test host."""
    prepare_host()
    prepare_memcache_role()
    prepare_leaderboard_role()

def generate_upstart_conf(rootdir, program, args, description):
    """Create a .conf file for an Upstart service. Returns it
    as a StringIO object."""
    name = os.path.basename(program)
    return StringIO('''description "%(description)s"

start on runlevel [2345]
stop on runlevel [!2345]
respawn

# Supported starting with Ubuntu 12.04
#console log
#setuid ubuntu

# We need to specify an absolute path for the program even though
# we use --chdir to work around the following bug:
# http://bugs.debian.org/cgi-bin/bugreport.cgi?bug=669047
exec start-stop-daemon --start --chdir %(rootdir)s --chuid ubuntu --name %(name)s --startas %(rootdir)s/%(program)s -- %(args)s
''' % locals())

def start_upstart_service(name, rootdir, program, args, description):
    """Create an Upstart service .conf file, uploads it and starts the service."""
    put(generate_upstart_conf(rootdir, program, args, description),
        '/etc/init/%s.conf' % name, use_sudo=True)
    # Need to do a stop + start for Upstart to use the new config.
    with settings(warn_only=True):
        sudo('stop %s' % name)
    sudo('start %s' % name)

@task
def start_http_server(rootdir):
    """Starts the Pings http server."""
    start_upstart_service('pings-http-server', rootdir, program='bin/paster',
                          args='serve development.ini',
                          description='Pings http server')

@task
def start_storage_server(rootdir):
    """Starts the Pings storage server."""
    data_dir = '%s/data' % rootdir
    sudo('mkdir -p ' + data_dir)
    sudo('chown %s. %s' % (PINGS_USER, data_dir))
    start_upstart_service('pings-storage-server', rootdir,
                          program='bin/storage_server',
                          args='development.ini %s' % data_dir,
                          description='Pings storage server')

@task
def start_leaderboards_server(rootdir, wipe_data=False):
    """Starts the Pings leaderboards server."""
    if not wipe_data:
        # TODO Save data before stopping the service.
        raise NotImplementedError
    start_upstart_service('pings-leaderboards-server', rootdir,
                          program='bin/leaderboards_server', args='development.ini',
                          description='Pings leaderboards server')

@task
@roles('test')
def deploy_test():
    """Installs everything on a single test server, able to run
    the development.ini file configuration, then starts all
    the Pings servers.

    Not to be used for deployment!"""
    prepare_source()
    pings_src_dir = upload_source()

    # Install everything
    rootdir = '/srv/pings_test'
    setup_virtualenv(rootdir)
    with cd(rootdir):
        put('development.ini', '.', use_sudo=True)

        # TODO Remove hardcoded python version in path.
        with cd('/srv/pings_test/local/lib/python2.7/site-packages'):
            geoip_filename = 'GeoLiteCity.dat'
            if not exists(geoip_filename):
                put(geoip_filename, '.', use_sudo=True)

        # Install all requirements.
        sudo('bin/pip install -r %s' % os.path.join(pings_src_dir, 'requirements.txt'))

        # Ignore dependencies. They are all in the requirements.pip file
        # anyways, and we don't want the --force-reinstall option to
        # reinstall all dependencies. Said --force-reinstall option is
        # there so the version we have now is installed even if the version
        # number wasn't bumped up (kinda handy during development).
        sudo('bin/pip install --no-deps --force-reinstall %s' % pings_src_dir)

    # Install and start all the services
    start_leaderboards_server(rootdir, wipe_data=True)
    start_storage_server(rootdir)
    start_http_server(rootdir)
