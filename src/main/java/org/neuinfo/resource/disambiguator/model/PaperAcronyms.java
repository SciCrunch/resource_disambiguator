package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 * Created by bozyurt on 9/19/14.
 */
@Entity
@Table(name = "rd_paper_acronyms")
public class PaperAcronyms {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "pubmed_id", length = 20)
    private String pubmedId;

    @Column(name = "acronym", length = 100)
    private String acronym;

    @Column(name = "expansion")
    private String expansion;

    public long getId() {
        return id;
    }


    public String getPubmedId() {
        return pubmedId;
    }

    public void setPubmedId(String pubmedId) {
        this.pubmedId = pubmedId;
    }

    public String getAcronym() {
        return acronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public String getExpansion() {
        return expansion;
    }

    public void setExpansion(String expansion) {
        this.expansion = expansion;
    }
}
