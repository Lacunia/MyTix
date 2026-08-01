#!/bin/bash

set -euo pipefail

CONNECTOR="mysql-connector-java-8.0.29.jar"

if [ ! -f "$CONNECTOR" ]; then
    echo "Missing $CONNECTOR in the project root."
    exit 1
fi

mkdir -p build
javac -cp "$CONNECTOR" -d build src/*.java src/utils/*.java
java -cp "build:$CONNECTOR" DbConnector
