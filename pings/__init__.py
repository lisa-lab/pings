import gevent.monkey
from pyramid.config import Configurator
from pings.resources import Root, init_zmq, init_token_memcache

def main(global_config, **settings):
    """ This function returns a Pyramid WSGI application."""
    gevent.monkey.patch_all()

    init_token_memcache()
    init_zmq()
    
    config = Configurator(root_factory=Root, settings=settings)
    config.add_route('get_pings', '/get_pings')
    config.add_route('submit_ping_results', '/submit_ping_results')
    config.scan()

    return config.make_wsgi_app()
