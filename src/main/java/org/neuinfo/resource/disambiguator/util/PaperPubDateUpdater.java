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
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts publication date from the filePath and completes missing pubdate fields.
 * <p/>
 * Created by bozyurt on 7/29/14.
 */
public class PaperPubDateUpdater {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Map<String, String> monthMap = new HashMap<String, String>();

    static {
        monthMap.put("Jan", "01");
        monthMap.put("Feb", "02");
        monthMap.put("Mar", "03");
        monthMap.put("Apr", "04");
        monthMap.put("May", "05");
        monthMap.put("Jun", "06");
        monthMap.put("Jul", "07");
        monthMap.put("Aug", "08");
        monthMap.put("Sep", "09");
        monthMap.put("Oct", "10");
        monthMap.put("Nov", "11");
        monthMap.put("Dec", "12");
    }

    public void fixRegistry() {
        EntityManager em = null;
        List<?> resultList = null;
        try {
            em = Utils.getEntityManager(emFactory);
            final Query query = em.createNativeQuery("select nif_id, count(nif_id) from registry group by nif_id having count(nif_id) > 1");
            resultList = query.getResultList();

        } finally {
            Utils.closeEntityManager(em);
        }
        int count = 0;
        for (Object o : resultList) {
            Object[] row = (Object[]) o;
            String nifId = row[0].toString();
            fixRegistryResource(nifId);
            count++;
            System.out.println("handled " + count + " of " + resultList.size());
        }
    }

