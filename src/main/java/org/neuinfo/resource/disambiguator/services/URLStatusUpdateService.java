package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.UrlStatus;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.OpMode;
import org.neuinfo.resource.disambiguator.util.URLContent;
import org.neuinfo.resource.disambiguator.util.URLValidator;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.EntityManager;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * checks if urls from rd_urls table are alive or not
 * Created by bozyurt on 2/14/14.
 */
public class URLStatusUpdateService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    protected int badCount = 0;
    //static Pattern supPattern = Pattern.compile("suppl?", Pattern.CASE_INSENSITIVE);
    private static final ExecutorService executorService = Executors
            .newFixedThreadPool(10);
    static Logger log = Logger.getLogger(URLStatusUpdateService.class);

    public void shutdown() {
        executorService.shutdownNow();
    }

    public void handleParallel() throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        long start = System.currentTimeMillis();
        try {
            System.out.println("-- before getExistingUrlStatusRecs");
            final Set<Integer> urlStatusRecsSet = getExistingUrlStatusRecs();
            System.out.println("-- after getExistingUrlStatusRecs");
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("select u.url, u.id from URLRec u");
            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            badCount = 0;
            int count = 0;
            List<UrlStatus> usList = Collections.synchronizedList(new ArrayList<UrlStatus>(1000));
            List<Callable<Void>> jobs = new ArrayList<Callable<Void>>(1000);
            while (results.next()) {
                String urlStr = (String) results.get(0);
                Integer urlID = (Integer) results.get(1);
                if (!urlStatusRecsSet.contains(urlID)) {
                    Worker worker = new Worker(this, urlStr, urlID, usList);
                    jobs.add(worker);

                    if (jobs.size() >= 100) {
                        log.info("processing all jobs 10 at a time");
                        executorService.invokeAll(jobs);
                        jobs.clear();
                        log.info("now adding urlstatus recs");
                        System.out.println("handled so far " + count);
                        saveUrlStatusRecs(usList);

                    }
                }
                count++;
            }
            if (!jobs.isEmpty()) {
                log.info("processing all jobs 10 at a time");
                executorService.invokeAll(jobs);
                jobs.clear();
                log.info("now adding urlstatus recs");
                System.out.println("handled so far " + count);
                saveUrlStatusRecs(usList);
            }
            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));
            log.info("Finished url status updating");
            log.info("---------------------------------------------------");
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }

    }

    public void handle() {
        EntityManager em = null;
        StatelessSession session = null;
        Map<String, List<String>> host2URLMap = new HashMap<String, List<String>>();
        Map<String, Boolean> pathStatusMap = new HashMap<String, Boolean>();
        try {
            System.out.println("-- before getExistingUrlStatusRecs");
            final Set<Integer> urlStatusRecsSet = getExistingUrlStatusRecs();
            System.out.println("-- after getExistingUrlStatusRecs");
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("select u.url, u.id from URLRec u");
            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            badCount = 0;
            int count = 0;
            List<UrlStatus> usList = new ArrayList<UrlStatus>(1000);
            while (results.next()) {
                String urlStr = (String) results.get(0);
                Long urlID = (Long) results.get(1);
                if (!urlStatusRecsSet.contains(urlID)) {
                    try {
                        URL url = new URL(urlStr);
                        String path = url.getPath();

                        String host = url.getHost();
                        String query = url.getQuery();
                        String urlPath = urlStr;

                        UrlStatus us = new UrlStatus();
                        us.setUrlID(urlID);

                        boolean found = false;
                        if (query != null && query.length() > 0) {
                            urlPath = urlPath.substring(0, urlStr.length() - query.length());
                            if (pathStatusMap.containsKey(urlPath)) {
                                us.setAlive(pathStatusMap.get(urlPath));
                                us.setFlags(UrlStatus.INFERRED);
                                found = true;
                            }
                        }

                        if (!found) {
                            System.out.println("checking url:" + urlStr);
                            URLValidator uv = new URLValidator(urlStr, OpMode.FULL_CONTENT);
                            URLContent urlContent = uv.checkValidity(true);


                            us.setAlive(urlContent != null);
                            us.setFlags(UrlStatus.CHECKED);
                            if (urlContent != null) {
                                pathStatusMap.put(urlPath, Boolean.TRUE);
                            } else {
                                pathStatusMap.put(urlPath, Boolean.FALSE);
                            }
                        }
                        usList.add(us);
                    } catch (Exception x) {
                        UrlStatus us = new UrlStatus();
                        us.setUrlID(urlID);
                        us.setAlive(false);
                        us.setFlags(UrlStatus.BAD_URL);
                        usList.add(us);

                        badCount++;
                    }
                }
                count++;

                if (usList.size() >= 100) {
                    System.out.println("handled so far " + count);
                    saveUrlStatusRecs(usList);
                }
                if ((count % 1000) == 0) {
                    System.out.println("seen so far " + count);
                }
            }
            if (!usList.isEmpty()) {
                saveUrlStatusRecs(usList);
            }
            System.out.println(">> badCount:" + badCount + " total:" + count);
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    Set<Integer> getExistingUrlStatusRecs() {
        Set<Integer> seenUrlIdSet = new HashSet<Integer>(50001);
        StatelessSession session = null;
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("select u.urlID from UrlStatus u");
            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            while (results.next()) {
                Integer urlID = (Integer) results.get(0);
                seenUrlIdSet.add(urlID);
            }

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }

        return seenUrlIdSet;

    }

    void saveUrlStatusRecs(List<UrlStatus> usList) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            for (UrlStatus us : usList) {
                em.persist(us);
            }
            Utils.commitTransaction(em);
            usList.clear();
        } catch (Exception x) {
            x.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    public static class Worker implements Callable<Void> {
        URLStatusUpdateService service;
        String urlStr;
        long urlID;
        List<UrlStatus> usList;

        public Worker(URLStatusUpdateService service, String urlStr, long urlID, List<UrlStatus> usList) {
            this.service = service;
            this.urlStr = urlStr;
            this.urlID = urlID;
            this.usList = usList;
        }

        @Override
        public Void call() throws Exception {
            try {
                URL url = new URL(urlStr);

                UrlStatus us = new UrlStatus();
                us.setUrlID(urlID);
                System.out.println("checking url:" + urlStr);
                URLValidator uv = new URLValidator(urlStr, OpMode.FULL_CONTENT);
                URLContent urlContent = uv.checkValidity(true);


                us.setAlive(urlContent != null);
                us.setFlags(UrlStatus.CHECKED);

                usList.add(us);
            } catch (Exception x) {
                UrlStatus us = new UrlStatus();
                us.setUrlID(urlID);
                us.setAlive(false);
                us.setFlags(UrlStatus.BAD_URL);
                usList.add(us);

                service.badCount++;
            }
            return null;
        }

    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        URLStatusUpdateService service = new URLStatusUpdateService();
        try {
            injector = Guice.createInjector(new RDPersistModule());
            injector.injectMembers(service);

            // service.handle();
            service.handleParallel();
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            JPAInitializer.stopService();
            service.shutdown();
        }
    }

}
