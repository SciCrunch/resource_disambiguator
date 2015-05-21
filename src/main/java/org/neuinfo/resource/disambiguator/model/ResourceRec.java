package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 * Created by bozyurt on 12/19/13.
 */

@Entity
@Table(name = "rd_resource_ref")
public class ResourceRec {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "context", length = 2048)
    private String context;

    @Column(name = "entity", length = 1000)
    private String entity;

    @Column(name = "start_idx")
    private int startIdx;

    @Column(name = "end_idx")
    private int endIdx;

    @ManyToOne
    @JoinColumn(name = "doc_id")
    private Paper paper;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;

    private int flags = 0;

    @Column(name = "c_score")
    private Double classiferScore;

    public final static int DUPLICATE = 1;

    public Double getClassiferScore() {
        return classiferScore;
    }

    public void setClassiferScore(Double classiferScore) {
        this.classiferScore = classiferScore;
    }

    public long getId() {
        return id;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public int getStartIdx() {
        return startIdx;
    }

    public void setStartIdx(int startIdx) {
        this.startIdx = startIdx;
    }

    public int getEndIdx() {
        return endIdx;
    }

    public void setEndIdx(int endIdx) {
        this.endIdx = endIdx;
    }

    public Paper getPaper() {
        return paper;
    }

    public void setPaper(Paper paper) {
        this.paper = paper;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResourceRec{");
        sb.append("id=").append(id);
        sb.append(", entity='").append(entity).append('\'');
        sb.append(", context='").append(context).append('\'');
        sb.append(", startIdx=").append(startIdx);
        sb.append(", endIdx=").append(endIdx);
        sb.append(", paper=").append(paper);
        sb.append(", registry=").append(registry);
        sb.append('}');
        return sb.toString();
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }
}
