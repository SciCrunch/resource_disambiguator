package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.EntityManager;
import java.util.*;

/**
 * Created by bozyurt on 2/20/14.
 */
public class UrlResourceAssociationService {
    @Inject
    @IndicatesPrimaryJpa
    Provider<EntityManager> emFactory = null;
    private Map<String, Registry> registryMap = new HashMap<String, Registry>();
    int updateCount = 0;
    static Logger log = Logger
            .getLogger(UrlResourceAssociationService.class);

    public void handle() throws Exception {
        Set<String> seenPMCSet = new HashSet<String>();
        EntityManager em = null;
        Transaction tx;
        StatelessSession session = null;
        long start = System.currentTimeMillis();
        try {
            log.info("Starting  association of urls with registry items");
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();
            ScrollableResults urs = getAllUrlRecords(session);
            int count = 0;
            while (urs.next()) {
                URLRec ur = (URLRec) urs.get(0);
                String url = ur.getUrl();
                url = Utils.normalizeUrl(url);
                url = url.replaceAll("\\\\", "");
                handleUrl(session, url, ur, seenPMCSet);
                count++;
                if ((count % 1000) == 0) {
                    log.info("# of urls handled so far is " + count);
                }
            }
            tx.commit();
            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));
            log.info("updateCount:" + updateCount);
            log.info("---------------------------------------------------");
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    void handleUrl(StatelessSession session, String url, URLRec ur, Set<String> seenPMCSet) {
        if (!registryMap.containsKey(url)) {
            boolean skip = false;
            if (url.indexOf("www.") != -1) {
                int idx = url.indexOf("www.");
                String url1 = url.substring(idx + 4);
                url1 = "http://" + url1;
                if (registryMap.containsKey(url1)) {
                    updateURLRec(session, ur, url1);
                    skip = true;
                    updateCount++;
                }
            }
            if (!skip) {
                int dotCount = Utils.numOfMatches(url, '.');
                if (dotCount == 1) {
                    String url1 = url.replaceFirst("http://", "http://www.");
                    if (registryMap.containsKey(url1)) {
                        updateURLRec(session, ur, url1);
                        updateCount++;
                    }
                }
            }
        } else {
            updateURLRec(session, ur, url);
            updateCount++;
        }
        seenPMCSet.add(url);
    }

    void updateURLRec(StatelessSession session, URLRec ur, String url) {
        Registry reg = registryMap.get(url);
        ur.setRegistry(reg);
        session.update(ur);
    }

    public void prepRegistryMap() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);

            List<Registry> registryRecords = DisambiguatorFinder
                    .getAllRegistryRecords(em);
            for (Registry reg : registryRecords) {
                String url = reg.getUrl();
                if (url != null) {
                    url = Utils.normalizeUrl(url);
                    this.registryMap.put(url, reg);
                    String altUrl = reg.getAlternativeUrl();
                    String oldUrl = reg.getOldUrl();
                    if (altUrl != null) {
                        altUrl = Utils.normalizeUrl(altUrl);
                        this.registryMap.put(altUrl, reg);
                    }
                    if (oldUrl != null) {
                        oldUrl = Utils.normalizeUrl(oldUrl);
                        this.registryMap.put(oldUrl, reg);
                    }

                }
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    ScrollableResults getAllUrlRecords(StatelessSession session) {
        Query query = session.createQuery("from URLRec u where u.registry is null");
        return query.setReadOnly(false).setFetchSize(1000).scroll(ScrollMode.FORWARD_ONLY);
    }


    public static void main(String[] args) throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());
        try {
            UrlResourceAssociationService service = new UrlResourceAssociationService();
            injector.injectMembers(service);
            service.prepRegistryMap();
            service.handle();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
