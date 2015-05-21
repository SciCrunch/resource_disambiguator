package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 * Created by bozyurt on 10/7/14.
 */
@Entity
@Table(name = "rd_acronym")
public class Acronym {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(name = "acronym", length = 100, nullable = false)
    private String acronym;

    @Column(name = "frequency", nullable = false)
    private int frequency;

    public long getId() {
        return id;
    }

    public String getAcronym() {
        return acronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }
}
