#!/bin/sh

mvn install:install-file -Dfile=$HOME/dev/resource_disambiguator/dependencies/lib/opennlp-maxent-3.0.3.jar -DgroupId=bnlp -DartifactId=opennlp-maxent -Dversion=3.0.3 -Dpackaging=jar

mvn install:install-file -Dfile=$HOME/dev/resource_disambiguator/dependencies/lib/opennlp-tools-1.5.3.jar -DgroupId=bnlp -DartifactId=opennlp-tools -Dversion=1.5.3 -Dpackaging=jar

mvn install:install-file -Dfile=$HOME/dev/resource_disambiguator/dependencies/lib/jsvmlight.jar -DgroupId=bnlp -DartifactId=jsvmlight -Dversion=0.1 -Dpackaging=jar

