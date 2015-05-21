package org.neuinfo.resource.disambiguator.services;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.persistence.EntityManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.hibernate.criterion.Restrictions;
import org.neuinfo.resource.disambiguator.model.JobLog;
import org.neuinfo.resource.disambiguator.model.ResourceCandidate;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.model.URLRedirectRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.neuinfo.resource.disambiguator.services.URLScorerService.URLRecWrapper;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

/**
 * @author bozyurt
 */
public class URLScorerServiceManager {
    private static final ExecutorService executorService = Executors
            .newFixedThreadPool(10);
    String batchId;
    List<String> categories = new ArrayList<String>(3);
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(URLScorerServiceManager.class);

    public URLScorerServiceManager(String batchId) {
        this.batchId = batchId;
        categories.add("biological_process");
        categories.add("anatomical_structure");
        categories.add("resource");
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    void updateRecords(List<URLRecWrapper> updatedList) throws Exception {
        Transaction tx;
        EntityManager em = null;
        StatelessSession session = null;
        try {
            //	log.info("updateRecords:: getting session");
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();

            log.info("updateRecords:: starting txn");
            for (URLRecWrapper urw : updatedList) {
                //	log.info("updating URLRec " + ur.getUrl() + " score:"
                //			+ ur.getScore() + " batchId:" + ur.getBatchId());
                session.update(urw.getUrlRec());
                // if there is any redirect detected persists that also
                if (urw.getFinalRedirectURI() != null) {
                    URLRec ur = urw.getUrlRec();
                    URLRedirectRec urr = getUrlRedirectRec(session, ur);
                    if (urr == null) {
                        urr = new URLRedirectRec();
                        urr.setUrl(ur);
                        urr.setRedirectUrl(urw.getFinalRedirectURI().toString());

                        session.insert(urr);
                    }
                }
            }
            //log.info("updateRecords:: commiting txn");
            tx.commit();
            log.info("updated records");

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    URLRedirectRec getUrlRedirectRec(StatelessSession session, URLRec ur) {
        Query query = session.createQuery("from URLRedirectRec u where u.url.id = :id").setLong("id", ur.getId());
        List<?> list = query.list();
        if (!list.isEmpty()) {
            return (URLRedirectRec) list.get(0);
        }
        return null;
    }

    void saveJobStatus() throws Exception {
        Transaction tx;
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();
            JobLog jl = new JobLog();
            jl.setBatchId(batchId);
            jl.setModifiedBy("URLScorerServiceManager");
            jl.setOperation("scoring");
            jl.setStatus("finished");
            session.insert(jl);
            tx.commit();
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public void handle(int startUrlId) throws Exception {
        long start = System.currentTimeMillis();
        log.info("Starting scoring urls for " + batchId);
        EntityManager em = emFactory.get();
        StatelessSession session = ((Session) em.getDelegate())
                .getSessionFactory().openStatelessSession();
        Criteria criteria = session.createCriteria(ResourceCandidate.class)
                .setFetchMode("url", FetchMode.JOIN)
                .add(Restrictions.eq("batchId", batchId))
                .createAlias("url", "u").add(Restrictions.isNull("u.score"));

        criteria.setReadOnly(true).setFetchSize(1000).setCacheable(false);
        ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);
        int count = 1;
        Map<String, List<ResourceCandidate>> hostMap = new HashMap<String, List<ResourceCandidate>>();
        List<URLRecWrapper> updatedList = Collections
                .synchronizedList(new ArrayList<URLScorerService.URLRecWrapper>(1000));
        List<Callable<Void>> jobs = new ArrayList<Callable<Void>>(1000);
        if (startUrlId > 0) {
            while (results.next()) {
                ResourceCandidate rc = (ResourceCandidate) results.get(0);
                if (rc.getUrl() != null) {
                    int id = (int) rc.getUrl().getId();
                    if (id == (startUrlId - 1)) {
                        break;
                    }
                }
            }
        }
        log.info("iterating through results");
        while (results.next()) {
            ResourceCandidate rc = (ResourceCandidate) results.get(0);

            if (rc.getUrl() != null) {
                String urlStr = rc.getUrl().getUrl().trim();
                boolean neededTrimming = !urlStr.equals(rc.getUrl().getUrl());

                try {
                    URL url = new URL(urlStr);
                    String host = url.getHost();
                    if (host == null) {
                        URLRec urlRec = rc.getUrl();
                        urlRec.setScore(-1.0);
                        if (neededTrimming) {
                            urlRec.setUrl(urlStr);
                        }
                        updatedList.add(new URLRecWrapper(urlRec, null));
                    } else {
                        List<ResourceCandidate> rcList = hostMap.get(host);
                        if (rcList == null) {
                            rcList = new ArrayList<ResourceCandidate>(1);
                            hostMap.put(host, rcList);
                        }
                        rcList.add(rc);
                    }

                } catch (MalformedURLException e) {
                    log.info("Bad url:" + urlStr + ", Reason: "
                            + e.getMessage());
                    URLRec urlRec = rc.getUrl();
                    if (neededTrimming) {
                        urlRec.setUrl(urlStr);
                    }
                    urlRec.setScore(-1.0);
                    updatedList.add(new URLRecWrapper(urlRec, null));
                }

            }
            if ((count % 100) == 0) {
                log.info("# of urls handled so far is " + count);
                while (true) {
                    boolean found = false;
                    for (List<ResourceCandidate> rcList : hostMap.values()) {
                        if (!rcList.isEmpty()) {
                            ResourceCandidate arc = rcList.remove(0);
                            Worker worker = new Worker(categories, arc,
                                    updatedList, this.batchId);
                            jobs.add(worker);
                            found = true;
                        }
                    }
                    if (!found) {
                        break;
                    }
                }
                log.info("processing all jobs 10 at a time");
                executorService.invokeAll(jobs);
                log.info("updating records");
                updateRecords(updatedList);
                log.info("updated records");

                updatedList.clear();
                hostMap.clear();
                jobs.clear();
            }
            count++;
        }

        // handle the remaining
        if (!jobs.isEmpty()) {
            boolean hasData = true;
            while (hasData) {
                boolean found = false;
                for (List<ResourceCandidate> rcList : hostMap.values()) {
                    if (!rcList.isEmpty()) {
                        ResourceCandidate arc = rcList.remove(0);
                        Worker worker = new Worker(categories, arc,
                                updatedList, this.batchId);
                        jobs.add(worker);
                        found = true;
                    }
                }
                if (!found) {
                    break;
                }
            }
            executorService.invokeAll(jobs);
            log.info("updating records");
            updateRecords(updatedList);
        }

        saveJobStatus();
        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished scoring urls for " + batchId);
        log.info("---------------------------------------------------");

    }

    public static class Worker implements Callable<Void> {
        URLScorerService service;
        List<URLRecWrapper> updatedList;
        ResourceCandidate rc;

        public Worker(List<String> categories, ResourceCandidate rc,
                      List<URLRecWrapper> updatedList, String batchId) throws Exception {
            service = new URLScorerService(categories, batchId);
            this.rc = rc;
            this.updatedList = updatedList;
        }

        @Override
        public Void call() throws Exception {
            if (rc.getUrl() != null) {
                String url = rc.getUrl().getUrl().trim();
                boolean neededTrimming = !url.equals(rc.getUrl().getUrl());
                log.info("scoring " + url);
                try {
                    URLScorerService.URLRecWrapper urw = service.scoreURL(rc.getUrl());
                    URLRec ur = urw.getUrlRec();
                    log.info("score: " + ur.getScore() + "  " + ur.getUrl());
                    if (neededTrimming) {
                        ur.setUrl(url);
                    }
                    updatedList.add(urw);
                } catch (Throwable t) {
                    log.error("Worker scoreURL:", t);
                    URLRec urlRec = rc.getUrl();
                    urlRec.setScore(-1.0);
                    if (neededTrimming) {
                        urlRec.setUrl(url);
                    }
                    updatedList.add(new URLRecWrapper(urlRec, null));
                }
            }

            return null;
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("URLScorerServiceManager", options);
        System.exit(1);
    }

    public static void cli(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option myOption = Option.builder("d").required().hasArg()
                .argName("monthYear").desc("monthYear").build();
        Options options = new Options();
        options.addOption(help);
        options.addOption(myOption);

        CommandLineParser cli = new DefaultParser();
        CommandLine line = null;
        try {
            line = cli.parse(options, args);
        } catch (Exception x) {
            System.err.println(x.getMessage());
            usage(options);
        }

        assert line != null;
        if (line.hasOption("h")) {
            usage(options);
        }
        String batchId = line.getOptionValue("d");
        Injector injector = Guice.createInjector(new RDPersistModule());

        URLScorerServiceManager service = new URLScorerServiceManager(batchId);

        injector.injectMembers(service);
        try {
            service.handle(-1);
        } finally {
            JPAInitializer.stopService();
            service.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
    }
}
