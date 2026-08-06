#!/bin/bash

set -euo pipefail

CONNECTOR="mysql-connector-java-8.0.29.jar"
OPENNLP="opennlp-tools-2.3.3.jar"

if [ ! -f "$CONNECTOR" ]; then
    echo "Missing $CONNECTOR in the project root."
    exit 1
fi
if [ ! -f "$OPENNLP" ]; then
    echo "Missing $OPENNLP in the project root (required for R9's noun-phrase extraction)."
    exit 1
fi

CP="$CONNECTOR:$OPENNLP"

mkdir -p build
javac -cp "$CP" -d build src/*.java src/utils/*.java
java -cp "build:$CP" DbConnector
