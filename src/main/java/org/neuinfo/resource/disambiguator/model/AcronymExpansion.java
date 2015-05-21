package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 * Created by bozyurt on 10/7/14.
 */
@Entity
@Table(name = "rd_acronym_expansion")
public class AcronymExpansion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "acr_id")
    private Acronym acronym;

    @Column(name = "expansion", nullable = false)
    private String expansion;

    @Column(name = "frequency", nullable = false)
    private int frequency;
    @Column(name = "cluster_id")
    private int clusterId;

    public long getId() {
        return id;
    }


    public Acronym getAcronym() {
        return acronym;
    }

    public void setAcronym(Acronym acronym) {
        this.acronym = acronym;
    }

    public String getExpansion() {
        return expansion;
    }

    public void setExpansion(String expansion) {
        this.expansion = expansion;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getClusterId() {
        return clusterId;
    }

    public void setClusterId(int clusterId) {
        this.clusterId = clusterId;
    }
}
