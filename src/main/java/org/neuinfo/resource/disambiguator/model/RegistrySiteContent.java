package org.neuinfo.resource.disambiguator.model;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Calendar;

/**
 * Created by bozyurt on 1/2/14.
 */
@Entity
@Table(name = "rd_registry_site_content")
public class RegistrySiteContent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Type(type = "text")
    private String content;

    @Column(length = 1024)
    private String title;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;

    @Column(name = "last_mod_time")
    private Calendar lastModifiedTime = Calendar.getInstance();

    @Column(name = "redirect_url", length = 500)
    private String redirectUrl;

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    private int flags = ORIGINAL;

    public final static int ORIGINAL = 1;
    public final static int LATEST = 2;



    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public Calendar getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(Calendar lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RegistrySiteContent{");
        sb.append("id=").append(id);
        sb.append(", content='").append(content).append('\'');
        sb.append(", title='").append(title).append('\'');
        sb.append(", registry=").append(registry);
        sb.append(", lastModifiedTime=").append(lastModifiedTime);
        sb.append(", redirectUrl='").append(redirectUrl).append('\'');
        sb.append(", flags=").append(flags);
        sb.append('}');
        return sb.toString();
    }
}
