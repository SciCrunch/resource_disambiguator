#!/bin/sh
mvn install:install-file -Dfile=$HOME/dev/resource_disambiguator/dependencies/lib/bnlpkit-models-0.6.jar -DgroupId=bnlp -DartifactId=bnlpkit-models -Dversion=0.6 -Dpackaging=jar

