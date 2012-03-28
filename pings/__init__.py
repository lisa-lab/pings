import ConfigParser
import gevent.monkey
from pyramid.config import Configurator
from pings.resources import Root, init_storage_zmq, init_token_memcache, init_geoip

def _get_config_list(config_parser, section, item_prefix):
    """Supports storing a list of items in a .ini file. Expects a
    ConfigParser instance as its first argument. Then, say the section is
    'foo' and the item_prefix is "url". Then you can store a single url as:

    [foo]
    url = http://host.name/an/url/here

    Or you can store multiple urls as:

    [foo]
    url.0 = http://host.name/an/url/here
    url.1 = http://host.name/another/url/here
    url.2 = http://host.name/a/third/url/here

    The _get_config_list function will then return a list containing either
    the one (in the first case) or 3 (in the second case) urls. The suffixes
    after the "." can be anything, and there is no guarantee on the order
    of the items in the returned list.
    """
    return [config_parser.get(section, name)
            for name in config_parser.options(section)
            if name == item_prefix or name.startswith(item_prefix + '.')]

def main(global_config, **settings):
    """ This function returns the Pings WSGI application."""
    gevent.monkey.patch_all()

    # Load Pings-specific config.
    config_parser = ConfigParser.SafeConfigParser()
    config_parser.read(global_config['__file__'])
    init_geoip()
    init_token_memcache(_get_config_list(config_parser, 'token_memcache', 'server_address'),
                        config_parser.getint('token_memcache', 'token_expiration_sec'))
    init_storage_zmq(_get_config_list(config_parser, 'storage_client', 'server_url'))
    
    # Configure Pyramid app.
    config = Configurator(root_factory=Root, settings=settings)
    config.add_route('get_pings', '/get_pings')
    config.add_route('submit_ping_results', '/submit_ping_results')
    config.scan()

    return config.make_wsgi_app()
