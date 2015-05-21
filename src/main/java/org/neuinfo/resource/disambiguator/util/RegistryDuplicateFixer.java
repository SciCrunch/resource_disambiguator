package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.neuinfo.resource.disambiguator.model.*;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.BufferedReader;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 12/19/14.
 */
public class RegistryDuplicateFixer {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    private boolean fixPaperRefRegistryRefs = false;
    private boolean mergeRegistryRecs = false;

    public boolean isFixPaperRefRegistryRefs() {
        return fixPaperRefRegistryRefs;
    }

    public void setFixPaperRefRegistryRefs(boolean fixPaperRefRegistryRefs) {
        this.fixPaperRefRegistryRefs = fixPaperRefRegistryRefs;
    }

    public boolean isMergeRegistryRecs() {
        return mergeRegistryRecs;
    }

    public void setMergeRegistryRecs(boolean mergeRegistryRecs) {
        this.mergeRegistryRecs = mergeRegistryRecs;
    }

    public void handle(String[] nifIds, long[] refRegIds) throws Exception {
        for (int i = 0; i < nifIds.length; i++) {
            String nifId = nifIds[i];
            long refRegId = refRegIds[i];
            List<Registry> duplicateRecords = getDuplicateRecords(nifId);

            Registry refReg = null;
            for (Registry reg : duplicateRecords) {
                if (reg.getId() == refRegId) {
                    refReg = reg;
                    break;
                }
            }
            if (refReg == null) {
                System.out.println("No refReg for nifId:" + nifIds[i]);
                return;
            }
            if (fixPaperRefRegistryRefs) {
                for (Registry reg : duplicateRecords) {
                    if (reg != refReg) {
                        fixPaperRefRegistryRefs(reg, refReg);
                    }
                }
                System.out.println("fixPaperRefRegistryRefs completed.");
            }
            if (mergeRegistryRecs) {
                boolean merged = false;
                for (Registry reg : duplicateRecords) {
                    if (reg == refReg) {
                        continue;
                    }
                    merged |= merge(refReg, reg);
                }
                if (merged) {
                    updateRegistry(refReg);
                }
                for (Registry reg : duplicateRecords) {
                    if (reg != refReg) {
                        deleteRegistryRec(reg);
                    }
                }

                System.out.println("finished registry merge for " + refReg.getResourceName() + " " + refReg.getId());
            }
            System.out.println("Ref " + refReg.getNifId() + " [" + refReg.getId() + "] " + refReg.getResourceName());
            for (Registry reg : duplicateRecords) {
                int mentionCount = countMentions(reg);
                if (mentionCount > 0) {
                    System.out.println(reg.getNifId() + " [" + reg.getId() + "] " +
                            reg.getResourceName() + " has " + mentionCount + " mentions");
                }
            }

        }

    }

