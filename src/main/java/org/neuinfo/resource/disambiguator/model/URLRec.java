package org.neuinfo.resource.disambiguator.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "rd_urls")
public class URLRec {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "line_number_in_file")
    private int lineNumberInFile;

    @Column(name = "score")
    private Double score;

    @Column(name = "rank")
    private int rank;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "update_info", length = 2048)
    private String updateInfo;
    /**
     * format YYYYMM (year and month)
     */
    @Column(name = "batch_id", length = 6)
    private String batchId;

    @Column(name = "c_score")
    private Double classiferScore;

    @Column(name = "context", length = 2048)
    private String context;

    @ManyToOne
    @JoinColumn(name = "doc_id")
    private Paper paper;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;

    private int flags;

    @Column(name="resource_type_src")
    private Integer resourceTypeSource;

    @Column(name = "host_link_size")
    private Integer hostLinkSize;

    public final static int FROM_DEDUP_REG = 1;
    public final static int FROM_DEDUP_REG_SIM = 2;
    public final static int FROM_HOST_EXPANSION = 4;


    public final static int RTSC_HUMAN = 1;
    public final static int RTSC_CLASSIFIER = 2;
    public URLRec() {
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUpdateInfo() {
        return updateInfo;
    }

    public void setUpdateInfo(String updateInfo) {
        this.updateInfo = updateInfo;
    }

    public long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getLineNumberInFile() {
        return lineNumberInFile;
    }

    public void setLineNumberInFile(int lineNumberInFile) {
        this.lineNumberInFile = lineNumberInFile;
    }

    public Paper getPaper() {
        return paper;
    }

    public void setPaper(Paper paper) {
        this.paper = paper;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }


    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public Double getClassiferScore() {
        return classiferScore;
    }

    public void setClassiferScore(Double classiferScore) {
        this.classiferScore = classiferScore;
    }

    public  Integer getHostLinkSize() {
        return hostLinkSize;
    }

    public void setHostLinkSize(Integer hostLinkSize){
        this.hostLinkSize = hostLinkSize;
    }

    public Integer getResourceTypeSource() {
        return resourceTypeSource;
    }

    public void setResourceTypeSource(Integer resourceTypeSource) {
        this.resourceTypeSource = resourceTypeSource;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("URLRec [id=");
        builder.append(id);
        builder.append(", ");
        if (url != null) {
            builder.append("url=");
            builder.append(url);
            builder.append(", ");
        }
        if (score != null) {
            builder.append("score=");
            builder.append(score);
            builder.append(", ");
        }
        if (resourceType != null) {
            builder.append("resourceType=");
            builder.append(resourceType);
            builder.append(", ");
        }
        if (description != null) {
            builder.append("description=");
            builder.append(description);
            builder.append(", ");
        }
        if (batchId != null) {
            builder.append("batchId=");
            builder.append(batchId);
            builder.append(", ");
        }
        if (classiferScore != null) {
            builder.append("classiferScore=");
            builder.append(classiferScore);
            builder.append(", ");
        }
        if (paper != null) {
            builder.append("paper=");
            builder.append(paper);
            builder.append(", ");
        }
        if (registry != null) {
            builder.append("registry=");
            builder.append(registry);
            builder.append(", ");
        }

        builder.append("rank=");
        builder.append(rank);
        builder.append(", ");

        builder.append("flags=");
        builder.append(flags);
        builder.append(", ");
        if (updateInfo != null) {
            builder.append("updateInfo=");
            builder.append(updateInfo);
            builder.append(", ");
        }
        builder.append("lineNumberInFile=");
        builder.append(lineNumberInFile);
        builder.append("]");
        return builder.toString();
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }


    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }
}
