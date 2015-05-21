package org.neuinfo.resource.disambiguator.services;

import java.util.ArrayList;
import java.util.List;

/**
* Created by bozyurt on 12/19/13.
*/
public class PaperUrls {// Class to keep extracted data
    final String PMID;
    final String PMCID;
    final String title;
    final String journalTitle;
    final String filePath;
    List<ExtractUrlsFromPMCService.URLLocInfo> urls;
    List<ResourceInfo> resourceList;

    public PaperUrls(String pMID, String pMCID, String filePath,
                     List<ExtractUrlsFromPMCService.URLLocInfo> urls, String title, String journalTitle) {
        super();
        PMID = pMID;
        PMCID = pMCID;
        this.filePath = filePath;
        this.urls = urls;
        this.title = title;
        this.journalTitle = journalTitle;
    }

    public void addResource(ResourceInfo ri) {
        if (resourceList == null) {
            resourceList = new ArrayList<ResourceInfo>(2);
        }
        resourceList.add(ri);
    }

    public List<ResourceInfo> getResourceList() {
        return resourceList;
    }

    public String getPMID() {
        return PMID;
    }

    public String getPMCID() {
        return PMCID;
    }

    public List<ExtractUrlsFromPMCService.URLLocInfo> getUrls() {
        return urls;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getTitle() {
        return title;
    }

    public String getJournalTitle() {
        return journalTitle;
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PaperUrls [");
        if (PMID != null) {
            builder.append("PMID=");
            builder.append(PMID);
            builder.append(", ");
        }
        if (PMCID != null) {
            builder.append("PMCID=");
            builder.append(PMCID);
            builder.append(", ");
        }
        if (filePath != null) {
            builder.append("filePath=");
            builder.append(filePath);
            builder.append(", ");
        }
        if (urls != null) {
            builder.append("\nurls=");
            for(ExtractUrlsFromPMCService.URLLocInfo uli : urls) {
                builder.append("\t").append(uli).append("\n");
            }
        }
        builder.append("]");
        return builder.toString();
    }

   public static class ResourceInfo {
       final String entity;
       final int startIdx;
       final int endIdx;
       final String sentence;

       public ResourceInfo(String entity, int startIdx, int endIdx, String sentence) {
           this.entity = entity;
           this.startIdx = startIdx;
           this.endIdx = endIdx;
           this.sentence = sentence;
       }

       public String getEntity() {
           return entity;
       }

       public int getStartIdx() {
           return startIdx;
       }

       public int getEndIdx() {
           return endIdx;
       }

       public String getSentence() {
           return sentence;
       }
   }
}// ;
