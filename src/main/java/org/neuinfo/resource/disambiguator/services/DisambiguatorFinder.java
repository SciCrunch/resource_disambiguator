package org.neuinfo.resource.disambiguator.services;

import org.neuinfo.resource.disambiguator.model.*;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.*;

/**
 * DAO for Resource Disambiguator
 *
 * @author bozyurt
 */
public class DisambiguatorFinder {

    public static RegistrySiteContent getRegistrySiteContent(EntityManager em, Registry reg, int flags) {
        TypedQuery<RegistrySiteContent> query = em.createQuery(
                "from RegistrySiteContent r where r.registry.id = :id and flags = :flags",
                RegistrySiteContent.class);
        List<RegistrySiteContent> rscList = query.setParameter("id", reg.getId())
                .setParameter("flags", flags).getResultList();
        if (rscList == null || rscList.isEmpty()) {
            return null;
        }
        return rscList.get(0);
    }

    public static List<RegistrySiteContent> getEmptyContentRegistrySiteContent(EntityManager em) {
         TypedQuery<RegistrySiteContent> query = em.createQuery(
                "from RegistrySiteContent r where r.content is not null and length(r.content) = 0",
                RegistrySiteContent.class);
        return query.getResultList();
    }

    public static List<URLAnnotationInfo> getUrlAnnotationsWithRegistry(EntityManager em) {
        TypedQuery<URLAnnotationInfo> query = em.createQuery(
                "from URLAnnotationInfo u where registry.id is not null", URLAnnotationInfo.class);
        return query.getResultList();
    }

    public static List<Registry> getAllRegistryRecords(EntityManager em) {
        TypedQuery<Registry> query = em.createQuery("from Registry r where r.url is not null",
                Registry.class);

        return query.getResultList();
    }

    public static List<Registry> getAllActiveRegistryRecords(EntityManager em) {
        TypedQuery<Registry> query = em.createQuery("from Registry r where r.url is not null",
                Registry.class);

        List<Registry> resultList = query.getResultList();
        for (Iterator<Registry> it = resultList.iterator(); it.hasNext(); ) {
            Registry reg = it.next();
            if (reg.getAvailability() != null &&
                    reg.getAvailability().indexOf("THIS RESOURCE IS NO LONGER IN SERVICE") != -1) {
                it.remove();
            }
        }
        return resultList;
    }

    public static Registry getRegistryByName(EntityManager em, String resourceName) {
        TypedQuery<Registry> query = em.createQuery("from Registry r where r.resourceName = :rn",
                Registry.class);

        return query.setParameter("rn", resourceName).getSingleResult();
    }

    public static List<RegistrySiteContent> findMatchingSiteContent(EntityManager em, Long registryId) {
        TypedQuery<RegistrySiteContent> query = em.createQuery(
                "from RegistrySiteContent r where r.registry.id = :regId", RegistrySiteContent.class);
        query.setParameter("regId", registryId);
        return query.getResultList();
    }

    public static List<URLRec> getAllUrlRecordsForBatch(EntityManager em,
                                                        String batchId) {
        TypedQuery<URLRec> query = em.createQuery(
                "from URLRec where batchId = :batchId", URLRec.class)
                .setParameter("batchId", batchId);

        return query.getResultList();
    }

    public static List<Paper> getAllPapersWithoutTitle(EntityManager em) {
        TypedQuery<Paper> query = em.createQuery("from Paper where title is null", Paper.class);
        return query.getResultList();
    }
    public static List<Paper> getAllPapers(EntityManager em) {
        TypedQuery<Paper> query = em.createQuery("from Paper", Paper.class);
        return query.getResultList();
    }

    public static Publisher getPublisher(EntityManager em, String publisherName) {
        TypedQuery<Publisher> query = em.createQuery(
                "from Publisher where publisherName = :publisherName",
                Publisher.class).setParameter("publisherName", publisherName);

        return query.getSingleResult();
    }

    public static List<PublisherQueryLog> getQueryLogs(EntityManager em,
                                                       Long registryId, Long publisherId, Calendar afterDate) {
        TypedQuery<PublisherQueryLog> query = em
                .createQuery(
                        "from PublisherQueryLog q where q.registry.id = :id and  q.publisher.id = :pid "
                                + "and q.execTime >= :date",
                        PublisherQueryLog.class);
        query.setParameter("id", registryId).setParameter("date", afterDate)
                .setParameter("pid", publisherId);

        return query.getResultList();
    }

