package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;

/**
 *
 * Created by bozyurt on 1/10/14.
 */
@Entity
@Table(name = "rd_paper_path")
public class PaperPath {
    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "file_path", nullable = false)
	private String filePath;

    private Integer flags;

    public final static int PMC_OAI = 1;
    public final static int SPRINGER_ETC = 2;


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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PaperPath paperPath = (PaperPath) o;

        if (!filePath.equals(paperPath.filePath)) return false;
        if (!flags.equals(paperPath.flags)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = filePath.hashCode();
        result = 31 * result + flags.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PaperPath{");
        sb.append("id=").append(id);
        sb.append(", filePath='").append(filePath).append('\'');
        sb.append(", flags=").append(flags);
        sb.append('}');
        return sb.toString();
    }
}
