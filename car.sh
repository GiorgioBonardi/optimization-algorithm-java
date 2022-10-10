#!/bin/bash

# Compile the project And Run a given source file

print_usage() {
  echo "Compiles the project And Runs a given source file."
  echo
  echo "Usage:"
  echo "  ./car.sh [FILE] [ARGS...]"
  echo
  echo "Args:"
  echo "  FILE: the java path of a source file relative to 'it.unibs.mao.optalg.mkfsp'."
  echo "        If missing, 'Main' is used."
  echo "  ARGS: all other args are forwarded to the 'java' command that runs the program."
  echo
  echo "Example:"
  echo "  ./car.sh"
  echo "  Compiles the project and executes the source file 'it.unibs.mao.optalg.mkfsp.Main'."
  echo
  echo "  ./car.sh MyMain -Dproperty=value"
  echo "  Compiles the project and executes the source file 'it.unibs.mao.optalg.mkfsp.MyMain'"
  echo "  forwarding the option '-Dproperty=value' to the 'java' command."
  echo
}

if [ "$1" == "-h" -o "$1" == "--help" ]; then
  print_usage
  exit 0
fi

mvn clean package -DskipTests

if [[ `uname` = "MINGW"* ]]; then
  ENGINE_CLASSPATH="target\classes:target\lib\*:$GUROBI_HOME\lib\gurobi.jar"
else
  ENGINE_CLASSPATH="target/classes:target/lib/*:$GUROBI_HOME/lib/gurobi.jar"
fi

java -cp $ENGINE_CLASSPATH it.unibs.mao.optalg.mkfsp.${@:-Main}
