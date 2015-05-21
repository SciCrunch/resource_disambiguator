package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;
import java.util.Calendar;

/**
 * Created by bozyurt on 1/30/14.
 */
@Entity
@Table(name = "rd_ps_annot_info")
public class PubSearchAnnotationInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(length = 5)
    private String label;

    @Column(length = 1024)
    private String notes;

    @Column(name = "mod_time")
    private Calendar modificationTime = Calendar.getInstance();

    @ManyToOne
    @JoinColumn(name = "pr_id")
    private PaperReference paperReference;

    @Column(name = "op_type", length = 30)
    private String opType = "ps_filter";

    @Column(name = "modified_by", length = 40)
    private String modifiedBy;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;

    public long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public PaperReference getPaperReference() {
        return paperReference;
    }

    public void setPaperReference(PaperReference paperReference) {
        this.paperReference = paperReference;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PubSearchAnnotationInfo::[");
        sb.append("id=").append(id);
        sb.append(", label='").append(label).append('\'');
        sb.append(", notes='").append(notes).append('\'');
        sb.append(", modificationTime=").append(modificationTime);
        sb.append(", paperReference=").append(paperReference);
        sb.append(", opType='").append(opType).append('\'');
        sb.append(", modifiedBy='").append(modifiedBy).append('\'');
        sb.append(", registry=").append(registry);
        sb.append(']');
        return sb.toString();
    }
}
