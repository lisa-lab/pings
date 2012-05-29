"""Fabric file for deployment of Pings server to Ubuntu instances on Amazon
Web Services. Tested with Fabric 1.4.1."""

from __future__ import print_function
import os.path, time
from StringIO import StringIO
from fabric.api import *
from fabric.contrib import files
from boto.ec2.connection import EC2Connection

# Loads env.key_filename and env.roledefs settings.
from fabconfig import *

from setup import version as pings_version
here = os.path.dirname(__file__)

# User under which the Pings servers run.
PINGS_USER = 'pings'

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

def install_system_packages(package_list, dont_install_recommends=False):
    """Helper function. Install system package (i.e. .debs)."""
    update_system_packages_repos()

    # Sometimes the "apt-get install" call below fails to see the updated
    # package list from update_system_packages_repos() above. Retry both a
    # couple times as a hack to fix this. Test again when upgrading from
    # Ubuntu 11.10 and remove this hack if it isn't needed anymore.
    MAX_TRIES = 3
    sudo_command = ('apt-get install --assume-yes ' +
                    ('--no-install-recommends ' if dont_install_recommends else '') +
                    ' '.join(package_list))

    with settings(warn_only=True):
        for i in range(MAX_TRIES-1):
            result = sudo(sudo_command)
            if result.succeeded:
                return

            # Force repeat of "apt-get update".
            sudo('apt-get update')

    # Last try, but with warn_only with its default value of False, so we
    # get an error if we fail here.
    sudo(sudo_command)


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
def create_users():
    """Add users for running Pings server processes. Right now, all
    the Pings server processes share a single user."""
    sudo('adduser --system %s --home /srv --no-create-home --disabled-password' % PINGS_USER)

@task
def prepare_host():
    """Install all system base packages, basic Python environment,
    creates users, etc."""
    install_system_base_packages()
    bootstrap_python_install()
    create_users()

@task
def setup_virtualenv(rootdir):
    """Creates the virtualenv if needed."""
    # Running virtualenv when said virtualenv already exists and a process
    # is using it fails with a "text file busy" error on the python
    # executable. So check beforehand.
    if not files.exists(os.path.join(rootdir), 'lib'):
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
    user = PINGS_USER
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
exec start-stop-daemon --start --chdir %(rootdir)s --chuid %(user)s --name %(name)s --startas %(rootdir)s/%(program)s -- %(args)s
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
    # The -R is there in case the data dir already existed and has content,
    # but the PINGS_USER just changed, etc. We want to make sure that the
    # storage server has access to everything under the data dir, whatever
    # the configuration changes were.
    sudo('chown -R %s. %s' % (PINGS_USER, data_dir))
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

def get_python_version():
    return run('''python -c 'import platform; print("%s.%s" % tuple(platform.python_version_tuple()[:2]))' ''')

@task
@roles('test')
def deploy_test():
    """Installs everything on a single test server, able to run
    the development.ini file configuration, then starts all
    the Pings servers.

    Not to be used for deployment!"""
    prepare_source()
    pings_src_dir = upload_source()

    # Install everything. We install under a different rootdir than the
    # staging and deployment versions because we don't want to overwrite
    # things if we ever run this task mistake on the staging/deployment
    # servers.
    rootdir = '/srv/pings_test'
    setup_virtualenv(rootdir)
    with cd(rootdir):
        put('development.ini', '.', use_sudo=True)

        # Copy over Geoip db if needed.
        with cd('/srv/pings_test/local/lib/python%s/site-packages' % get_python_version()):
            geoip_filename = 'GeoLiteCity.dat'
            if not files.exists(geoip_filename):
                put(geoip_filename, '.', use_sudo=True)

        # Install all requirements.
        sudo('bin/pip install -r %s' % os.path.join(pings_src_dir, 'requirements.txt'))

        # Ignore dependencies. They are all in the requirements.pip file
        # anyways, and we don't want the --force-reinstall option to
        # reinstall all dependencies. Said --force-reinstall option is
        # there so the version we have now is installed even if the version
        # number wasn't bumped up (kinda handy during development).
        sudo('bin/pip install --no-deps --ignore-installed %s' % pings_src_dir)

    # Install and start all the services
    start_leaderboards_server(rootdir, wipe_data=True)
    start_storage_server(rootdir)
    start_http_server(rootdir)

