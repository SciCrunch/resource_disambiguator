package org.neuinfo.resource.disambiguator.model;

import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "rd_paper_reference")
public class PaperReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "publisher_doc_id", length = 100)
    private String publisherDocId;

    @Column(name = "pubmed_id")
    private String pubmedId;

    @Column(name = "genre", length = 40)
    private String genre;

    @Column(name = "title", length = 1000)
    private String title;

    @Column(name = "publication_name", length = 1000)
    private String publicationName;

    @Column(name = "publication_date", length = 40)
    private String publicationDate;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;

    @ManyToOne
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;

    @ManyToOne
    @JoinColumn(name = "query_log_id")
    private PublisherQueryLog publisherQueryLog;

    private int flags = 0;

    @Column(name = "c_score")
    private Double classiferScore;

    @Column(name = "description")
    @Type(type = "text")
    private String description;

    @Column(name = "authors", length = 1024)
    private String authors;

    @Column(name = "mesh_headings", length = 1024)
    private String meshHeadings;

    public final static int UNIQUE_REF = 1;
    public final static int URL_SEARCH = 2;

    public PaperReference() {
    }

    public PaperReference(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    public String getPublisherDocId() {
        return publisherDocId;
    }

    public void setPublisherDocId(String publisherDocId) {
        this.publisherDocId = publisherDocId;
    }

    public String getPubmedId() {
        return pubmedId;
    }

    public void setPubmedId(String pubmedId) {
        this.pubmedId = pubmedId;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPublicationName() {
        return publicationName;
    }

    public void setPublicationName(String publicationName) {
        this.publicationName = publicationName;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    public PublisherQueryLog getPublisherQueryLog() {
        return publisherQueryLog;
    }

    public void setPublisherQueryLog(PublisherQueryLog publisherQueryLog) {
        this.publisherQueryLog = publisherQueryLog;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public Double getClassiferScore() {
        return classiferScore;
    }

    public void setClassiferScore(Double classiferScore) {
        this.classiferScore = classiferScore;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMeshHeadings() {
        return meshHeadings;
    }

    public void setMeshHeadings(String meshHeadings) {
        this.meshHeadings = meshHeadings;
    }
}
