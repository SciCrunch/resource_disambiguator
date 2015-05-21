package org.neuinfo.resource.disambiguator.model;

import org.hibernate.annotations.Type;

import javax.persistence.*;

@Entity
@Table(name = "registry")
public class Registry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Type(type = "text")
    private String uuid;

    @Column(name = "nif_id")
    @Type(type = "text")
    private String nifId;

    @Column(name = "resource_name")
    @Type(type = "text")
    private String resourceName;

    @Type(type = "text")
    private String abbrev;

    @Type(type = "text")
    private String availability;

    @Type(type = "text")
    private String description;

    @Type(type = "text")
    private String url;

    @Column(name = "parent_organization")
    @Type(type = "text")
    private String parentOrganization;

    @Column(name = "parent_organization_id")
    @Type(type = "text")
    private String parentOrganizationId;

    @Column(name = "supporting_agency")
    @Type(type = "text")
    private String supportingAgency;

    @Column(name = "supporting_agency_id")
    @Type(type = "text")
    private String supportingAgencyId;

    @Column(name = "resource_type")
    @Type(type = "text")
    private String resourceType;

    @Column(name = "resource_type_ids")
    @Type(type = "text")
    private String resourceTypeIds;

    @Type(type = "text")
    private String keyword;

    @Column(name = "nif_pmid_link")
    @Type(type = "text")
    private String nifPmidLink;


    @Column(name = "publicationlink")
    @Type(type = "text")
    private String publicationLink;

    @Column(name = "resource_updated")
    private Integer resourceUpdated;

    @Type(type = "text")
    private String grants;

    @Type(type = "text")
    private String synonym;

    @Type(type = "text")
    private String logo;

    @Type(type = "text")
    private String comment;

    @Column(name = "license_url")
    @Type(type = "text")
    private String licenseUrl;

    @Column(name = "license_text")
    @Type(type = "text")
    private String licenseText;

    @Column(name = "date_created")
    private Integer dateCreated;

    @Column(name = "date_updated")
    private Integer dateUpdated;

    @Column(name = "curation_status")
    @Type(type = "text")
    private String curationStatus;

    @Column(name = "index_time")
    private Integer indexTime;

    @Column(name = "relatedto")
    @Type(type = "text")
    private String relatedTo;

    @Column(name = "old_url", length = 500)
    private String oldUrl;

    @Column(name = "alt_url", length = 500)
    private String alternativeUrl;

    @Column(name = "supercategory", length = 200)
    private String superCategory;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getNifId() {
        return nifId;
    }

    public void setNifId(String nifId) {
        this.nifId = nifId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getAbbrev() {
        return abbrev;
    }

    public void setAbbrev(String abbrev) {
        this.abbrev = abbrev;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getParentOrganization() {
        return parentOrganization;
    }

    public void setParentOrganization(String parentOrganization) {
        this.parentOrganization = parentOrganization;
    }

    public String getParentOrganizationId() {
        return parentOrganizationId;
    }

    public void setParentOrganizationId(String parentOrganizationId) {
        this.parentOrganizationId = parentOrganizationId;
    }

    public String getSupportingAgency() {
        return supportingAgency;
    }

    public void setSupportingAgency(String supportingAgency) {
        this.supportingAgency = supportingAgency;
    }

    public String getSupportingAgencyId() {
        return supportingAgencyId;
    }

    public void setSupportingAgencyId(String supportingAgencyId) {
        this.supportingAgencyId = supportingAgencyId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceTypeIds() {
        return resourceTypeIds;
    }

    public void setResourceTypeIds(String resourceTypeIds) {
        this.resourceTypeIds = resourceTypeIds;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getNifPmidLink() {
        return nifPmidLink;
    }

    public void setNifPmidLink(String nifPmidLink) {
        this.nifPmidLink = nifPmidLink;
    }

    public Integer getResourceUpdated() {
        return resourceUpdated;
    }

    public void setResourceUpdated(Integer resourceUpdated) {
        this.resourceUpdated = resourceUpdated;
    }

    public String getGrants() {
        return grants;
    }

    public void setGrants(String grants) {
        this.grants = grants;
    }

    public String getPublicationLink() {
        return publicationLink;
    }

    public void setPublicationLink(String publicationLink) {
        this.publicationLink = publicationLink;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getLicenseUrl() {
        return licenseUrl;
    }

    public void setLicenseUrl(String licenseUrl) {
        this.licenseUrl = licenseUrl;
    }

    public String getLicenseText() {
        return licenseText;
    }

    public void setLicenseText(String licenseText) {
        this.licenseText = licenseText;
    }

    public Integer getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Integer dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Integer getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Integer dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public String getCurationStatus() {
        return curationStatus;
    }

    public void setCurationStatus(String curationStatus) {
        this.curationStatus = curationStatus;
    }

    public Integer getIndexTime() {
        return indexTime;
    }

    public void setIndexTime(Integer indexTime) {
        this.indexTime = indexTime;
    }

    public String getSynonym() {
        return synonym;
    }

    public void setSynonym(String synonym) {
        this.synonym = synonym;
    }

    public String getRelatedTo() {
        return relatedTo;
    }

    public void setRelatedTo(String relatedTo) {
        this.relatedTo = relatedTo;
    }

    public String getOldUrl() {
        return oldUrl;
    }

    public void setOldUrl(String oldUrl) {
        this.oldUrl = oldUrl;
    }

    public String getAlternativeUrl() {
        return alternativeUrl;
    }

    public void setAlternativeUrl(String alternativeUrl) {
        this.alternativeUrl = alternativeUrl;
    }

    public String getSuperCategory() {
        return superCategory;
    }

    public void setSuperCategory(String superCategory) {
        this.superCategory = superCategory;
    }
}
