#!/bin/bash

CP=/usr/share/resource_disambiguator:$HOME/resource-disambiguator-prod.jar

java -Xmx1024M -cp $CP org.neuinfo.resource.disambiguator.util.PaperPathPopulator $*
 
 
