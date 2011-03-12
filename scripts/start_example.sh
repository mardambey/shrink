export AKKA_HOME=`pwd`
export CLASSPATH=$CLASSPATH:deploy/\*
export CLASSPATH=$CLASSPATH:$AKKA_HOME/config
java -Djava.ext.dirs=$AKKA_HOME/lib -classpath $CLASSPATH shrink.FloodExample
