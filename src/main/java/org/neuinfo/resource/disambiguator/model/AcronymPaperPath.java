package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 * Created by bozyurt on 9/22/14.
 */
@Entity
@Table(name = "rd_acr_paper_path")
public class AcronymPaperPath {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(name = "file_path", nullable = false)
    private String filePath;
    @Column(name = "pubmed_id", length = 20)
    private String pubmedId;

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
}
