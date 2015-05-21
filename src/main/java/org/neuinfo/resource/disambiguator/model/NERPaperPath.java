package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 * Created by bozyurt on 1/22/14.
 */

@Entity
@Table(name = "rd_ner_paper_path")
public class NERPaperPath {
    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "file_path", nullable = false)
	private String filePath;

    private Integer flags = 0;

    public long getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getFlags() {
        return flags;
    }

    public void setFlags(Integer flags) {
        this.flags = flags;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NERPaperPath{");
        sb.append("id=").append(id);
        sb.append(", filePath='").append(filePath).append('\'');
        sb.append(", flags=").append(flags);
        sb.append('}');
        return sb.toString();
    }
}
