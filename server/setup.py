import os

from setuptools import setup, find_packages

here = os.path.abspath(os.path.dirname(__file__))
README = open(os.path.join(here, 'README.txt')).read()

version = '1.0'
# Use requirements.txt for all requirements, at least for now.
requires = []


if __name__ == '__main__':
    setup(name='pings',
          version=version,
          description='pings',
          long_description=README,
          classifiers=[
            "Programming Language :: Python",
            "Framework :: Pylons",
            "Topic :: Internet :: WWW/HTTP",
            "Topic :: Internet :: WWW/HTTP :: WSGI :: Application",
            ],
          author='Christian Hudon',
          author_email='chrish@pianocktail.org',
          url='https://github.com/lisa-lab/pings',
          keywords='web pyramid pylons',
          packages=find_packages(),
          include_package_data=True,
          zip_safe=False,
          install_requires=requires,
          tests_require=requires,
          test_suite="pings",
          scripts = ['leaderboards_server', 'storage_server'],
          entry_points = """\
          [paste.app_factory]
          main = pings.web_server:main
          """,
          paster_plugins=['pyramid'],
          )
