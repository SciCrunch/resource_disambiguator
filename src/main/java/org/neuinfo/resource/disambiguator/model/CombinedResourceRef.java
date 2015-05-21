package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 *
 * Created by bozyurt on 2/6/14.
 */

@Entity
@Table(name = "rd_comb_resource_ref")
public class CombinedResourceRef {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "pubmed_id", nullable = false)
    private String pubmedId;

    @Column(name = "nif_id", length = 30, nullable = false)
    private String nifId;

    @Column(name = "registry_id", nullable = false)
    private int registryId;

    @Column(name = "src", length = 10, nullable = false)
    private String source;

    @Column(name = "confidence")
    private Double confidence;

    public long getId() {
        return id;
    }

    public String getPubmedId() {
        return pubmedId;
    }

    public void setPubmedId(String pubmedId) {
        this.pubmedId = pubmedId;
    }

    public String getNifId() {
        return nifId;
    }

    public void setNifId(String nifId) {
        this.nifId = nifId;
    }

    public int getRegistryId() {
        return registryId;
    }

    public void setRegistryId(int registryId) {
        this.registryId = registryId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
}
