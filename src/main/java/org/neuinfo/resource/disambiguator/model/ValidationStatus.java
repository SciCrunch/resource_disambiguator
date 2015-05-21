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
@Table(name = "rd_validation_status")
public class ValidationStatus {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@ManyToOne
	@JoinColumn(name = "registry_id")
	private Registry registry;
	
	@Column(name = "is_up")
	private boolean up = false;

	@Column(name = "last_checked_time")
	private Calendar lastCheckedTime = Calendar.getInstance();
	
	private String message;
	
	public Calendar getLastCheckedTime() {
	    return lastCheckedTime;
	}

	public void setLastCheckedTime(Calendar lastCheckedTime) {
	    this.lastCheckedTime = lastCheckedTime;
	}
	
	public boolean isUp() {
	    return up;
	}
	
	public void setUp(boolean up) {
	    this.up = up;
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

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
