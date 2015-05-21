package org.neuinfo.resource.disambiguator.model;

import java.util.Calendar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * 
 * @author bozyurt
 * 
 */
@Entity
@Table(name = "rd_resource_candidate")
public class ResourceCandidate {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@OneToOne
	@JoinColumn(name = "url_id")
	private URLRec url;

	@Column(name = "score")
	private Double score;

	@Column(name = "resource_type")
	private String resourceType;

	@Column(name = "title", length = 512)
	private String title;

	@Column(name = "description", length = 2048)
	private String description;

	@Column(name = "mod_time")
	private Calendar modificationTime = Calendar.getInstance();

	/**
	 * format YYYYMM (year and month)
	 */
	@Column(name = "batch_id", length = 6)
	private String batchId;
	
	@Column(name = "status", length = 20)
	private String status;

	public URLRec getUrl() {
		return url;
	}

	public void setUrl(URLRec url) {
		this.url = url;
	}

	public Double getScore() {
		return score;
	}

	public void setScore(Double score) {
		this.score = score;
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

	public Calendar getModificationTime() {
		return modificationTime;
	}

	public void setModificationTime(Calendar modificationTime) {
		this.modificationTime = modificationTime;
	}

	public long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBatchId() {
		return batchId;
	}

	public void setBatchId(String batchId) {
		this.batchId = batchId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ResourceCandidate [id=");
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
		if (title != null) {
			builder.append("title=");
			builder.append(title);
			builder.append(", ");
		}
		if (description != null) {
			builder.append("description=");
			builder.append(description);
			builder.append(", ");
		}
		if (modificationTime != null) {
			builder.append("modificationTime=");
			builder.append(modificationTime);
			builder.append(", ");
		}
		if (batchId != null) {
			builder.append("batchId=");
			builder.append(batchId);
			builder.append(", ");
		}
		if (status != null) {
			builder.append("status=");
			builder.append(status);
		}
		builder.append("]");
		return builder.toString();
	}

	
}
