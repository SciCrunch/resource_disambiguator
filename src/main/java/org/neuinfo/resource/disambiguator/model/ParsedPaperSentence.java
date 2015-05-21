package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 * Created by bozyurt on 9/22/14.
 */
@Entity
@Table(name = "rd_parsed_paper_sentence")
public class ParsedPaperSentence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(name = "pubmed_id", length = 20, nullable = false)
    private String pubmedId;
    @Column(name = "sentence", length = 500, nullable = false)
    private String sentence;
    @Column(name = "pt", length = 1024, nullable = false)
    private String pt;

    public long getId() {
        return id;
    }

    public String getPubmedId() {
        return pubmedId;
    }

    public void setPubmedId(String pubmedId) {
        this.pubmedId = pubmedId;
    }

    public String getSentence() {
        return sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }

    public String getPt() {
        return pt;
    }

    public void setPt(String pt) {
        this.pt = pt;
    }
}
