#!/bin/bash

# Later change to where the java files actually are
javac -cp "mysql-connector-java-8.0.29.jar" *.java utils/*.java && \
java -cp ".:mysql-connector-java-8.0.29.jar" DbConnector