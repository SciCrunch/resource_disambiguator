#!/bin/sh

mvn install:install-file -Dfile=$HOME/dev/resource_disambiguator/dependencies/lib/jsvmlight.jar -DgroupId=bnlp -DartifactId=jsvmlight -Dversion=0.1 -Dpackaging=jar

