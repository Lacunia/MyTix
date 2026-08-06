#!/bin/bash

set -euo pipefail

# Always run relative to this script's own location (the project root), not
# wherever the caller's shell happened to be -- run.sh, models/, and the
# jars are all referenced by relative path below.
cd "$(dirname "${BASH_SOURCE[0]}")"

CONNECTOR="mysql-connector-java-8.0.29.jar"
OPENNLP="opennlp-tools-2.3.3.jar"
SLF4J="slf4j-api-2.0.13.jar"

if [ ! -f "$CONNECTOR" ]; then
    echo "Missing $CONNECTOR in the project root."
    exit 1
fi
if [ ! -f "$OPENNLP" ]; then
    echo "Missing $OPENNLP in the project root (required for R9's noun-phrase extraction)."
    exit 1
fi
if [ ! -f "$SLF4J" ]; then
    echo "Missing $SLF4J in the project root (opennlp-tools depends on it at runtime)."
    exit 1
fi

CP="$CONNECTOR:$OPENNLP:$SLF4J"

mkdir -p build
javac -cp "$CP" -d build src/*.java src/utils/*.java
java -cp "build:$CP" DbConnector
