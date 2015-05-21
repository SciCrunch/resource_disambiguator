package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by bozyurt on 1/2/14.
 */
public class RegistrySiteContentPopulator {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(RegistrySiteContentPopulator.class);
    private static final ExecutorService executorService = Executors
            .newFixedThreadPool(10);


    public void shutdown() {
        executorService.shutdownNow();
    }

    public void handle(int flags, IPredicate<Registry> predicate) throws Exception {
        EntityManager em = null;
        long start = System.currentTimeMillis();
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> registryList = DisambiguatorFinder.getAllActiveRegistryRecords(em);
          /*
            List<RegistrySiteContent> erscList = DisambiguatorFinder.getEmptyContentRegistrySiteContent(em);
            Set<Integer> revisitRegSet = new HashSet<Integer>();
            for(RegistrySiteContent r : erscList) {
                revisitRegSet.add( r.getRegistry().getId());
            }
            erscList = null;
          */
            List<Callable<Void>> jobs = new ArrayList<Callable<Void>>(100);
            int count = 1;
            for (Registry reg : registryList) {
                /*
                if (!revisitRegSet.contains(reg.getId())) {
                    continue;
                }
                */
                if (predicate != null && !predicate.satisfied(reg)) {
                    continue;
                }

                Worker worker = new Worker(this, reg, flags);
                jobs.add(worker);
                if ((count % 100) == 0) {
                    log.info("# of resources handled so far is " + count);
                    log.info("processing all jobs 10 at a time");
                    executorService.invokeAll(jobs);
                    jobs.clear();
                }
                count++;
            }
            if (!jobs.isEmpty()) {
                log.info("processing all jobs 10 at a time");
                executorService.invokeAll(jobs);
            }
            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));
            log.info("Finished registry site content population");
            log.info("---------------------------------------------------");
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void handleRedirectsOnly(int flags) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> registryList = DisambiguatorFinder.getAllActiveRegistryRecords(em);
            for (Registry reg : registryList) {
                List<RegistrySiteContent> siteContents =
                        DisambiguatorFinder.findMatchingSiteContent(em, reg.getId());
                if (!siteContents.isEmpty()) {
                    RegistrySiteContent registrySiteContent = siteContents.get(0);
                    if (registrySiteContent.getRedirectUrl() != null) {
                        addOrUpdateContentRec(em, reg, flags);
                    }
                }
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void updateRedirects(boolean onlyForPreviousRedirects) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> registryList = DisambiguatorFinder.getAllActiveRegistryRecords(em);
            for (Registry reg : registryList) {
                updateRedirectUrlFromContent(reg, onlyForPreviousRedirects);
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    private void updateRedirectUrl(EntityManager em, Registry reg) {
        String urlStr = reg.getUrl();
        try {
            Utils.beginTransaction(em);
            URLValidator validator = new URLValidator(urlStr, OpMode.FULL_CONTENT);

            URLContent urlContent = validator.checkValidity(true);
            if (RedirectUtils.isAValidRedirect(urlContent.getFinalRedirectURI(), urlStr)) {
                List<RegistrySiteContent> siteContents =
                        DisambiguatorFinder.findMatchingSiteContent(em, reg.getId());
                if (!siteContents.isEmpty()) {
                    URL redirectURL = RedirectUtils.normalizeRedirectURL(
                            urlContent.getFinalRedirectURI().toString(), urlStr);
                    if (RedirectUtils.isRedirectUrlValid(redirectURL.toString())) {
                        System.out.println("redirect:" + urlContent.getFinalRedirectURI());
                        System.out.println("was " + urlStr);
                        RegistrySiteContent rsc = siteContents.get(0);
                        rsc.setRedirectUrl(urlContent.getFinalRedirectURI().toString());

                        em.merge(rsc);
                    }
                }
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            log.error(t.getMessage());
            //t.printStackTrace();
            Utils.rollbackTransaction(em);
        }
    }

    private void updateRedirectUrlFromContent(Registry reg,
                                              boolean onlyForPreviousRedirects) {

        EntityManager em = null;
        String urlStr = reg.getUrl();
        urlStr = Utils.extractUrl(urlStr);
        try {
            em = Utils.getEntityManager(emFactory);
            System.out.println("url:" + urlStr);
            Utils.beginTransaction(em);
            List<RegistrySiteContent> siteContents =
                    DisambiguatorFinder.findMatchingSiteContent(em, reg.getId());
            if (!siteContents.isEmpty()) {
                if (onlyForPreviousRedirects) {
                    RegistrySiteContent registrySiteContent = siteContents.get(0);
                    if (registrySiteContent.getRedirectUrl() == null) {
                        return;
                    }
                }

                URLValidator validator = new URLValidator(urlStr, OpMode.FULL_CONTENT);
                URLContent urlContent = validator.checkValidity(false);
                if (RedirectUtils.isAValidRedirect(urlContent.getFinalRedirectURI(), urlStr)) {
                    URL redirectURL = RedirectUtils.normalizeRedirectURL(
                            urlContent.getFinalRedirectURI().toString(), urlStr);
                    if (RedirectUtils.isRedirectUrlValid(redirectURL.toString())) {
                        urlContent = validator.checkValidity(false);
                        if (urlContent != null && !redirectURL.toString().equals(urlStr)) {
                            System.out.println("redirect:" + redirectURL);
                            System.out.println("was " + urlStr);
                            RegistrySiteContent rsc = siteContents.get(0);
                            rsc.setRedirectUrl(redirectURL.toString());
                            em.merge(rsc);
                        }
                    }
                }
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            log.error(t.getMessage());
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static RegistrySiteContent findRegistrySiteContent(List<RegistrySiteContent> contentList,
                                                              int flags) {
        RegistrySiteContent rsc = null;
        for (RegistrySiteContent r : contentList) {
            if (r.getFlags() == flags) {
                rsc = r;
                break;
            }
        }
        return rsc;
    }

    void addOrUpdateContentRec(Registry reg, int flags) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            addOrUpdateContentRec(em, reg, flags);

        } finally {
            Utils.closeEntityManager(em);
        }
    }


    private void addOrUpdateContentRec(EntityManager em, Registry reg, int flags) {
        String urlStr = reg.getUrl();
        urlStr = Utils.extractUrl(urlStr);
        try {
            Utils.beginTransaction(em);
            List<RegistrySiteContent> contentList =
                    DisambiguatorFinder.findMatchingSiteContent(em, reg.getId());
            URL url = new URL(urlStr);
            RegistrySiteContent origRSC = findRegistrySiteContent(contentList, RegistrySiteContent.ORIGINAL);
            if (origRSC != null) {
                if (origRSC.getRedirectUrl() != null) {
                    urlStr = origRSC.getRedirectUrl();
                    url = new URL(urlStr);
                    log.info("handling resource " + reg.getResourceName() + "\nurl (redirect):" + urlStr);
                } else {
                    log.info("handling resource " + reg.getResourceName() + "\nurl:" + urlStr);
                }
            } else {
                log.info("handling resource " + reg.getResourceName() + "\nurl:" + urlStr);
            }

            String content;
            String title;
            HtmlContentExtractor hce = new HtmlContentExtractor(url);
            hce.extractContent();
            content = hce.getContent();
            title = hce.getTitle();
            if (content == null) {
                log.info("No content for " + reg.getResourceName() + " urlStr:" + urlStr);
            }
            if (contentList.isEmpty()) {
                RegistrySiteContent rsc = new RegistrySiteContent();
                rsc.setRegistry(reg);
                rsc.setContent(content);
                rsc.setTitle(title);
                rsc.setFlags(flags);

                em.persist(rsc);
            } else {
                if (flags == RegistrySiteContent.LATEST) {
                    RegistrySiteContent rsc = findRegistrySiteContent(contentList, flags);
                    if (rsc == null) {
                        rsc = new RegistrySiteContent();
                        rsc.setRegistry(reg);
                        rsc.setContent(content);
                        rsc.setTitle(title);
                        rsc.setFlags(flags);

                        em.persist(rsc);
                    } else {
                        rsc.setContent(content);
                        rsc.setTitle(title);
                        rsc.setLastModifiedTime(Calendar.getInstance());
                        em.merge(rsc);
                    }

                } else {
                    RegistrySiteContent rsc = findRegistrySiteContent(contentList, flags);
                    Assertion.assertNotNull(rsc);

                    rsc.setContent(content);
                    rsc.setTitle(title);
                    rsc.setLastModifiedTime(Calendar.getInstance());
                    em.merge(rsc);
                }
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            Utils.rollbackTransaction(em);
            log.error(t.getMessage());
        }
    }

    public static class Worker implements Callable<Void> {
        Registry reg;
        int flags;
        RegistrySiteContentPopulator populator;

        public Worker(RegistrySiteContentPopulator populator, Registry reg, int flags) {
            this.populator = populator;
            this.reg = reg;
            this.flags = flags;
        }

        @Override
        public Void call() throws Exception {
            populator.addOrUpdateContentRec(reg, flags);
            return null;
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("RegistrySiteContentPopulator", options);
        System.exit(1);
    }

    public static class RegistryUrlPredicate implements IPredicate<Registry> {

        @Override
        public boolean satisfied(Registry reg) {
            String urlStr = reg.getUrl();
            String url = Utils.extractUrl(urlStr);
            return !urlStr.equals(url);
        }
    }

    public static void cli(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option cmdOption = Option.builder("c").hasArg().argName("command")
                .desc("Command one of [all,redirect,redirect-fast,redirect-content]").build();
        Options options = new Options();
        options.addOption(help);
        options.addOption(cmdOption);

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
        String cmd = "all";
        if (line.hasOption("c")) {
            cmd = line.getOptionValue("c");
        }

        Injector injector = Guice.createInjector(new RDPersistModule());

        RegistrySiteContentPopulator populator = new RegistrySiteContentPopulator();

        try {
            injector.injectMembers(populator);

            if (cmd.equalsIgnoreCase("all")) {
                populator.handle(RegistrySiteContent.ORIGINAL, null);
                populator.updateRedirects(false);
            } else if (cmd.equalsIgnoreCase("redirect-fast")) {
                populator.updateRedirects(true);
            } else if (cmd.equalsIgnoreCase("redirect")) {
                populator.updateRedirects(false);
            } else if (cmd.equalsIgnoreCase("redirect-content")) {
                populator.handleRedirectsOnly(RegistrySiteContent.ORIGINAL);
            } else if (cmd.equalsIgnoreCase("current")) {
                populator.handle(RegistrySiteContent.LATEST, null);
            }
        } finally {
            JPAInitializer.stopService();
            populator.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
    }

}
