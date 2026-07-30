#!/bin/bash
javac -cp "mysql-connector-java-8.0.29.jar" DbConnector.java SearchQueries.java Reports.java EnvConfig.java UserOperations.java EventOperations.java BookingOperations.java ResaleOperations.java ReviewOperations.java OrganizerToolkit.java && \
java -cp ".:mysql-connector-java-8.0.29.jar" DbConnector