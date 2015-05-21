#!/bin/sh


mvn install:install-file -Dfile=$HOME/dev/resource_disambiguator/dependencies/lib/stanford-corenlp-1.3.4.jar -DgroupId=edu.stanford.nlp -DartifactId=stanford-corenlp -Dversion=1.3.4 -Dpackaging=jar

mvn install:install-file -Dfile=$HOME/dev/resource_disambiguator/dependencies/lib/stanford-corenlp-models-1.3.4.jar -DgroupId=edu.stanford.nlp -DartifactId=stanford-corenlp-models -Dversion=1.3.4 -Dpackaging=jar


