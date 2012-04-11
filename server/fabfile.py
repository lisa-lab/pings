"""Fabric file for deployment of Pings server to Ubuntu instances on Amazon
Web Services. Tested with Fabric 1.4.1."""

import os.path
from fabric.api import *
from fabric.contrib.files import exists

# Loads env.key_filename and env.roledefs settings.
from fabconfig import *

from setup import version as pings_version
here = os.path.dirname(__file__)


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
    install_system_packages(['memcached'])

@task
def prepare_leaderboard_role():
    """Setup a computer for the leaderboard server role."""
    install_system_packages(['redis-server'])

@task
def prepare_host():
    """Install all system base packages, basic Python environment, etc."""
    # TODO Remove ability to sudo without a password on remote host!
    install_system_base_packages()
    bootstrap_python_install()

@task
@roles('test')
def prepare_test_host():
    """Installs all required packages for all roles, to prepare a host for
    being used as a test host."""
    prepare_host()
    prepare_memcache_role()
    prepare_leaderboard_role()

@task
@roles('test')
def deploy_test():
    """Installs everything on a single test server, able to run the development.ini file configuration.
    Not to be used for deployment!"""
    prepare_source()
    pings_src_dir = upload_source()

    # Install everything
    sudo('virtualenv --distribute /srv/pings_test')
    with cd('/srv/pings_test'):
        put('development.ini', '.', use_sudo=True)

        # TODO Remove hardcoded python version in path.
        with cd('/srv/pings_test/local/lib/python2.7/site-packages'):
            geoip_filename = 'GeoLiteCity.dat'
            if not exists(geoip_filename):
                put(geoip_filename, '.', use_sudo=True)

        sudo('bin/pip install -r %s' % os.path.join(pings_src_dir, 'requirements.pip'))
        sudo('bin/pip install %s' % pings_src_dir)