    void fixRegistryResource(String nifId) {
        EntityManager em = null;

        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            TypedQuery<Registry> query = em.createQuery("from Registry r where r.nifId = :nifId order by r.id desc", Registry.class);
            query.setParameter("nifId", nifId);
            List<Registry> resultList = query.getResultList();
            List<Long> badRegistryIds = new ArrayList<Long>(resultList.size() - 1);
            boolean first = true;
            Long goodRegistryId = null;
            for (Registry r : resultList) {
                if (first) {
                    goodRegistryId = r.getId();
                    first = false;
                } else {
                    badRegistryIds.add(r.getId());
                    // remove the old registry entries
                    em.remove(r);
                }
            }
            // updateURLRecRecords(goodRegistryId, badRegistryIds);
            // updateResourceRecRecords(goodRegistryId, badRegistryIds);
            // updatePublisherRecRecords(goodRegistryId, badRegistryIds);
            // updateCombinedResourceRefRecords(goodRegistryId, badRegistryIds);
            // updateRegistrySiteContentRecords(goodRegistryId, badRegistryIds);

            // updateRegistryUpdateStatusRecords(goodRegistryId, badRegistryIds);
            // updateValidationStatusRecords(goodRegistryId, badRegistryIds);
            // updatePublisherQueryLogRecords(goodRegistryId, badRegistryIds);
            // updateRegistryRedirectAnnotInfoRecords(goodRegistryId, badRegistryIds);

         //   updateURLAnnotationInfoRecords(goodRegistryId, badRegistryIds);
         //   System.out.println("---------------------------");
         //   updateNERAnnotationInfoRecords(goodRegistryId, badRegistryIds);

            Utils.commitTransaction(em);
            if (!badRegistryIds.isEmpty()) {
                System.out.println("removed " + badRegistryIds.size() + " records for " + resultList.get(0).getNifId());
            }
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateURLRecRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<URLRec> query = em.createQuery("from URLRec u where u.registry.id in (:ids)", URLRec.class)
                    .setParameter("ids", badRegistryIds);
            List<URLRec> resultList = query.getResultList();
            for (URLRec ur : resultList) {
                ur.setRegistry(theReg);
                em.merge(ur);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " registry items for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateRegistrySiteContentRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<RegistrySiteContent> query = em.createQuery("from RegistrySiteContent u where u.registry.id in (:ids)",
                    RegistrySiteContent.class).setParameter("ids", badRegistryIds);
            List<RegistrySiteContent> resultList = query.getResultList();
            for (RegistrySiteContent rsc : resultList) {
                rsc.setRegistry(theReg);
                em.merge(rsc);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " RegistrySiteContent items for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateResourceRecRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<ResourceRec> query = em.createQuery("from ResourceRec u where u.registry.id in (:ids)", ResourceRec.class)
                    .setParameter("ids", badRegistryIds);
            List<ResourceRec> resultList = query.getResultList();
            for (ResourceRec rr : resultList) {
                rr.setRegistry(theReg);
                em.merge(rr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " NER record registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }

    }

    void updatePublisherRecRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<PaperReference> query = em.createQuery("from PaperReference u where u.registry.id in (:ids)",
                    PaperReference.class).setParameter("ids", badRegistryIds);
            List<PaperReference> resultList = query.getResultList();
            for (PaperReference rr : resultList) {
                rr.setRegistry(theReg);
                em.merge(rr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " publisher search record registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    void updateCombinedResourceRefRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            List<Integer> ids = new ArrayList<Integer>(badRegistryIds.size());
            for (Long id : badRegistryIds) {
                ids.add(id.intValue());
            }
            TypedQuery<CombinedResourceRef> query = em.createQuery("from CombinedResourceRef u where u.registryId in (:ids)",
                    CombinedResourceRef.class).setParameter("ids", ids);
            List<CombinedResourceRef> resultList = query.getResultList();
            for (CombinedResourceRef crr : resultList) {
                crr.setRegistryId(goodRegistryId.intValue());
                em.merge(crr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " combined resource ref record registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateRegistryUpdateStatusRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<RegistryUpdateStatus> query = em.createQuery("from RegistryUpdateStatus u where u.registry.id in (:ids)",
                    RegistryUpdateStatus.class).setParameter("ids", badRegistryIds);
            List<RegistryUpdateStatus> resultList = query.getResultList();
            for (RegistryUpdateStatus rr : resultList) {
                rr.setRegistry(theReg);
                em.merge(rr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " RegistryUpdateStatus registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateValidationStatusRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<ValidationStatus> query = em.createQuery("from ValidationStatus u where u.registry.id in (:ids)",
                    ValidationStatus.class).setParameter("ids", badRegistryIds);
            List<ValidationStatus> resultList = query.getResultList();
            for (ValidationStatus rr : resultList) {
                rr.setRegistry(theReg);
                em.merge(rr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " ValidationStatus registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updatePublisherQueryLogRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<PublisherQueryLog> query = em.createQuery("from PublisherQueryLog u where u.registry.id in (:ids)",
                    PublisherQueryLog.class).setParameter("ids", badRegistryIds);
            List<PublisherQueryLog> resultList = query.getResultList();
            for (PublisherQueryLog rr : resultList) {
                rr.setRegistry(theReg);
                em.merge(rr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " PublisherQueryLog registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    void updateRegistryRedirectAnnotInfoRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<RegistryRedirectAnnotInfo> query = em.createQuery("from RegistryRedirectAnnotInfo u where u.registry.id in (:ids)",
                    RegistryRedirectAnnotInfo.class).setParameter("ids", badRegistryIds);
            List<RegistryRedirectAnnotInfo> resultList = query.getResultList();
            for (RegistryRedirectAnnotInfo rr : resultList) {
                rr.setRegistry(theReg);
                em.merge(rr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + " PublisherQueryLog registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    void updateURLAnnotationInfoRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<URLAnnotationInfo> query = em.createQuery("from URLAnnotationInfo u where u.registry.id in (:ids)",
                    URLAnnotationInfo.class).setParameter("ids", badRegistryIds);
            List<URLAnnotationInfo> resultList = query.getResultList();
            for (URLAnnotationInfo rr : resultList) {
                rr.setRegistry(theReg);
                em.merge(rr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + "  registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateNERAnnotationInfoRecords(Long goodRegistryId, List<Long> badRegistryIds) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry theReg = em.find(Registry.class, goodRegistryId);
            Assertion.assertNotNull(theReg);
            TypedQuery<NERAnnotationInfo> query = em.createQuery("from NERAnnotationInfo u where u.registry.id in (:ids)",
                    NERAnnotationInfo.class).setParameter("ids", badRegistryIds);
            List<NERAnnotationInfo> resultList = query.getResultList();
            for (NERAnnotationInfo rr : resultList) {
                rr.setRegistry(theReg);
                em.merge(rr);
            }
            if (!resultList.isEmpty()) {
                System.out.println("updated " + resultList.size() + "  registry ids for "
                        + theReg.getResourceName() + " " + theReg.getNifId());
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void handle() throws Exception {
        EntityManager em = null;
        List<?> resultList = null;
        try {
            em = Utils.getEntityManager(emFactory);
            final Query query = em.createQuery("select p.id, p.filePath from Paper p where p.publicationDate is null");

            resultList = query.getResultList();
        } finally {
            Utils.closeEntityManager(em);
        }
        Pattern p = Pattern.compile("([12]\\d\\d\\d)_([A-Z][a-z][a-z])_(\\d{1,2})[\\(_]");
        int count = 0;
        for (Object o : resultList) {
            Object[] row = (Object[]) o;
            Long id = (Long) row[0];
            String filePath = row[1].toString();
            final Matcher matcher = p.matcher(filePath);
            if (matcher.find()) {
                String year = matcher.group(1);
                String month = matcher.group(2);
                String day = matcher.group(3);
                String pubDate = String.format("%s-%s-%02d", year, monthMap.get(month), Integer.parseInt(day));
                System.out.println(filePath + " => " + pubDate);
                updatePaper(id, pubDate);
            }

            count++;
        }
    }

    private void updatePaper(Long paperId, String pubDate) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Paper paper = em.find(Paper.class, paperId);
            paper.setPublicationDate(pubDate);
            em.merge(paper);
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            Utils.rollbackTransaction(em);
            t.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            PaperPubDateUpdater dateUpdater = new PaperPubDateUpdater();
            injector.injectMembers(dateUpdater);
            dateUpdater.handle();

            //dateUpdater.fixRegistry();

        } finally {
            JPAInitializer.stopService();
        }
    }

}
