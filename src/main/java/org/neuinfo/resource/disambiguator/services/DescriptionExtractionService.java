package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.hibernate.criterion.Restrictions;
import org.neuinfo.resource.disambiguator.model.JobLog;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.DescriptionExtractor;
import org.neuinfo.resource.disambiguator.util.OpMode;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author bozyurt
 */
public class DescriptionExtractionService {
    private String batchId;
    private int scoreThreshold;
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(DescriptionExtractionService.class);

    public DescriptionExtractionService(String batchId, int scoreThreshold) {
        this.batchId = batchId;
        this.scoreThreshold = scoreThreshold;
    }

    void handle() throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        long start = System.currentTimeMillis();
        try {
            log.info("Started description extraction for " + batchId);
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();

            Criteria criteria = session.createCriteria(URLRec.class)
                    .add(Restrictions.eq("batchId", batchId));
                    // .add(Restrictions.ge("score", new Double(scoreThreshold)));
            criteria.setReadOnly(true).setFetchSize(1000).setCacheable(true);

            ScrollableResults results = criteria
                    .scroll(ScrollMode.FORWARD_ONLY);
            int count = 1;
            List<URLRec> modUrlList = new ArrayList<URLRec>();
            while (results.next()) {
                URLRec ur = (URLRec) results.get(0);
                if (ur.getDescription() == null) {
                    if (ur.getUrl().indexOf("www.biomedcentral.com") != -1) {
                        log.info("skipping " + ur.getUrl());
                        continue;
                    }

                    String description = extractDescription(ur);

                    if (description != null && description.trim().length() > 0) {

                        log.info("url:" + ur.getUrl());
                        log.info(description);

                        ur.setDescription(description.trim());
                        modUrlList.add(ur);
                    }
                    count++;
                }
                if ((count % 100) == 0) {
                    log.info("# of urls handled so far is " + count);
                    saveDescriptions(modUrlList);
                    modUrlList.clear();
                }
            } // while
            if (!modUrlList.isEmpty()) {
                saveDescriptions(modUrlList);
            }

            saveJobStatus();
            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));
            log.info("Finished description extraction for " + batchId);
            log.info("---------------------------------------------------");
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    void saveJobStatus() throws Exception {
        Transaction tx = null;
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();
            JobLog jl = new JobLog();
            jl.setBatchId(batchId);
            jl.setModifiedBy("DescriptionExtractionService");
            jl.setOperation("descriptions");
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

    String extractDescription(URLRec ur) {
        String description = null;
        try {
            URL url = new URL(ur.getUrl());

            DescriptionExtractor de = new DescriptionExtractor(url,
                    OpMode.DESCR);
            if (de.getAboutLink() != null) {
                String description1 = de.getDescription();
                url = new URL(de.getAboutLink());
                de = new DescriptionExtractor(url, OpMode.DESCR_FROM_ABOUT_PAGE);
                description = de.getDescription();
                if (description == null || description.trim().length() == 0) {
                    description = description1;
                } else {
                    if (description1 != null
                            && description1.length() > description.length()) {
                        description = description1;
                    }
                }

            } else {
                description = de.getDescription();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            log.warn("No description for " + ur.getUrl() + " Reason:" + t.getMessage());
            description = null;

        }
        if (description != null) {
            String desc = Utils.filterNonUTF8(description);
            if (!desc.equals(description)) {
                log.info("description:\n" + description);
                log.info("after Non UTF8 sequence filtering:\n" + description);

                description = desc;
            }

        }
        return description;
    }

    void saveDescriptions(List<URLRec> modURList) throws Exception {
        Transaction tx = null;
        EntityManager em = null;
        StatelessSession session = null;

        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();

            for (URLRec ur : modURList) {
                session.update(ur);
            }

            tx.commit();
        } catch (Exception x) {
            if (tx != null) {
                tx.rollback();
            }
            throw x;
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("DescriptionExtractor", options);
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
        Injector injector = null;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            DescriptionExtractionService des = new DescriptionExtractionService(
                    batchId, 5);

            injector.injectMembers(des);

            des.handle();
        } finally {
            JPAInitializer.stopService();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
    }

    static void testDriver() throws Exception {
        Injector injector = null;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            String batchId = "201310";
            DescriptionExtractionService des = new DescriptionExtractionService(
                    batchId, 5);

            injector.injectMembers(des);

            des.handle();
        } finally {
            JPAInitializer.stopService();
        }
    }

}
