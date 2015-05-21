package org.neuinfo.resource.disambiguator.model;

import java.util.Calendar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "rd_url_annot_info")
public class URLAnnotationInfo {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(length = 5)
	private String label;

	@Column(name = "resource_type")
	private String resourceType;

	@Column(length = 1024)
	private String notes;

	@Column(name = "mod_time")
	private Calendar modificationTime = Calendar.getInstance();
	
	@ManyToOne
	@JoinColumn(name = "url_id")
	private URLRec url;
	
	@Column(name="op_type",length=30)
	private String opType = "candidate_filter";
	
	@Column(name="modified_by", length=40)
	private String modifiedBy;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public URLRec getUrl() {
		return url;
	}

	public void setUrl(URLRec url) {
		this.url = url;
	}

	public long getId() {
		return id;
	}

	public String getResourceType() {
		return resourceType;
	}

	public void setResourceType(String resourceType) {
		this.resourceType = resourceType;
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

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("URLAnnotationInfo [id=");
		builder.append(id);
		builder.append(", ");
		if (label != null) {
			builder.append("label=");
			builder.append(label);
			builder.append(", ");
		}
		if (url != null) {
			builder.append("url=");
			builder.append(url);
			builder.append(", ");			
		}
		if (opType != null) {
			builder.append("opType=");
			builder.append(opType);
			builder.append(", ");
		}
		if (resourceType != null) {
			builder.append("resourceType=");
			builder.append(resourceType);
			builder.append(", ");
		}
		if (notes != null) {
			builder.append("notes=");
			builder.append(notes);
			builder.append(", ");
		}
		if (modifiedBy != null) {
			builder.append("modifiedBy=");
			builder.append(modifiedBy);
			builder.append(",  ");
		}
		if (modificationTime != null) {
			builder.append("modificationTime=");
			builder.append(modificationTime);
		}

		builder.append("]");
		return builder.toString();
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
}
