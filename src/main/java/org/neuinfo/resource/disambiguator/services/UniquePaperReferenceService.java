package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import gnu.trove.TIntObjectHashMap;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.JobLog;
import org.neuinfo.resource.disambiguator.model.PaperReference;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Assertion;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.EntityManager;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Determines and tags unique paper resource references not seen in the PMC open access journals.
 * <p/>
 * <p/>
 * Created by bozyurt on 1/16/14.
 */
public class UniquePaperReferenceService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    TIntObjectHashMap pmid2RegMap = new TIntObjectHashMap();
    static Logger log = Logger.getLogger(UniquePaperReferenceService.class);

    public void handle() throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        Connection con = null;
        try {
            em = Utils.getEntityManager(emFactory);
            long start = System.currentTimeMillis();
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            prepPmid2RegMap();
            //Query q = session.createQuery("from PaperReference r where r.flags = 0");

            log.info("getting PaperReference records...");
            Query q = session.createQuery("select r.id, r.pubmedId, r.registry.id from PaperReference r");
            //q.setReadOnly(true).setFetchSize(Integer.MIN_VALUE); // retrieve row by row for MySQL (seems not working) IBO
            q.setReadOnly(true);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            int count = 0;
            int updateCount = 0;
            List<PRProxy> proxies = new LinkedList<PRProxy>();
            while (results.next()) {
                long id = results.getLong(0);
                String pmidStr = results.getString(1);
                Long paperRegistryId = results.getLong(2);
                int pmid = Integer.parseInt(pmidStr);
                proxies.add(new PRProxy(id, paperRegistryId, pmid));
            }
            results.close();

            log.info("read " + proxies.size() + " PaperReference proxy records");
            for(PRProxy proxy : proxies) {
                PaperReference pr = new PaperReference(proxy.id);
                int pmid = proxy.pmid;
                Long paperRegistryId = proxy.registryId;

                // PaperReference pr = (PaperReference) results.get(0);
                if (this.pmid2RegMap.containsKey(pmid)) {
                    Object o = this.pmid2RegMap.get(pmid);
                    if (o instanceof Long) {
                        Long registryId = (Long) o;
                        if (paperRegistryId != registryId) {
                            setFlags(pr);
                            updatePaperRef(pr);
                            updateCount++;
                        }
                    } else {
                        List<Long> list = (List<Long>) this.pmid2RegMap.get(pmid);
                        boolean found = false;
                        for (Long registryId : list) {
                            if (paperRegistryId == registryId) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            setFlags(pr);
                            updatePaperRef(pr);
                            updateCount++;
                        }
                    }

                } else {
                    setFlags(pr);
                    updatePaperRef(pr);
                    updateCount++;
                }
                count++;
                if ((count % 100) == 0) {
                    log.info("# of PMIDs processed so far:" + count);
                    log.info("# of new PMID/Registry combinations so far:" + updateCount);
                }
            }
            saveJobStatus();
            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));
            log.info("Finished unique paper reference service");
            log.info("---------------------------------------------------");

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public static class PRProxy {
        final long id;
        final Long registryId;
        final int pmid;

        public PRProxy(long id, Long registryId, int pmid) {
            this.id = id;
            this.registryId = registryId;
            this.pmid = pmid;
        }
    }

    void setFlags(PaperReference pr) {
        if (pr.getFlags() == 0) {
            pr.setFlags(PaperReference.UNIQUE_REF);
        } else {
            pr.setFlags(PaperReference.UNIQUE_REF | pr.getFlags());
        }
    }

    private void updatePaperRef(PaperReference pr) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            PaperReference thePR = em.find(PaperReference.class, pr.getId());
            Assertion.assertNotNull(thePR);
            thePR.setFlags(pr.getFlags());
            em.merge(thePR);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            log.error(x.getMessage());
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    void prepPmid2RegMap() {
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            Query query = session.createQuery(
                    "select distinct p.pubmedId, u.registry.id as regid from Paper p, URLRec u " +
                            "where u.paper.id = p.id and u.registry is not null and p.pubmedId is not null"
            );
            query.setReadOnly(false).setFetchSize(1000);
            ScrollableResults results = query.scroll(ScrollMode.FORWARD_ONLY);
            while (results.next()) {
                int pmid = Integer.parseInt((String) results.get(0));
                Long registryId = (Long) results.get(1);
                // System.out.printf("pmid:%d registryId:%d%n", pmid, registryId);
                if (this.pmid2RegMap.containsKey(pmid)) {
                    Object o = this.pmid2RegMap.get(pmid);
                    if (o instanceof Long) {
                        List<Long> list = new ArrayList<Long>(2);
                        list.add((Long) o);
                        list.add(registryId);
                        this.pmid2RegMap.put(pmid, list);
                    } else {
                        List<Long> list = (List<Long>) this.pmid2RegMap.get(pmid);
                        list.add(registryId);
                    }
                } else {
                    this.pmid2RegMap.put(pmid, registryId);
                }
            }

            results.close();
            query = session.createQuery(
                    "select distinct p.pubmedId, r.registry.id as regid from Paper p, ResourceRec r " +
                            "where r.paper.id = p.id and r.registry is not null and p.pubmedId is not null"
            );
            query.setReadOnly(false).setFetchSize(1000);
            results = query.scroll(ScrollMode.FORWARD_ONLY);
            while (results.next()) {
                int pmid = Integer.parseInt((String) results.get(0));
                Long registryId = (Long) results.get(1);
                if (this.pmid2RegMap.containsKey(pmid)) {
                    Object o = this.pmid2RegMap.get(pmid);
                    if (o instanceof Long) {
                        List<Long> list = new ArrayList<Long>(2);
                        Long regId = (Long) o;
                        if (regId != registryId) {
                            list.add(regId);
                        }
                        list.add(registryId);
                        this.pmid2RegMap.put(pmid, list);
                    } else {
                        List<Long> list = (List<Long>) this.pmid2RegMap.get(pmid);
                        if (!list.contains(registryId)) {
                            list.add(registryId);
                        }
                    }
                } else {
                    this.pmid2RegMap.put(pmid, registryId);
                }
            }

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }

    }

    void saveJobStatus() {
        Transaction tx;
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();
            JobLog jl = new JobLog();
            // batchId does not apply
            jl.setBatchId("all");
            jl.setModifiedBy("UniquePaperReferenceService");
            jl.setOperation("unique_paper_ref");
            jl.setStatus("finished");
            session.insert(jl);

            tx.commit();
        } catch (Exception x) {
            log.error(x.getMessage());
            Utils.rollbackTransaction(em);
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            UniquePaperReferenceService service = new UniquePaperReferenceService();
            injector.injectMembers(service);

            service.handle();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
