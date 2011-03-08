AKKA_HOME=`pwd`
CLASSPATH=$CLASSPATH:$AKKA_HOME/config
java -Djava.ext.dirs=$AKKA_HOME/lib -classpath $CLASSPATH akka.kernel.Main
