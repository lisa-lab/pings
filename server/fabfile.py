"""Fabric file for deployment of Pings server to Ubuntu instances on Amazon
Web Services. Tested with Fabric 1.4.1."""

import os.path
from fabric.api import *

from fabconfig import *
from setup import version as pings_version
here = os.path.dirname(__file__)

# Remote directory where upload_sources unpacks the source code.
pings_src_dir = '/tmp/pings-%s' % pings_version

def pack():
    local('python setup.py sdist')

def prepare_deploy():
    local('py.test')
    pack()

def upload_source():
    put(os.path.join(here, 'dist', 'pings-%s.tar.gz' % pings_version),
        '/tmp/pings-server.tar.gz')
    with cd('/tmp'):
        run('tar xavf /tmp/pings-server.tar.gz')

def install_system_packages():
    system_packages = ['libevent-dev', 'libzmq-dev',
                       'wget', 'most', 'htop', 'mmv',
                       'python-dev', 'python-distribute', 'cython']

    sudo('apt-get update')
    sudo('apt-get install --assume-yes ' + ' '.join(system_packages))

def bootstrap_python_install():
    """Install system-wide Python packages that will allow the rest of the
    required Python packages to be installed via pip in a virtualenv."""
    run('wget http://pypi.python.org/packages/source/p/pip/pip-1.1.tar.gz -O pip-1.1.tar.gz')
    run('tar xavf pip-1.1.tar.gz')
    with cd('pip-1.1'):
        sudo('python setup.py install')
    sudo('pip install virtualenv')

def prepare_host():
    """Install all system packages, basic Python environment, etc."""
    install_system_packages()
    bootstrap_python_install()

@roles('test')
def deploy_test():
    """Installs everything on a single test server."""
    #prepare_deploy()
    upload_source()

    # Install everything
    sudo('virtualenv --distribute /srv/pings_test')
    with cd('/srv/pings_test'):
        sudo('bin/pip install -r %s' % os.path.join(pings_src_dir, 'requirements.pip'))
        sudo('bin/python %s install' % os.path.join(pings_src_dir, 'setup.py'))
