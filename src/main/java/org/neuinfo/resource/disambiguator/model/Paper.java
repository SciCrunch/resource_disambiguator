package org.neuinfo.resource.disambiguator.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = "rd_paper")
public class Paper {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "file_path")
	String filePath;

	@Column(name = "pubmed_id")
	String pubmedId;

	@Column(name = "doi")
	String doi;

	@Column(name = "pmc_id", length = 20)
	String pmcId;
	
	@Column(name = "journal_title", length = 1000)
	String journalTitle;
	
	@Column(name = "title", length = 2000)
	String title;

    @Column(name = "pubdate", length = 10)
    String publicationDate;

	@OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<URLRec> urls = new HashSet<URLRec>(17);


    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, orphanRemoval =  true)
    private Set<ResourceRec> resourceRefs = new HashSet<ResourceRec>(7);

	public Paper() {
	}

    public Set<ResourceRec> getResourceRefs() {
        return resourceRefs;
    }

    public void setResourceRefs(Set<ResourceRec> resourceRefs) {
        this.resourceRefs = resourceRefs;
    }

    public Set<URLRec> getUrls() {
		return urls;
	}

	public void setUrls(Set<URLRec> urls) {
		this.urls = urls;
	}

	public long getId() {
		return id;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public String getPubmedId() {
		return pubmedId;
	}

	public void setPubmedId(String pubmedId) {
		this.pubmedId = pubmedId;
	}

	public String getDoi() {
		return doi;
	}

	public void setDoi(String doi) {
		this.doi = doi;
	}

	public String getPmcId() {
		return pmcId;
	}

	public void setPmcId(String pmcId) {
		this.pmcId = pmcId;
	}


	public String getJournalTitle() {
		return journalTitle;
	}

	public void setJournalTitle(String journalTitle) {
		this.journalTitle = journalTitle;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    @Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Paper [id=");
		builder.append(id);
		builder.append(", ");
		if (filePath != null) {
			builder.append("filePath=");
			builder.append(filePath);
			builder.append(", ");
		}
		if (pubmedId != null) {
			builder.append("pubmedId=");
			builder.append(pubmedId);
			builder.append(", ");
		}
		if (title != null) {
			builder.append("title=");
			builder.append(title);
			builder.append(", ");
		}
		if (journalTitle != null) {
			builder.append("journalTitle=");
			builder.append(journalTitle);
			builder.append(", ");
		}
		if (doi != null) {
			builder.append("doi=");
			builder.append(doi);
			builder.append(", ");
		}
		if (pmcId != null) {
			builder.append("pmcId=");
			builder.append(pmcId);
			builder.append(", ");
		}
		builder.append("]");
		return builder.toString();
	}

	
}
