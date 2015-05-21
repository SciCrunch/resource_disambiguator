package org.neuinfo.resource.disambiguator.model;

import javax.persistence.*;
import java.util.Calendar;

/**
 * Created by bozyurt on 4/15/14.
 */

@Entity
@Table(name = "rd_redirect_history")
public class RedirectHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @ManyToOne
    @JoinColumn(name = "registry_id")
    private Registry registry;

    @Column(name = "mod_time")
    private Calendar modificationTime = Calendar.getInstance();

    @Column(name = "modified_by", length = 40)
    private String modifiedBy;

    public long getId() {
        return id;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public Calendar getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(Calendar modificationTime) {
        this.modificationTime = modificationTime;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }
}
