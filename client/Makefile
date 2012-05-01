SRC = Pinger.java PingsClient.java PingsApplet.java TestPingers.java \
      ClientInfo.java ServerProxy.java Launcher.java IcmpPinger.java \
      TcpPinger.java TraceRouter.java

# Join multiple libraries together with ":" (e.g. foo.jar:bar.jar)
EXTERNAL_LIBS = external_libs/json-simple-1.1.1.jar:external_libs/plugin.jar
JAVADOC_OUTPUT_DIR = doc

JAVAC = javac
JFLAGS = -classpath .:$(EXTERNAL_LIBS)

JAVADOC = javadoc
JDOCFLAGS = -private -d $(JAVADOC_OUTPUT_DIR) -classpath .:$(EXTERNAL_LIBS)

.SUFFIXES: .java .class

.java.class:
	$(JAVAC) $(JFLAGS) $*.java


default: compile

compile: $(SRC:.java=.class)

doc: $(SRC:.java=.class)
	$(JAVADOC) $(JDOCFLAGS) $(SRC)

clean:
	$(RM) -v *.class *~
	$(RM) -vrf resources $(JAVADOC_OUTPUT_DIR)
