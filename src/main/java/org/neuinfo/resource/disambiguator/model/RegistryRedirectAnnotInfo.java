package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;
import java.util.Calendar;

/**
 * Created by bozyurt on 4/9/14.
 */

@Entity
@Table(name = "rd_reg_redirect_annot_info")
public class RegistryRedirectAnnotInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @Column(length = 5)
    private String label;

    @Column(name = "c_score", nullable = false)
    private Double classiferScore;

    @Column(length = 1024)
    private String notes;

    @Column(name = "mod_time")
    private Calendar modificationTime = Calendar.getInstance();

    @Column(name = "status", nullable = false)
    private Integer status = NEEDS_USER_ATTENTION;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;

    @Column(name = "modified_by", length = 40)
    private String modifiedBy;


    public final static Integer NEEDS_USER_ATTENTION = 1;
    public final static Integer CONFIRMED = 2;

    public long getId() {
        return id;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Double getClassiferScore() {
        return classiferScore;
    }

    public void setClassiferScore(Double classiferScore) {
        this.classiferScore = classiferScore;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Calendar getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(Calendar modificationTime) {
        this.modificationTime = modificationTime;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
