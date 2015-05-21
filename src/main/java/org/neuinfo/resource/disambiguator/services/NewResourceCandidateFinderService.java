package org.neuinfo.resource.disambiguator.services;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.neuinfo.resource.disambiguator.model.JobLog;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.ResourceCandidate;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;

/**
 * 03find_new_url_with_position.pl
 *
 * @author bozyurt
 */
public class NewResourceCandidateFinderService {

    @Inject
    @IndicatesPrimaryJpa
    Provider<EntityManager> emFactory = null;

    private Map<String, Registry> registryMap = new HashMap<String, Registry>(
            7919);
    static Logger log = Logger
            .getLogger(NewResourceCandidateFinderService.class);

    public NewResourceCandidateFinderService() {
    }

    public void handleNewResourceCandidates2(String batchId) throws Exception {
        Set<String> seenPMCSet = new HashSet<String>();
        EntityManager em = null;
        Transaction tx;
        StatelessSession session = null;
        long start = System.currentTimeMillis();
        try {
            log.info("Starting finding new resource candidates for " + batchId);
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();
            ScrollableResults urs = getAllUrlRecordsForBatch(session, batchId);
            Set<Integer> resourceCandidateUrlIdsSet = getExistingResourceCandidateUrlIds(
                    session, batchId);

            // don't check if the url is already listed as a resource candidate
            //Set<Integer> resourceCandidateUrlIdsSet = new HashSet<Integer>();

            int count = 0;
            while (urs.next()) {
                URLRec ur = (URLRec) urs.get(0);
                String url = ur.getUrl();
                url = Utils.normalizeUrl(url);
                url = url.replaceAll("\\\\", "");
                handle2(session, url, ur, seenPMCSet,
                        resourceCandidateUrlIdsSet, batchId);
                count++;
                if ((count % 1000) == 0) {
                    log.info("# of urls handled so far is " + count);
                }
            }

            tx.commit();
            saveJobStatus(batchId);
            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));
            log.info("Finished finding new resource candidates for " + batchId);
            log.info("---------------------------------------------------");
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    void saveJobStatus(String batchId) throws Exception {
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
            jl.setModifiedBy("NewResourceCandidateFinderService");
            jl.setOperation("pmc_resource_ref");
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

    void handle2(StatelessSession session, String url, URLRec ur,
                 Set<String> seenPMCSet, Set<Integer> resourceCandidateUrlIdsSet,
                 String batchId) {
        Long urlID = ur.getId();
        if (!registryMap.containsKey(url)) {
            boolean skip = false;
            if (url.indexOf("www.") != -1) {
                int idx = url.indexOf("www.");
                String url1 = url.substring(idx + 4);
                url1 = "http://" + url1;
                if (registryMap.containsKey(url1)) {
                    updateURLRec2(session, ur, url1);
                    skip = true;
                }
            }
            int dotCount = Utils.numOfMatches(url, '.');
            if (dotCount == 1) {
                String url1 = url.replaceFirst("http://", "http://www.");
                if (registryMap.containsKey(url1)) {
                    updateURLRec2(session, ur, url1);
                    skip = true;
                }
            }
            if (!url.endsWith("index.html")) {
                String url1 = url + ((!url.endsWith("/")) ? "/" : "") + "index.html";
                if (registryMap.containsKey(url1)) {
                    updateURLRec2(session, ur, url1);
                    skip = true;
                }
            }

            if (!skip && !seenPMCSet.contains(url)
                    && !resourceCandidateUrlIdsSet.contains(urlID)) {
                ResourceCandidate rc = new ResourceCandidate();
                rc.setUrl(ur);
                rc.setModificationTime(Calendar.getInstance());
                rc.setBatchId(batchId);
                session.insert(rc);
            }
        } else {
            updateURLRec2(session, ur, url);
        }
        seenPMCSet.add(url);
    }

    ScrollableResults getAllUrlRecordsForBatch(StatelessSession session,
                                               String batchId) {
        Query query = session.createQuery(
                "from URLRec where batchId = :batchId").setString("batchId",
                batchId);
        query.setReadOnly(false);
        query.setFetchSize(1000);
        return query.scroll(ScrollMode.FORWARD_ONLY);
    }

    public Set<Integer> getExistingResourceCandidateUrlIds(
            StatelessSession session, String batchId) {
        Query query = session
                .createQuery(
                        "select r.url.id from ResourceCandidate r where batchId = :batchId")
                .setString("batchId", batchId);
        List<?> list = query.list();
        Set<Integer> seenURLSet = new HashSet<Integer>(list.size());
        for (Object o : list) {
            seenURLSet.add(new Integer(o.toString()));
        }
        return seenURLSet;
    }

    void updateURLRec2(StatelessSession session, URLRec ur, String url) {
        Registry reg = registryMap.get(url);
        ur.setRegistry(reg);
        session.update(ur);
    }


    public void prepRegistryMap() throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> registryList = DisambiguatorFinder.getAllActiveRegistryRecords(em);
            for (Registry reg : registryList) {
                updateRegistryMap(reg);
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void prepRegistryMap(String resourceName) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> registryList = DisambiguatorFinder.getAllActiveRegistryRecords(em);
            for (Registry reg : registryList) {
                if (reg.getResourceName().indexOf(resourceName) != -1) {
                    updateRegistryMap(reg);
                    break;
                }
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected void updateRegistryMap(Registry reg) {
        String url = Utils.extractUrl(reg.getUrl());
        if (url != null) {
            url = Utils.normalizeUrl(url);
            registryMap.put(url, reg);
        }
        String altUrl = reg.getAlternativeUrl();
        String oldUrl = reg.getOldUrl();
        if (altUrl != null) {
            String[] urls = Utils.normalizeUrls(altUrl);
            for (String anAltURL : urls) {
                registryMap.put(anAltURL, reg);
            }
        }
        if (oldUrl != null) {
            oldUrl = Utils.normalizeUrl(oldUrl);
            registryMap.put(oldUrl, reg);
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("NewResourceCandidateFinderService", options);
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

        if (line.hasOption("h")) {
            usage(options);
        }
        String batchId = line.getOptionValue("d");
        Injector injector = Guice.createInjector(new RDPersistModule());

        NewResourceCandidateFinderService service = new NewResourceCandidateFinderService();
        injector.injectMembers(service);
        try {
            service.prepRegistryMap();
            service.handleNewResourceCandidates2(batchId);
        } finally {
            JPAInitializer.stopService();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
        //testDriver();

    }

    static void testDriver() throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());
        NewResourceCandidateFinderService service = new NewResourceCandidateFinderService();

        try {
            injector.injectMembers(service);
            service.prepRegistryMap("ImageJ");
            service.handleNewResourceCandidates2("201407");
        } finally {
            JPAInitializer.stopService();
        }
    }
}
