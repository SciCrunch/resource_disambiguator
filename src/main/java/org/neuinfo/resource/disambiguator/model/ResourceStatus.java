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
@Table(name = "rd_resource_status")
public class ResourceStatus {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@ManyToOne
	@JoinColumn(name = "registry_id")
	private Registry registry;
	
	@ManyToOne
	@JoinColumn(name = "doc_id")
	private Paper paper;
	
	@Column(name = "is_valid")
	private boolean valid = false;

	@Column(name = "last_checked_time")
	private Calendar lastCheckedTime = Calendar.getInstance();
	
	public Calendar getLastCheckedTime() {
	    return lastCheckedTime;
	}
	
	public void setLastCheckedTime(Calendar lastCheckedTime) {
	    this.lastCheckedTime = lastCheckedTime;
	}
	
	public boolean isValid() {
	    return valid;
	}
	
	public void setValid(boolean valid) {
	    this.valid = valid;
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
	
	public long getId() {
	    return id;
	}
}