@task
@roles('test')
def ssh_test():
    """Opens an interactive ssh session to the test hosts. Saves remembering
    the long AWS hostname and manually supplying the correct host key."""
    open_shell()


@task
def launch_new_instance(instance_type='m1.small', use_raid=False, use_32bits=False,
                        security_groups=['quicklaunch-1'], rootdir='/srv/pings'):
    """Launch a new EC2 instance, with EBS backing store. If use_raid is
    True, we will create a 2 GiB RAID10 array and mount it at rootdir,
    which defaults to /srv/pings. The default instance type is m1.small.
    Returns a tuple with the instance id"""


    # To have things survive after a reboot, the code below assumes that
    # the AMI uses an EBS backing store. These AMI's are in the us-east1
    # zone.
    if use_32bits:
        # AMI: ubuntu/images/ebs/ubuntu-oneiric-11.10-i386-server-20120222
        AMI = 'ami-a0ba68c9'
    else:
        # AMI: ubuntu/images/ebs/ubuntu-oneiric-11.10-amd64-server-20120222
        AMI = 'ami-baba68d3'

    conn = EC2Connection()

    # Launch a new EC2 instance. The rest of the code below assumes that
    # we're working on a new, "pristine" instance. Don't reuse said code if
    # that assumption changes!
    reservation = conn.run_instances(AMI, instance_type=instance_type,
                                     key_name='pings_keypair',
                                     security_groups=security_groups)

    assert len(reservation.instances) == 1
    instance = reservation.instances[0]

    # Check up on its status every so often
    print('Waiting for instance to start...', end='')
    status = instance.update()
    while status == 'pending':
        time.sleep(2)
        print('.', end='')
        status = instance.update()
    print()

    if status != 'running':
        raise RuntimeError('Instance %s failed to start! (Status: %s)' % (instance.id, status))

    # We need multiple connection attemps as the EC2 host is not ready to
    # accept ssh connections as soon as its status changes to "running".
    with(settings(host_string=instance.public_dns_name,
                  connection_attempts=5)):
        sudo('mkdir -p %s' % rootdir)
        if use_raid:
            # Create block devices.
            component_devices = ['/dev/sd' + l for l in "hijk"]
            for device in component_devices:
                # Size of volume (first argument) is in GiB. Size of the RAID10
                # array will be two times that size.
                vol = conn.create_volume(1, instance.placement)
                vol.attach(instance.id, device)

            # Install required packages. Using dont_install_recommends=True
            # to avoid pulling in Mail Transfer Agent together with mdadm
            # install.
            install_system_packages(['mdadm', 'xfsprogs'],
                                    dont_install_recommends=True)

            # Configure as RAID10 array.
            raid_device = '/dev/md0'
            sudo('mdadm --create %s --level 10 --raid-devices %d %s' %
                 (raid_device, len(component_devices),
                  ' '.join(d.replace('sd', 'xvd') for d in component_devices)))

            # Make sure RAID array persists after reboot. Linux's RAID
            # array autodetection will not work as we're not partitioning
            # the devices.
            sudo('/usr/share/mdadm/mkconf > /etc/mdadm/mdadm.conf')
            # Also make sure the new mdadn.conf is included in the initramfs.
            sudo('update-initramfs -u')

            # Format and mount on rootdir. Use noatime for better
            # performance. Use xfs for its ability to freeze the filesystem
            # (which can be used to take a consistent EBS snapshot).
            sudo('mkfs -t xfs %s' % raid_device)
            files.append('/etc/fstab', '%s     %s    xfs   defaults,relatime    0 0\n'
                         % (raid_device, rootdir), use_sudo=True)
            sudo('mount %s' % rootdir)

    print()
    print('Instance started. Id: %s, DNS name: %s' % (instance.id, instance.public_dns_name))
    return instance

@task
def uname():
    run('uname -a')
