package org.neuinfo.resource.disambiguator.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "rd_publisher")
public class Publisher {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "publisher_name")
	private String publisherName;

	@Column(name = "api_key")
	private String apiKey;

	@Column(name = "connections_allowed")
	private int numConnectionsAllowed;

	public Publisher() {
	}

	public long getId() {
		return id;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public int getNumConnectionsAllowed() {
		return numConnectionsAllowed;
	}

	public void setNumConnectionsAllowed(int numConnectionsAllowed) {
		this.numConnectionsAllowed = numConnectionsAllowed;
	}

	public String getPublisherName() {
		return publisherName;
	}

	public void setPublisherName(String publisherName) {
		this.publisherName = publisherName;
	}


}
