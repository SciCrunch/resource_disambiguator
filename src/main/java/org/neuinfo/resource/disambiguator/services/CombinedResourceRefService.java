package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.hibernate.Query;
import org.neuinfo.resource.disambiguator.model.CombinedResourceRef;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.*;
import java.math.BigInteger;
import java.util.*;

/**
 * Created by bozyurt on 2/6/14.
 */
public class CombinedResourceRefService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory = null;
    static Logger log = Logger.getLogger(CombinedResourceRefService.class);


    public void addAnnotationInfo() throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            // ScrollableResults results = getPubSearchAnnotInfoResults(session);
            ScrollableResults results = getNERAnnotationInfoResults(session);
            int count = 0;
            while (results.next()) {
                String pubmedId = (String) results.get(0);
                String nifId = (String) results.get(1);
                Integer registryId = ((BigInteger) results.get(2)).intValue();
                String src = (String) results.get(3);
                String label = (String) results.get(4);
                if (pubmedId == null || nifId == null) {
                    continue;
                }
                CombinedResourceRef crr = new CombinedResourceRef();
                crr.setNifId(nifId);
                crr.setSource(src);
                crr.setPubmedId(pubmedId);
                crr.setRegistryId(registryId);
                modifyCombinedResourceRef(crr, label);
                System.out.println(crr + " label:" + label);
                count++;
            }
            System.out.println(count + " items.");

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }


    public void handle() throws Exception {
        EntityManager em = null;
        Transaction tx;
        StatelessSession session = null;
        long start = System.currentTimeMillis();
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();

            ScrollableResults results = getCombinedResults(session);
            List<CombinedResourceRef> crrList = new ArrayList<CombinedResourceRef>(1000);
            int count = 0;
            while (results.next()) {
                String pubmedId = (String) results.get(0);
                String nifId = (String) results.get(1);
                Integer registryId = ((BigInteger) results.get(2)).intValue();
                String src = (String) results.get(3);
                if (pubmedId == null || nifId == null) {
                    continue;
                }
                count++;
                CombinedResourceRef crr = new CombinedResourceRef();
                crr.setNifId(nifId);
                crr.setSource(src);
                crr.setPubmedId(pubmedId);
                crr.setRegistryId(registryId);
                crrList.add(crr);
                if (crr.getSource().equals("u")) {
                    crr.setConfidence(1.0);
                } else if (crr.getSource().equals("n")) {
                    crr.setConfidence(0.8);
                } else {
                    crr.setConfidence(0.2);
                }

                if ((count % 1000) == 0) {
                    saveCombinedResourceRefs(crrList);
                    crrList.clear();
                    System.out.println("processed so far:" + count);
                }
            }

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    void modifyCombinedResourceRef(CombinedResourceRef theCRR, String label) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            TypedQuery<CombinedResourceRef> query =
                    em.createQuery("from CombinedResourceRef c where c.pubmedId = :id",
                            CombinedResourceRef.class).setParameter("id", theCRR.getPubmedId());
            List<CombinedResourceRef> list = query.getResultList();
            Map<String, CombinedResourceRef> seenMap = new HashMap<String, CombinedResourceRef>();
            for (CombinedResourceRef crr : list) {
                String key = prepKey(crr);
                seenMap.put(key, crr);
            }
            String theKey = prepKey(theCRR);
            String src = theCRR.getSource();
            if (seenMap.containsKey(theKey)) {
                if (label.equals("bad")) {
                    CombinedResourceRef crr = seenMap.get(theKey);
                    em.remove(crr);
                } else {
                    CombinedResourceRef crr = seenMap.get(theKey);
                    if (src.equals("p") || src.equals("u") || src.equals("n")) {
                        crr.setConfidence(0.95);
                    }
                    em.merge(crr);
                }
            }

            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void saveCombinedResourceRefs(List<CombinedResourceRef> crrList) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            TypedQuery<CombinedResourceRef> query =
                    em.createQuery("from CombinedResourceRef c where c.pubmedId in (:ids)",
                            CombinedResourceRef.class);
            List<String> pmidList = new ArrayList(crrList.size());
            for (CombinedResourceRef crr : crrList) {
                pmidList.add(crr.getPubmedId());
            }
            query.setParameter("ids", pmidList);

            List<CombinedResourceRef> list = query.getResultList();
            Set<String> seenSet = new HashSet<String>();
            for (CombinedResourceRef crr : list) {
                String key = prepKey(crr);
                seenSet.add(key);
            }
            for (CombinedResourceRef crr : crrList) {
                String key = prepKey(crr);
                if (!seenSet.contains(key)) {
                    em.persist(crr);
                }
            }

            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static String prepKey(CombinedResourceRef crr) {
        StringBuilder sb = new StringBuilder();
        sb.append(crr.getPubmedId()).append(':').append(crr.getRegistryId());
        return sb.toString();
    }

    ScrollableResults getPubSearchAnnotInfoResults(StatelessSession session) {
        Query query = session.createSQLQuery("select p.pubmed_id, r.nif_id, r.id, 'p' \\:\\: varchar(1) as src, a.label  " +
                "from rd_ps_annot_info a, rd_paper_reference p, registry r " +
                "where  a.pr_id = p.id and p.registry_id = r.id and (a.label = 'good' or a.label = 'bad') ");
        query.setReadOnly(true).setFetchSize(1000);
        return query.scroll(ScrollMode.FORWARD_ONLY);
    }

    ScrollableResults getNERAnnotationInfoResults(StatelessSession session) {
       Query query = session.createSQLQuery("select p.pubmed_id, r.nif_id, r.id, 'n' \\:\\: varchar(1) as src, a.label  " +
                "from rd_ner_annot_info a, rd_resource_ref rr, registry r, rd_paper p " +
                "where  a.rr_id = rr.id and rr.registry_id = r.id and rr.doc_id = p.id and (a.label = 'good' or a.label = 'bad') ");
        query.setReadOnly(true).setFetchSize(1000);
        return query.scroll(ScrollMode.FORWARD_ONLY);
    }

    ScrollableResults getCombinedResults(StatelessSession session) {
        Query query = session.createSQLQuery(
                "select p.pubmed_id, r.nif_id, r.id, 'u' as src from rd_urls u, rd_paper p, registry r where " +
                        "u.doc_id = p.id and u.registry_id = r.id union all " +
                        "select p.pubmed_id, r.nif_id, r.id, 'n' as src from rd_resource_ref a, rd_paper p, registry r where " +
                        "a.registry_id = r.id and a.doc_id = p.id and a.flags = 0 union all " +
                        "select p.pubmed_id, r.nif_id, r.id, 'p' as src from rd_paper_reference p, registry r where " +
                        "p.registry_id = r.id and p.flags = 1");
        query.setReadOnly(true).setFetchSize(1000);
        return query.scroll(ScrollMode.FORWARD_ONLY);
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            CombinedResourceRefService service = new CombinedResourceRefService();

            injector.injectMembers(service);

            // service.handle();

            service.addAnnotationInfo();
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
