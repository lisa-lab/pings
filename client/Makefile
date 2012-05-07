SRC = Pinger.java PingsClient.java PingsApplet.java TestPingers.java \
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

.SUFFIXES: .java .class

.java.class:
	$(JAVAC) $(JFLAGS) $*.java


default: compile

compile: $(SRC:.java=.class)

doc: $(SRC:.java=.class)
	$(JAVADOC) $(JDOCFLAGS) $(SRC)

clean:
	$(RM) -vrf  $(ADDITIONAL_DIR_TO_CLEAN)
	find . -name "*.class" -delete
	find . -name "*.~" -delete
	$(RM) -vrf resources $(JAVADOC_OUTPUT_DIR)
