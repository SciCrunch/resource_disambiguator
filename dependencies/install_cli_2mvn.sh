#!/bin/sh


mvn install:install-file -Dfile=$HOME/dev/resource_disambiguator/dependencies/lib/commons-cli-1.3-SNAPSHOT.jar -DgroupId=commons-cli -DartifactId=commons-cli -Dversion=1.3-SNAPSHOT -Dpackaging=jar

