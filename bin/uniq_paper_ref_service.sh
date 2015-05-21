#!/bin/bash

CP=/usr/share/resource_disambiguator:$HOME/resource-disambiguator-prod.jar

java -Xmx5000M -cp $CP org.neuinfo.resource.disambiguator.services.UniquePaperReferenceService $*
 
