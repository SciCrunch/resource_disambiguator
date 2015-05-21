package org.neuinfo.resource.disambiguator.services;

import bnlpkit.util.NumberUtils;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReferenceSearchServiceManager {

    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(ReferenceSearchServiceManager.class);
    private static final ExecutorService executorService = Executors
            .newFixedThreadPool(3);

    public ReferenceSearchServiceManager() {
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    public void handle(List<BaseReferenceSearchService> searchServiceList)
            throws Exception {
        List<Registry> registryRecords = null;
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            registryRecords = DisambiguatorFinder.getAllRegistryRecords(em);
        } finally {
            Utils.closeEntityManager(em);
        }
        List<Callable<Void>> jobs = new ArrayList<Callable<Void>>(
                searchServiceList.size());

        Calendar afterDate = Utils.getStartOfMonth();
        log.info("afterDate:" + afterDate);
        for (BaseReferenceSearchService service : searchServiceList) {
            Worker worker = new Worker(registryRecords, service, afterDate);
            jobs.add(worker);
        }

        log.info("starting all " + searchServiceList.size()
                + " resource reference search services in parallel...");
        long start = System.currentTimeMillis();
        executorService.invokeAll(jobs);
        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished reference searches.");
        log.info("---------------------------------------------------");
    }

    public static class Worker implements Callable<Void> {
        List<Registry> registryRecords;
        BaseReferenceSearchService service;
        Calendar afterDate;

        public Worker(List<Registry> registryRecords,
                      BaseReferenceSearchService service, Calendar afterDate) {
            this.registryRecords = registryRecords;
            this.service = service;
            this.afterDate = afterDate;
        }

        @Override
        public Void call() throws Exception {
            try {
                service.init(registryRecords);
                service.handle(afterDate);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return null;
        }
    }// ;

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ReferenceSearchServiceManager", options);
        System.exit(1);
    }

    public static void cli(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Options options = new Options();
        options.addOption(help);
        Option excludeOption = Option.builder("e").hasArg()
                .argName("exclude publisher(s)")
                .desc("any of [springer,nature,nif] comma separated").build();
        Option delayOption = Option.builder("d").hasArg().argName("delay in msecs")
                .desc("delay in msecs before each new search (NIF only)").build();
        options.addOption(excludeOption);
        options.addOption(delayOption);
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

        long delay = -1;
        if (line.hasOption("d")) {
            delay = Long.parseLong(line.getOptionValue("d"));
        }
        String excludedStr = null;
        if (line.hasOption("e")) {
            excludedStr = line.getOptionValue("e").trim();
        }
        Set<String> allowedSet = new HashSet<String>(7);
        allowedSet.add("springer");
        allowedSet.add("nature");
        allowedSet.add("nif");

        Set<String> excludedSet = new HashSet<String>(7);
        if (excludedStr != null) {
            excludedStr = excludedStr.toLowerCase();
            if (excludedStr.indexOf(',') != -1) {
                String[] toks = excludedStr.split("\\s*,\\s*");
                for (String tok : toks) {
                    if (!allowedSet.contains(tok)) {
                        usage(options);
                    }
                    excludedSet.add(tok);
                }
            } else {
                if (!allowedSet.contains(excludedStr)) {
                    usage(options);
                }
                excludedSet.add(excludedStr);
            }
        }

        Injector injector = Guice.createInjector(new RDPersistModule());
        ReferenceSearchServiceManager manager = new ReferenceSearchServiceManager();
        List<BaseReferenceSearchService> searchServiceList = new ArrayList<BaseReferenceSearchService>(
                3);
        try {
            injector.injectMembers(manager);

            if (!excludedSet.contains("springer")) {
                SpringerReferenceSearchService springer = new SpringerReferenceSearchService();
                injector.injectMembers(springer);
                searchServiceList.add(springer);
            }

            if (!excludedSet.contains("nature")) {
                NatureReferenceSearchService nature = new NatureReferenceSearchService();
                injector.injectMembers(nature);
                searchServiceList.add(nature);
            }

            if (!excludedSet.contains("nif")) {
                NeuinfoReferenceSearchService nif = new NeuinfoReferenceSearchService();
                nif.setDelay(delay);
                if (delay > 0) {
                    log.info("set delay to " + delay + " msecs");
                }
                injector.injectMembers(nif);
                searchServiceList.add(nif);
            }
            manager.handle(searchServiceList);
        } finally {
            JPAInitializer.stopService();
            manager.shutdown();
            for(BaseReferenceSearchService s : searchServiceList) {
                s.shutdown();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
    }
}