    public static PublisherQueryLog getLatestQueryLog(EntityManager em,
                                                       Long publisherId, Calendar afterDate) {
        TypedQuery<PublisherQueryLog> query = em
                .createQuery(
                        "from PublisherQueryLog q where q.publisher.id = :pid "
                                + "and q.execTime >= :date order by q.execTime desc",
                        PublisherQueryLog.class);
        query.setParameter("date", afterDate).setParameter("pid", publisherId).setMaxResults(1);
        List<PublisherQueryLog> resultList = query.getResultList();
        if (resultList.isEmpty()) {
            return null;
        }
        return resultList.get(0);
    }

    public static List<PaperReference> getMatchingPaperReferences(
            EntityManager em, List<String> pmidList, Long publisherId,
            Long registryId) {
        if (pmidList.isEmpty()) {
            return new ArrayList<PaperReference>(0);
        }
        TypedQuery<PaperReference> query = em.createQuery(
                "from PaperReference p where p.registry.id = :id and  "
                        + "p.publisher.id = :pid and p.pubmedId in (:pmids) ",
                PaperReference.class);
        query.setParameter("id", registryId).setParameter("pid", publisherId);
        query.setParameter("pmids", pmidList);
        return query.getResultList();
    }

    public static List<String> getMatchingPMIDsFromURLMatch(EntityManager em,
                                                            List<String> pmidList, Long registryId) {
        Query query = em.createQuery(
                "select distinct(p.pubmedId) from URLRec u inner join u.paper p " +
                        "where u.registry.id = :id and p.pubmedId in (:pmids)");
        query.setParameter("id", registryId).setParameter("pmids", pmidList);
        List<?> resultList = query.getResultList();
        List<String> matchedPMIDs = new ArrayList<String>(resultList.size());
        for (Object o : resultList) {
            matchedPMIDs.add((String) o);
        }
        return matchedPMIDs;
    }

    public static PaperReference findPaperReferenceMatching(EntityManager em,
                                                            String articleIdentifier,
                                                            Publisher publisher) {
        TypedQuery<PaperReference> query = em.createQuery(
                "from PaperReference p where p.publisherDocId = :pubId and p.publisher.id = :pid " +
                        "and p.pubmedId is not null",
                PaperReference.class);
        query.setParameter("pubId", articleIdentifier).setParameter("pid", publisher.getId());

        List<PaperReference> resultList = query.getResultList();
        if (resultList.isEmpty()) {
            return null;
        }
        return resultList.get(0);
    }

    public static List<String> getMatchingPMIDsFromNER(EntityManager em,
                                                       List<String> pmidList, Long registryId) {
        Query query = em.createQuery(
                "select distinct(p.pubmedId) from ResourceRec r inner join r.paper p " +
                        "where r.registry.id = :id and p.pubmedId in (:pmids)");
        query.setParameter("id", registryId).setParameter("pmids", pmidList);
        List<?> resultList = query.getResultList();
        List<String> matchedPMIDs = new ArrayList<String>(resultList.size());
        for (Object o : resultList) {
            matchedPMIDs.add((String) o);
        }
        return matchedPMIDs;
    }

    public static List<PaperReference> getMatchingPaperReferences(
            EntityManager em, Integer publisherId) {
        TypedQuery<PaperReference> query = em.createQuery(
                "from PaperReference p where p.publisher.id = :pid",
                PaperReference.class);
        query.setParameter("pid", publisherId);
        return query.getResultList();
    }

    public static List<PaperReference> getMatchingPaperReferences(EntityManager em, Integer publisherId, String pmid) {
        TypedQuery<PaperReference> query = em.createQuery(
                "from PaperReference p where p.publisher.id = :pid and p.pubmedId = :pmid",
                PaperReference.class);
        query.setParameter("pid", publisherId).setParameter("pmid", pmid);
        return query.getResultList();
    }

    public static List<String> getUniqueBatchIds4RegistryUpdateStatus(EntityManager em) {
        Query query = em.createQuery(
                "select distinct(s.batchId) from RegistryUpdateStatus s order by s.batchId desc");
        List<?> resultList = query.getResultList();
        List<String> batchIdList = new ArrayList<String>(resultList.size());
        batchIdList = typesafeCopy(resultList, batchIdList, String.class);
        return batchIdList;
    }

    public static <T, C extends Collection<T>> C typesafeCopy(Iterable<?> from, C to, Class<T> clazz) {
        for (Object o : from) {
            to.add(clazz.cast(o));
        }
        return to;
    }
}
