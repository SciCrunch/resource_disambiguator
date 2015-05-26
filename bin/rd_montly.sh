#!/bin/bash

echo "rd_monthly task"
echo "date is `date`"
now=$(date +'%T');
echo "starting processing at $now"

mon=$(date '+%m');
year=$(date '+%y');

batchId=20$year$mon
echo "batchId:$batchId"

#./registry_sync.sh

./download_pmc.sh -d $batchId

./extract_url_pmc.sh -d $batchId

./resource_candidate_finder.sh -d $batchId

./url_scorer.sh -d $batchId

./desc_extractor.sh -d $batchId

./extract_ner_pmc.sh -d $batchId

# ---
./search_4references.sh 
#./search_4references.sh -d 1000 
#./search_4references.sh -e nature,nif 

./uniq_paper_ref_service.sh




