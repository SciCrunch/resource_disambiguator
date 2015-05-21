package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.Paper;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import javax.persistence.*;
import javax.persistence.Query;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by bozyurt on 1/9/14.
 */
public class URLInterBatchDuplicateRemover {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(URLInterBatchDuplicateRemover.class);

    public void handle() throws Exception {
        StatelessSession session = null;
        EntityManager em = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMM");
        try {
            em = Utils.getEntityManager(emFactory);

            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Criteria criteria = session.createCriteria(Paper.class);
            criteria.setReadOnly(true).setFetchSize(1000).setCacheable(false);
            ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);
            Map<Long, URLRec> urlIdMap = new HashMap<Long, URLRec>(17);
            Date oldestDate = null;
            Long theURId = null;
            int count = 0;
            while (results.next()) {
                Paper paper = (Paper) results.get(0);
                TypedQuery<URLRec> uq = em.createQuery("from URLRec u where u.paper.id = :pid order by url",
                        URLRec.class).setParameter("pid", paper.getId());
                String curUrl = null;
                for (URLRec ur : uq.getResultList()) {
                    if (curUrl == null || !curUrl.equals(ur.getUrl())) {
                        curUrl = ur.getUrl();
                        if (theURId != null) {
                            System.out.println("theURId:" + theURId + " oldestDate:" + oldestDate);
                            if (urlIdMap.size() > 1) {
                                for (Long urId : urlIdMap.keySet()) {
                                    if (urId != theURId) {
                                        //System.out.println("to remove " + urId);
                                        removeDuplicates(em, urlIdMap, theURId);
                                    }
                                }
                            }
                        }
                        urlIdMap.clear();
                        oldestDate = null;
                        theURId = null;
                    }
                    urlIdMap.put(ur.getId(), ur);
                    Date bd = sdf.parse(ur.getBatchId());
                    if (oldestDate == null || oldestDate.after(bd)) {
                        oldestDate = bd;
                        theURId = ur.getId();
                    }
                    System.out.printf("%d, %s, %s%n", ur.getId(), ur.getUrl(), ur.getBatchId());
                }
                // final block
                if (theURId != null) {
                    System.out.println("theURId:" + theURId + " oldestDate:" + oldestDate);
                    if (urlIdMap.size() > 1) {
                        for (Long urId : urlIdMap.keySet()) {
                            if (urId != theURId) {
                                //System.out.println("to remove " + urId);
                                removeDuplicates(em, urlIdMap, theURId);
                            }
                        }
                    }
                }
                // if (count > 10) {
                //     break;
                //  }
                count++;
            }

//            Query query = em.createNativeQuery(
//                    "select * from rd_urls order by batch_id, doc_id", URLRec.class);


        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    private void removeDuplicates(EntityManager em, Map<Long, URLRec> urlIdMap, Long theURId) {
        try {
            Utils.beginTransaction(em);
            for (Long urId : urlIdMap.keySet()) {
                if (urId != theURId) {
                    System.out.println("to remove " + urId);
                    Query query = em.createQuery("delete from URLRec u where u.id = :id").setParameter("id", urId);
                    query.executeUpdate();
                }
            }
            Utils.commitTransaction(em);
        } catch (Exception x) {
            x.printStackTrace();
            Utils.rollbackTransaction(em);
        }
    }


    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            URLInterBatchDuplicateRemover remover = new URLInterBatchDuplicateRemover();
            injector.injectMembers(remover);

            remover.handle();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