    public int countMentions(Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<URLRec> query = em.createQuery("from URLRec u where u.registry.id = :id", URLRec.class);
            List<URLRec> list = query.setParameter("id", reg.getId()).getResultList();
            int mentionCount = list.size();
            if (mentionCount == 0) {
                TypedQuery<ResourceRec> q2 = em.createQuery("from ResourceRec r where r.registry.id = :id",
                        ResourceRec.class);
                List<ResourceRec> list1 = q2.setParameter("id", reg.getId()).getResultList();
                mentionCount = list1.size();
                if (mentionCount > 0) {
                    System.out.println(reg.getId() + " URLRec count:" + mentionCount);
                }
            }
            if (mentionCount == 0) {
                TypedQuery<PaperReference> q3 = em.createQuery("from PaperReference p where p.registry.id = :id",
                        PaperReference.class);
                List<PaperReference> list2 = q3.setParameter("id", reg.getId()).getResultList();
                mentionCount = list2.size();
                if (mentionCount > 0) {
                    System.out.println(reg.getId() + " PaperReference count:" + mentionCount);
                }
            }

            return mentionCount;
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    List<Registry> getDuplicateRecords(String nifId) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<Registry> query = em.createQuery("from Registry r where r.nifId = :nifId order by r.id", Registry.class);
            return query.setParameter("nifId", nifId).getResultList();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void fixPaperRefRegistryRefs(Registry badReg, Registry goodReg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<URLRec> uq = em.createQuery("from URLRec u where u.registry.id = :id", URLRec.class);
            List<URLRec> urls = uq.setParameter("id", badReg.getId()).getResultList();
            for (URLRec ur : urls) {
                updateURLRecRegistry(ur, goodReg);
            }
            TypedQuery<PaperReference> q3 = em.createQuery("from PaperReference p where p.registry.id = :id",
                    PaperReference.class);
            List<PaperReference> list = q3.setParameter("id", badReg.getId()).getResultList();
            for (PaperReference pr : list) {
                updatePaperRefRegistry(pr, goodReg);
            }
            TypedQuery<PublisherQueryLog> q = em.createQuery("from PublisherQueryLog p where p.registry.id = :id",
                    PublisherQueryLog.class);
            List<PublisherQueryLog> publisherQueryLogs = q.setParameter("id", badReg.getId()).getResultList();
            for (PublisherQueryLog pql : publisherQueryLogs) {
                updatePublisherQueryLog(pql, goodReg);
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updatePublisherQueryLog(PublisherQueryLog pql, Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            pql.setRegistry(reg);
            em.merge(pql);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateURLRecRegistry(URLRec ur, Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            ur.setRegistry(reg);
            em.merge(ur);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updatePaperRefRegistry(PaperReference pr, Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            pr.setRegistry(reg);
            em.merge(pr);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateRegistry(Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            em.merge(reg);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void deleteRegistryRec(Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry registry = em.find(Registry.class, reg.getId());
            TypedQuery<ValidationStatus> query = em.createQuery("from ValidationStatus v where v.registry.id = :id",
                    ValidationStatus.class);
            List<ValidationStatus> validationStatuses = query.setParameter("id", reg.getId()).getResultList();
            for (ValidationStatus vs : validationStatuses) {
                em.remove(vs);
            }
            TypedQuery<PublisherQueryLog> q2 = em.createQuery("from PublisherQueryLog p where p.registry.id = :id",
                    PublisherQueryLog.class);
            List<PublisherQueryLog> publisherQueryLogs = q2.setParameter("id", reg.getId()).getResultList();
            for (PublisherQueryLog pql : publisherQueryLogs) {
                em.remove(pql);
            }
            em.remove(registry);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public boolean merge(Registry regRef, Registry other) {
        boolean changed = false;
        if (!Utils.isSame(regRef.getResourceName(), other.getResourceName())) {
            regRef.setResourceName(other.getResourceName());
            changed = true;
        }
        if (!Utils.isSame(regRef.getUuid(), other.getUuid())) {
            regRef.setUuid(other.getUuid());
            changed = true;
        }
        if (!Utils.isSame(regRef.getAbbrev(), other.getAbbrev())) {
            regRef.setAbbrev(other.getAbbrev());
            changed = true;
        }
        if (!Utils.isSame(regRef.getAvailability(), other.getAvailability())) {
            regRef.setAvailability(other.getAvailability());
            changed = true;
        }
        if (!Utils.isSame(regRef.getDescription(), other.getDescription())) {
            regRef.setDescription(other.getDescription());
            changed = true;
        }
        if (!Utils.isSame(regRef.getUrl(), other.getUrl())) {
            regRef.setUrl(other.getUrl());
            changed = true;
        }
        if (!Utils.isSame(regRef.getParentOrganization(), other.getParentOrganization())) {
            regRef.setParentOrganization(other.getParentOrganization());
            changed = true;
        }
        if (!Utils.isSame(regRef.getParentOrganizationId(), other.getParentOrganizationId())) {
            regRef.setParentOrganizationId(other.getParentOrganizationId());
            changed = true;
        }
        if (!Utils.isSame(regRef.getSupportingAgency(), other.getSupportingAgency())) {
            regRef.setSupportingAgency(other.getSupportingAgency());
            changed = true;
        }
        if (!Utils.isSame(regRef.getSupportingAgencyId(), other.getSupportingAgencyId())) {
            regRef.setSupportingAgencyId(other.getSupportingAgencyId());
            changed = true;
        }
        if (!Utils.isSame(regRef.getResourceType(), other.getResourceType())) {
            regRef.setResourceType(other.getResourceType());
            changed = true;
        }
        if (!Utils.isSame(regRef.getResourceTypeIds(), other.getResourceTypeIds())) {
            regRef.setResourceTypeIds(other.getResourceTypeIds());
            changed = true;
        }
        if (!Utils.isSame(regRef.getKeyword(), other.getKeyword())) {
            regRef.setKeyword(other.getKeyword());
            changed = true;
        }
        if (!Utils.isSame(regRef.getNifPmidLink(), other.getNifPmidLink())) {
            regRef.setNifPmidLink(other.getNifPmidLink());
            changed = true;
        }
        if (!Utils.isSame(regRef.getPublicationLink(), other.getPublicationLink())) {
            regRef.setPublicationLink(other.getPublicationLink());
            changed = true;
        }
        if (!Utils.isSame(regRef.getGrants(), other.getGrants())) {
            regRef.setGrants(other.getGrants());
            changed = true;
        }
        if (!Utils.isSame(regRef.getSynonym(), other.getSynonym())) {
            regRef.setSynonym(other.getSynonym());
            changed = true;
        }
        if (!Utils.isSame(regRef.getLogo(), other.getLogo())) {
            regRef.setLogo(other.getLogo());
            changed = true;
        }
        if (!Utils.isSame(regRef.getComment(), other.getComment())) {
            regRef.setComment(other.getComment());
            changed = true;
        }
        if (!Utils.isSame(regRef.getLicenseUrl(), other.getLicenseUrl())) {
            regRef.setLicenseUrl(other.getLicenseUrl());
            changed = true;
        }
        if (!Utils.isSame(regRef.getLicenseText(), other.getLicenseText())) {
            regRef.setLicenseText(other.getLicenseText());
            changed = true;
        }
        if (!Utils.isSame(regRef.getCurationStatus(), other.getCurationStatus())) {
            regRef.setCurationStatus(other.getCurationStatus());
            changed = true;
        }
        if (!Utils.isSame(regRef.getRelatedTo(), other.getRelatedTo())) {
            regRef.setRelatedTo(other.getRelatedTo());
            changed = true;
        }
        if (!Utils.isSame(regRef.getOldUrl(), other.getOldUrl())) {
            regRef.setOldUrl(other.getOldUrl());
            changed = true;
        }
        if (!Utils.isSame(regRef.getAlternativeUrl(), other.getAlternativeUrl())) {
            regRef.setAlternativeUrl(other.getAlternativeUrl());
            changed = true;
        }
        if (!Utils.isSame(regRef.getSuperCategory(), other.getSuperCategory())) {
            regRef.setSuperCategory(other.getSuperCategory());
            changed = true;
        }
        return changed;
    }

    public static Map<String, Long> getDuplicates() throws Exception {
        Map<String, Long> map = new HashMap<String, Long>();
        //File f = new File("/home/bozyurt/dev/java/resource_disambiguator/duplicates_all.txt");
        File f = new File("/home/bozyurt/dev/java/resource_disambiguator/duplicates4.txt");
        BufferedReader in = null;
        try {
            in = Utils.newUTF8CharSetReader(f.getAbsolutePath());
            String line;
            Pattern pattern = Pattern.compile(".+nifId:([\\w-]+).+::\\s+(\\d+)");
            while ((line = in.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String nifId = matcher.group(1);
                    String regIdStr = matcher.group(2);
                    Long regId = Long.parseLong(regIdStr);
                    if (!map.containsKey(nifId)) {
                        map.put(nifId, regId);
                    } else {
                        //  System.out.println("duplicate nifId:" + nifId);
                    }
                }

            }
        } finally {
            Utils.close(in);
        }
        return map;
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            RegistryDuplicateFixer fixer = new RegistryDuplicateFixer();
            injector.injectMembers(fixer);
            fixer.setFixPaperRefRegistryRefs(true);
            fixer.setMergeRegistryRecs(true);
            //  Duplicate nifId:nlx_152293 Beckman Coulter :: 15875 // 14727
            // Duplicate nifId:nlx_152335 Covance :: 15908
            // Duplicate nifId:nlx_152412 Miltenyi Biotec :: 15822
            //
            //
            //fixer.handle(new String[]{"nlx_152409", "nlx_152451"}, new long[]{15824, 15573});
            // fixer.handle(new String[]{"nlx_157815"}, new long[]{14937});
            Map<String, Long> duplicateMap = getDuplicates();

            System.out.println("duplicateMap.size:" + duplicateMap.size());
            int count = 1;
            for (String nifId : duplicateMap.keySet()) {
                Long goodRegId = duplicateMap.get(nifId);
                System.out.println("Fixing " + nifId + " (" + count + " of " + duplicateMap.size() + ")");
                fixer.handle(new String[]{nifId}, new long[]{goodRegId});
                count++;
            }

        } finally {
            JPAInitializer.stopService();
        }
    }
}
