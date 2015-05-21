package org.neuinfo.resource.disambiguator.services;

import java.util.ArrayList;
import java.util.List;

public class ArticleRec {
    String identifier;
    String genre;
    String publicationDate;
    String publicationName;
    String title;
    String pmid;
    List<String> authors;
    List<String> meshHeadings;
    String description;

    public void addAuthor(String author) {
        if (authors == null) {
            authors = new ArrayList<String>(3);
        }
        authors.add(author);
    }

    public void addMeshHeading(String heading) {
        if (meshHeadings == null) {
            meshHeadings = new ArrayList<String>(5);
        }
        meshHeadings.add(heading);
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getGenre() {
        return genre;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public String getPublicationName() {
        return publicationName;
    }

    public String getTitle() {
        return title;
    }

    public String getPmid() {
        return pmid;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public List<String> getMeshHeadings() {
        return meshHeadings;
    }

    public String getDescription() {
        return description;
    }

    public void setPmid(String pmid) {
        this.pmid = pmid;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ArticleRec{");
        sb.append("identifier='").append(identifier).append('\'');
        sb.append(", genre='").append(genre).append('\'');
        sb.append(", publicationDate='").append(publicationDate).append('\'');
        sb.append(", publicationName='").append(publicationName).append('\'');
        sb.append(", title='").append(title).append('\'');
        sb.append(", pmid='").append(pmid).append('\'');
        if (authors != null) {
            sb.append(",authors:[");
            for(String author: authors) {
                sb.append(author).append(',');
            }
            sb.append("]");
        }
        if (description != null) {
            int max = Math.min(description.length(), 100);
            sb.append("\n,description:").append(description.substring(0, max));
        }
        if (meshHeadings != null) {
            sb.append("\n,meshHeadings:[");
            for(String mh : meshHeadings) {
                sb.append(mh).append(',');
            }
            sb.append("]");
        }
        sb.append('}');
        return sb.toString();
    }
}