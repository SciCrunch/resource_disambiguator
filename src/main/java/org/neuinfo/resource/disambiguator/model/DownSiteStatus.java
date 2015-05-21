package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;
import java.util.Calendar;

/**
 * Created by bozyurt on 8/13/14.
 */

@Entity
@Table(name = "rd_down_site_status")
public class DownSiteStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "resource_name")
    String resourceName;
    @Column(name = "nif_id", length = 50)
    String nifId;
    @Column(name = "message")
    String message;
    @Column(name = "url")
    String url;


    @Column(name = "batch_id", length = 8)
    String batchId;

    @Column(name = "label", length = 5)
    String label;

    @Column(name = "num_of_consecutive_checks")
    Integer numOfConsecutiveChecks;

    @Column(name = "last_checked_time")
    Calendar lastCheckedTime;

    @Column(name = "mod_time")
    Calendar modTime = Calendar.getInstance();

    @Column(name = "modified_by", length = 40)
    String modifiedBy;

    public long getId() {
        return id;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getNifId() {
        return nifId;
    }

    public void setNifId(String nifId) {
        this.nifId = nifId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getNumOfConsecutiveChecks() {
        return numOfConsecutiveChecks;
    }

    public void setNumOfConsecutiveChecks(Integer numOfConsecutiveChecks) {
        this.numOfConsecutiveChecks = numOfConsecutiveChecks;
    }

    public Calendar getLastCheckedTime() {
        return lastCheckedTime;
    }

    public void setLastCheckedTime(Calendar lastCheckedTime) {
        this.lastCheckedTime = lastCheckedTime;
    }

    public Calendar getModTime() {
        return modTime;
    }

    public void setModTime(Calendar modTime) {
        this.modTime = modTime;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }
}
