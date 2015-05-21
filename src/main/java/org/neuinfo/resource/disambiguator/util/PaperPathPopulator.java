package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.PaperPath;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.ExtractUrlsFromPMCService;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by bozyurt on 1/10/14.
 */
public class PaperPathPopulator {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(PaperPathPopulator.class);


    public void handle(String batchId) throws Exception {
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");
        String indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchId);
        int baseDirLen = baseDir.getAbsolutePath().length();
        File[] journals = baseDir.listFiles();
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            int count = 0;
            Integer theFlag = 0;
            for (File journalDir : journals) {
                List<File> papers = new ArrayList<File>();
                ExtractUrlsFromPMCService.getPapers(journalDir, papers);

                for (File paperPath : papers) {
                    String relPath = paperPath.getAbsolutePath().substring(baseDirLen);

                    TypedQuery<PaperPath> query = em.createQuery(
                            "from PaperPath p  where p.filePath = :path",
                            PaperPath.class);
                    List<PaperPath> ppList = query.setParameter("path",
                            relPath).getResultList();
                    if (ppList.isEmpty()) {
                        PaperPath pp = new PaperPath();
                        pp.setFilePath(relPath);
                        pp.setFlags(theFlag);
                        em.persist(pp);
                        count++;
                    }
                    if ((count % 100) == 0) {
                        // flush a batch of inserts and release memory
                        em.flush();
                        em.clear();
                        log.info(count + " paper paths handled so far.");
                    }
                }
            }

            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("PaperPathPopulator [-h] -b <batchId>", options);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option myOption = Option.builder("b").required().hasArg()
                .argName("batchId").desc("monthYear").build();
        Options options = new Options();
        options.addOption(help);
        options.addOption(myOption);

        CommandLineParser cli = new DefaultParser();
        CommandLine line = null;
        try {
            line = cli.parse(options, args);
        } catch(Exception x) {
            System.err.println(x.getMessage());
            usage(options);
        }

        if (line.hasOption("h")) {
            usage(options);
        }

        String batchId = line.getOptionValue("b");

        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            PaperPathPopulator ppp = new PaperPathPopulator();
            injector.injectMembers(ppp);

            ppp.handle(batchId);

        } finally {
            JPAInitializer.stopService();
        }
    }
}
