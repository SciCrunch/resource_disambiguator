package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;
import java.util.Calendar;

@Entity
@Table(name = "rd_reg_update_status")
public class RegistryUpdateStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;


    @Column(name = "update_year", length = 4)
    private String updateYear;

    @Column(name = "update_line", length = 1000)
    private String updateLine;

    @Column(name = "last_checked_time")
    private Calendar lastCheckedTime = Calendar.getInstance();

    @Column(name = "similarity")
    private Double similarity;

    @Column(name = "containment")
    private Double containment;

    @Column(name = "batch_id", length = 8)
    private String batchId;

    @Column(name = "cos_similarity")
    private Double cosSimilarity;

    @Column(name = "sem_similarity")
    private Double semanticSimilarity;

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public String getUpdateYear() {
        return updateYear;
    }

    public void setUpdateYear(String updateYear) {
        this.updateYear = updateYear;
    }

    public String getUpdateLine() {
        return updateLine;
    }

    public void setUpdateLine(String updateLine) {
        this.updateLine = updateLine;
    }

    public Calendar getLastCheckedTime() {
        return lastCheckedTime;
    }

    public void setLastCheckedTime(Calendar lastCheckedTime) {
        this.lastCheckedTime = lastCheckedTime;
    }

    public long getId() {
        return id;
    }


    public Double getContainment() {
        return containment;
    }

    public void setContainment(Double containment) {
        this.containment = containment;
    }

    public Double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public Double getSemanticSimilarity() {
        return semanticSimilarity;
    }

    public void setSemanticSimilarity(Double semanticSimilarity) {
        this.semanticSimilarity = semanticSimilarity;
    }

    public Double getCosSimilarity() {
        return cosSimilarity;
    }

    public void setCosSimilarity(Double cosSimilarity) {
        this.cosSimilarity = cosSimilarity;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RegistryUpdateStatus{");
        sb.append("id=").append(id);
        sb.append(", registry=").append(registry);
        sb.append(", updateYear='").append(updateYear).append('\'');
        sb.append(", updateLine='").append(updateLine).append('\'');
        sb.append(", lastCheckedTime=").append(lastCheckedTime);
        sb.append(", similarity=").append(similarity);
        sb.append(", containment=").append(containment);
        sb.append(", batchId=").append(batchId);
        sb.append('}');
        return sb.toString();
    }
}
