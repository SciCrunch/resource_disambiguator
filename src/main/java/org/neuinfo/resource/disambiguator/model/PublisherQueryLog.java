package org.neuinfo.resource.disambiguator.model;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

@Entity
@Table(name = "rd_publisher_query_log")
public class PublisherQueryLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(name = "query_str", length = 1000)
	private String queryString;
	
	@ManyToOne
	@JoinColumn(name = "publisher_id")
	private Publisher publisher;
	
	@ManyToOne
	@JoinColumn(name = "registry_id")
	private Registry registry;
	
	@Column(name = "exec_time")
	private Calendar execTime;

	public String getQueryString() {
		return queryString;
	}

	public void setQueryString(String queryString) {
		this.queryString = queryString;
	}

	public Publisher getPublisher() {
		return publisher;
	}

	public void setPublisher(Publisher publisher) {
		this.publisher = publisher;
	}

	public Registry getRegistry() {
		return registry;
	}

	public void setRegistry(Registry registry) {
		this.registry = registry;
	}

	public Calendar getExecTime() {
		return execTime;
	}

	public void setExecTime(Calendar execTime) {
		this.execTime = execTime;
	}

	public long getId() {
		return id;
	}

}
