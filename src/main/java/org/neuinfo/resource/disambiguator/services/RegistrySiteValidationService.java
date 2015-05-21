package org.neuinfo.resource.disambiguator.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.ValidationStatus;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.URLContent;
import org.neuinfo.resource.disambiguator.util.URLValidator;
import org.neuinfo.resource.disambiguator.util.Utils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;

/**
 * @author bozyurt
 */
public class RegistrySiteValidationService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(UpdateCheckingService.class);
    private static final ExecutorService executorService = Executors
            .newFixedThreadPool(10);

    public void shutdown() {
        executorService.shutdownNow();
    }

    public void handle() throws Exception {
        long start = System.currentTimeMillis();
        log.info("Starting registry site validation");
        List<Registry> registryList = getRegistryList();

        List<ValidationStatus> vsList = Collections.synchronizedList(new ArrayList<ValidationStatus>(100));
        List<Callable<Void>> jobs = new ArrayList<Callable<Void>>(100);

        int count = 1;
        for (Registry registry : registryList) {
            String urlStr = registry.getUrl();

            // FIXME check only the first URL
            urlStr = Utils.extractUrl(urlStr);
            if (urlStr == null) {
                System.err.println("No url for registry " + registry.getResourceName() +
                        " - " + registry.getUrl());
                continue;
            }

            Worker worker = new Worker(this, vsList, urlStr, registry);
            jobs.add(worker);
            /*
            log.info("checking registry site " + urlStr);
            URLValidator uv = new URLValidator(urlStr, null);

            URLContent uc;
            ValidationStatus vs = new ValidationStatus();
            vs.setRegistry(registry);


            try {
                uc = uv.checkValidity(true);
                vs.setUp(uc != null);
            } catch (Exception x) {
                vs.setUp(false);
                log.error(x.getMessage());
                if (x.getMessage() != null) {
                    int max = Math.min(254, x.getMessage().length());
                    vs.setMessage(x.getMessage().substring(0, max));
                }
            }
            */
            if ((count % 100) == 0) {
                log.info("# of resources handled so far is " + count);
                log.info("processing all jobs 10 at a time");
                executorService.invokeAll(jobs);
                for (ValidationStatus vs : vsList) {
                    saveValidateStatus(vs);
                }
                jobs.clear();
                vsList.clear();
            }

            count++;
        }
        if (!jobs.isEmpty()) {
            log.info("processing all jobs 10 at a time");
            executorService.invokeAll(jobs);
            for (ValidationStatus vs : vsList) {
                saveValidateStatus(vs);
            }
            jobs.clear();
            vsList.clear();
        }
        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished registry site validation");
        log.info("---------------------------------------------------");

    }


    protected List<Registry> getRegistryList() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            // only active registry records
            return DisambiguatorFinder.getAllActiveRegistryRecords(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected void saveValidateStatus(ValidationStatus vs) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            em.persist(vs);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            log.error(x.getMessage());
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected void saveValidateStatusList(List<ValidationStatus> vsList)
            throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            for (ValidationStatus vs : vsList) {
                em.persist(vs);
            }
            Utils.commitTransaction(em);
        } catch (Exception x) {
            log.error(x.getMessage());
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static class Worker implements Callable<Void> {
        RegistrySiteValidationService service;
        List<ValidationStatus> vsList;
        String urlStr;
        Registry registry;

        public Worker(RegistrySiteValidationService service, List<ValidationStatus> vsList,
                      String urlStr, Registry registry) {
            this.service = service;
            this.vsList = vsList;
            this.urlStr = urlStr;
            this.registry = registry;
        }

        @Override
        public Void call() throws Exception {
            log.info("checking registry site " + urlStr);
            URLValidator uv = new URLValidator(urlStr, null);

            URLContent uc;
            ValidationStatus vs = new ValidationStatus();
            vs.setRegistry(registry);
            vsList.add(vs);
            try {
                uc = uv.checkValidity(true);
                vs.setUp(uc != null);
            } catch (Exception x) {
                vs.setUp(false);
                log.error(x.getMessage());
                if (x.getMessage() != null) {
                    int max = Math.min(254, x.getMessage().length());
                    vs.setMessage(x.getMessage().substring(0, max));
                }
            }
            return null;
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("RegistrySiteValidationService", options);
        System.exit(1);
    }

    public static void cli(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Options options = new Options();
        options.addOption(help);

        CommandLineParser cli = new DefaultParser();
        CommandLine line = null;
        try {
            line = cli.parse(options, args);
        } catch (Exception x) {
            System.err.println(x.getMessage());
            usage(options);
        }

        if ((line != null) && line.hasOption("h")) {
            usage(options);
        }
        Injector injector = Guice.createInjector(new RDPersistModule());
        RegistrySiteValidationService service = new RegistrySiteValidationService();
        try {
            injector.injectMembers(service);
            service.handle();

            // also check for sites that are down
            DownSiteStatusService dss = new DownSiteStatusService();
            injector.injectMembers(dss);
            dss.handle();
        } finally {
            JPAInitializer.stopService();
            service.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
    }

}
