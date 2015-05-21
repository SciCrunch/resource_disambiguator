package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;
import javax.persistence.Id;
import java.util.Calendar;

/**
 * Created by bozyurt on 2/14/14.
 */
@Entity
@Table(name = "rd_url_status")
public class UrlStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "url_id")
	private Long urlID;

    @Column(name="alive")
    private boolean alive;

    @Column(name = "last_mod_time")
    private Calendar lastModifiedTime = Calendar.getInstance();

    private int flags = 0;

    private int type = 0;

    public static final int CHECKED = 1;
    public static final int INFERRED = 2; /* for links that vary in query params */
    public static final int BAD_URL = 4;

    public static final int TYPE_JOURNAL = 1;
    public static final int TYPE_PUBLISHER = 2;
    public static final int TYPE_INSTITUTION = 4;
    public static final int TYPE_SUPPL = 8;


    public long getId() {
        return id;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public Calendar getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(Calendar lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public int getFlags() {
        return flags;
    }

    public Long getUrlID() {
        return urlID;
    }

    public void setUrlID(Long urlID) {
        this.urlID = urlID;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
