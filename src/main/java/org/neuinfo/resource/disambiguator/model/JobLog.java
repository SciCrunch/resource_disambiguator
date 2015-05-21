package org.neuinfo.resource.disambiguator.model;

import java.util.Calendar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "rd_job_log")
public class JobLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(length = 40, nullable = false)
	private String operation;

	@Column(length = 30, nullable = false)
	private String status;
	/**
	 * format YYYYMM (year and month)
	 */
	@Column(name = "batch_id", length = 6, nullable = false)
	private String batchId;

	@Column(name = "mod_time")
	private Calendar modTime = Calendar.getInstance();

	@Column(name = "modified_by", length = 40)
	private String modifiedBy;

	public String getOperation() {
		return operation;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getBatchId() {
		return batchId;
	}

	public void setBatchId(String batchId) {
		this.batchId = batchId;
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

	public long getId() {
		return id;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("JobLog [id=");
		builder.append(id);
		builder.append(", ");
		if (operation != null) {
			builder.append("operation=");
			builder.append(operation);
			builder.append(", ");
		}
		if (status != null) {
			builder.append("status=");
			builder.append(status);
			builder.append(", ");
		}
		if (batchId != null) {
			builder.append("batchId=");
			builder.append(batchId);
			builder.append(", ");
		}
		if (modTime != null) {
			builder.append("modTime=");
			builder.append(modTime);
			builder.append(", ");
		}
		if (modifiedBy != null) {
			builder.append("modifiedBy=");
			builder.append(modifiedBy);
		}
		builder.append("]");
		return builder.toString();
	}

}
