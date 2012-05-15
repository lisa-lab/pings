SRC = Prober.java PingsClient.java PingsApplet.java TestPingers.java \
      ClientInfo.java ServerProxy.java Launcher.java IcmpPinger.java \
      TcpPinger.java TraceRouter.java GeoipInfo.java PingGlobe.java


# Join multiple libraries together with ":" (e.g. foo.jar:bar.jar)
EXTERNAL_LIBS = external_libs/json-simple-1.1.1.jar:external_libs/plugin.jar:globe

ADDITIONAL_SRC = globe
JAVADOC_OUTPUT_DIR = doc

ADDITIONAL_DIR_TO_CLEAN = com

JAVAC = javac
JFLAGS = -classpath .:$(EXTERNAL_LIBS)

JAVADOC = javadoc
JDOCFLAGS = -private -d $(JAVADOC_OUTPUT_DIR) -classpath .:$(EXTERNAL_LIBS) -sourcepath $(ADDITIONAL_SRC) -Xlint:unchecked

JAR = jar

.SUFFIXES: .java .class

.java.class:
	$(JAVAC) $(JFLAGS) $*.java


default: compile

compile: $(SRC:.java=.class)

doc: $(SRC:.java=.class)
	$(JAVADOC) $(JDOCFLAGS) $(SRC)

jar: compile
	$(JAR) cf pings.jar *.class
        # The -C option to jar only applies to the next argument, so ask
        # xargs to pass one file at a time with -L 1.
	(cd globe; find . -type f -name \*.class -print0) | xargs -L 1 -0 $(JAR) uf pings.jar -C globe
        # Add contents of external_libs...
	tempdir=`mktemp -d`; echo $$tempdir; for f in $$PWD/external_libs/*.jar; do (cd $$tempdir; jar xf $$f); done; \
	$(RM) -rf $$tempdir/META-INF; $(JAR) uf pings.jar -C $$tempdir .; $(RM) -rf $$tempdir
        # Add globe data files.
	$(JAR) uf pings.jar -C globe world.shp -C globe world.dbf
        # Add Jar Index.
	$(JAR) i pings.jar

clean:
	$(RM) -vrf  $(ADDITIONAL_DIR_TO_CLEAN)
	find . -name "*.class" -delete
	find . -name "*.~" -delete
	$(RM) -vrf resources $(JAVADOC_OUTPUT_DIR)
