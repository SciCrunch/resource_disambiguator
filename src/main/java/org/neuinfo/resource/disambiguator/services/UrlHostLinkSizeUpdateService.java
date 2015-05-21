package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.EntityManager;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bozyurt on 2/20/14.
 */
public class UrlHostLinkSizeUpdateService {
    @Inject
    @IndicatesPrimaryJpa
    Provider<EntityManager> emFactory = null;

    static Logger log = Logger
            .getLogger(UrlResourceAssociationService.class);

    public void handle() throws Exception {
        EntityManager em = null;
        Transaction tx;
        StatelessSession session = null;
        Map<String, HostLinkFreq> hlfMap = new HashMap<String, HostLinkFreq>();
        long start = System.currentTimeMillis();
        try {
            log.info("Starting  Url Host link size record update process");
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            ScrollableResults results = getAllUrls(session);
            while (results.next()) {
                String urlStr = (String) results.get(0);
                try {
                    URL url = new URL(urlStr);
                    String host = url.getHost().toLowerCase();
                    HostLinkFreq hlf = hlfMap.get(host);
                    if (hlf == null) {
                        hlf = new HostLinkFreq(host);
                        hlfMap.put(host, hlf);
                    }
                    hlf.incr();
                } catch (Exception x) {
                    // ignore
                }
            }
            results.close();

            int count = 0;
            tx = session.beginTransaction();
            results = getAllUrlRecords(session);
            while (results.next()) {
                URLRec ur = (URLRec) results.get(0);

                try {
                    URL url = new URL(ur.getUrl());
                    String host = url.getHost().toLowerCase();
                    HostLinkFreq hlf = hlfMap.get(host);
                    if (hlf != null) {
                        ur.setHostLinkSize(hlf.count);
                        session.update(ur);
                    }

                } catch (Exception x) {
                    // ignore
                }
                count++;
                if ((count % 1000) == 0) {
                    log.info("# of urls handled so far is " + count);
                }
            }

            tx.commit();
            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));
            log.info("---------------------------------------------------");
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    ScrollableResults getAllUrls(StatelessSession session) {
        Query query = session.createQuery("select u.url from URLRec u");
        return query.setReadOnly(true).setFetchSize(1000).scroll(ScrollMode.FORWARD_ONLY);
    }

    ScrollableResults getAllUrlRecords(StatelessSession session) {
        Query query = session.createQuery("from URLRec u");
        return query.setReadOnly(false).setFetchSize(1000).scroll(ScrollMode.FORWARD_ONLY);
    }

    public static class HostLinkFreq {
        String host;
        int count = 0;

        public HostLinkFreq(String host) {
            this.host = host;
        }

        void incr() {
            count++;
        }
    }

     public static void main(String[] args) throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());
        try {
            UrlHostLinkSizeUpdateService service = new UrlHostLinkSizeUpdateService();
            injector.injectMembers(service);
            service.handle();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
