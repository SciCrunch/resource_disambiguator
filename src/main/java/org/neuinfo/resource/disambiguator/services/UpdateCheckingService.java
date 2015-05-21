package org.neuinfo.resource.disambiguator.services;

import bnlpkit.util.GenUtils;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.model.RegistryUpdateStatus;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.DocSimilarityUtils;
import org.neuinfo.resource.disambiguator.util.HtmlContentExtractor;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author bozyurt
 */
public class UpdateCheckingService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(UpdateCheckingService.class);
    private final static Pattern yearPattern = Pattern.compile("(20\\d\\d)");
    private static final ExecutorService executorService = Executors
            .newFixedThreadPool(10);


    public void fixBadUpdateYears() {
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        List<Registry> registryList = getRegistryList();
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<RegistryUpdateStatus> query = em.createQuery("from RegistryUpdateStatus r where r.updateYear is not null", RegistryUpdateStatus.class);
            List<RegistryUpdateStatus> resultList = query.getResultList();

            Pattern p = Pattern.compile("updated?.+(20\\d\\d)", Pattern.CASE_INSENSITIVE);
            Pattern rp = Pattern.compile("20\\d\\d\\s*-\\s*(20\\d\\d)");
            // Utils.beginTransaction(em);
            for (RegistryUpdateStatus rus : resultList) {
                int rusUpdateYear = Integer.parseInt(rus.getUpdateYear());
                String updateLine = rus.getUpdateLine();
                Matcher m1 = p.matcher(updateLine);
                int updateYear = -1;
                if (m1.find()) {
                    int year = Integer.parseInt(m1.group(1));
                    if (year > 0 && rusUpdateYear != year) {
                        System.out.println("year:" + year + " rusUpdateYear:" + rusUpdateYear);
                         if (year > updateYear && year <= thisYear) {
                             updateYear = year;
                         }

                    }
                }

                Matcher matcher = rp.matcher(updateLine);
                if (matcher.find()) {
                    int year = Integer.parseInt(matcher.group(1));
                    if (year > updateYear && year <= thisYear) {
                        updateYear = year;
                    }
                    System.out.println(GenUtils.formatText(updateLine, 100));
                }
                if (updateYear > 0) {
                    System.out.println("updateYear:" + updateYear);

                    rus.setUpdateYear(String.valueOf(updateYear));
                    updateUpdateStatus(rus);
                } else {
                    System.out.println("rusUpdateYear:" + rusUpdateYear);
                }
                System.out.println("===================================");


            }
            // Utils.commitTransaction(em);
        } catch (Exception x) {
            log.error(x.getMessage());
            // Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void handle() throws Exception {
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        List<Registry> registryList = getRegistryList();
        List<Callable<Void>> jobs = new ArrayList<Callable<Void>>(100);
        long start = System.currentTimeMillis();
        int count = 1;
        for (Registry registry : registryList) {
            Worker worker = new Worker(this, registry, thisYear);
            jobs.add(worker);
            //handleRegistry(thisYear, registry);
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
        log.info("Finished registry update checking.");
        log.info("---------------------------------------------------");
    }

    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    void handleRegistry(int thisYear, Registry registry) {
        String urlStr = registry.getUrl();
        urlStr = Utils.extractUrl(urlStr);
        //urlStr = Utils.normalizeUrl(urlStr);
        try {
            URL url = new URL(urlStr);
            HtmlContentExtractor hce = new HtmlContentExtractor(url);
            hce.extractContent();
            String content = hce.getContent();
            if (content == null) {
                content = "";
            }
            String[] lines = content.split("\\.");
            String updateLine = null;
            int updateYear = -1;
            for (String line : lines) {
                if (line.toLowerCase().indexOf("update") != -1) {
                    if (line.indexOf("20") != -1) {
                        Matcher matcher = yearPattern.matcher(line);
                        if (matcher.find()) {
                            String yearStr = matcher.group(1);
                            int year = Integer.parseInt(yearStr);
                            if (year > updateYear && year <= thisYear) {
                                updateYear = year;
                                line = line.replaceAll("[\\r\\n]", "");
                                line = line.replaceAll("\\s+", " ");
                                updateLine = line;
                            }
                        }
                    }
                }
            }
            RegistryUpdateStatus theRus = null;
            if (updateLine != null) {
                RegistryUpdateStatus rus = new RegistryUpdateStatus();
                rus.setRegistry(registry);
                rus.setUpdateLine(updateLine);
                rus.setUpdateYear(String.valueOf(updateYear));
                RegistryUpdateStatus rus2 = check4ContentChanges(content, registry, rus);
                if (rus2 != null) {
                    theRus = rus2;
                } else {
                    theRus = rus;
                }
            } else {
                RegistryUpdateStatus rus = check4ContentChanges(content, registry, null);
                if (rus != null) {
                    theRus = rus;
                }
            }
            if (theRus != null) {
                saveUpdateStatus(theRus);
            }

        } catch (Exception x) {
            log.error("handleRegistry", x);
        }
    }


    protected RegistryUpdateStatus check4ContentChanges(String content, Registry registry,
                                                        RegistryUpdateStatus rus) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            RegistrySiteContent rsc = DisambiguatorFinder.getRegistrySiteContent(em, registry,
                    RegistrySiteContent.ORIGINAL);
            if (rsc == null) {
                return null;
            }
            String origContent = rsc.getContent();

            if (origContent == null || origContent.trim().length() == 0) {
                return null;
            }
            origContent = origContent.trim();
            Set<String> origSet = DocSimilarityUtils.prepShingles(origContent, 5);
            Set<String> curSet = DocSimilarityUtils.prepShingles(content, 5);

            double jc = DocSimilarityUtils.calcJaccardIndex(origSet, curSet);
            double containment1 = DocSimilarityUtils.calcContainment(curSet, origSet);
            double containment2 = DocSimilarityUtils.calcContainment(origSet, curSet);
            double containment = Math.max(containment1, containment2);
            if (rus == null) {
                rus = new RegistryUpdateStatus();
                rus.setRegistry(registry);
            }

            rus.setBatchId(Utils.prepBatchId());
            rus.setContainment(containment);
            rus.setSimilarity(jc);

            System.out.println("containment:" + containment + " jc:" + jc);

            addOrUpdateLatestContentRec(em, registry, content);

            return rus;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected void addOrUpdateLatestContentRec(EntityManager em, Registry reg, String content) {
        try {
            Utils.beginTransaction(em);
            List<RegistrySiteContent> contentList = DisambiguatorFinder.findMatchingSiteContent(em, reg.getId());
            RegistrySiteContent rsc = findRegistrySiteContent(contentList, RegistrySiteContent.LATEST);
            if (rsc == null) {
                rsc = new RegistrySiteContent();
                rsc.setRegistry(reg);
                rsc.setContent(content);
                rsc.setTitle(null);
                rsc.setFlags(RegistrySiteContent.LATEST);

                em.persist(rsc);
            } else {
                rsc.setContent(content);
                rsc.setLastModifiedTime(Calendar.getInstance());
                em.merge(rsc);
            }


            Utils.commitTransaction(em);
        } catch (Throwable t) {
            Utils.rollbackTransaction(em);
            log.error(t.getMessage());
        }
    }

    public static RegistrySiteContent findRegistrySiteContent(
            List<RegistrySiteContent> contentList,
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

    protected void saveUpdateStatus(RegistryUpdateStatus rus)
            throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            em.persist(rus);

            Utils.commitTransaction(em);
        } catch (Exception x) {
            log.error(x.getMessage());
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected void updateUpdateStatus(RegistryUpdateStatus rus)
            throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            em.merge(rus);

            Utils.commitTransaction(em);
        } catch (Exception x) {
            log.error(x.getMessage());
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected void saveUpdateStatusList(List<RegistryUpdateStatus> rusList)
            throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            for (RegistryUpdateStatus rus : rusList) {
                em.persist(rus);
            }

            Utils.commitTransaction(em);
        } catch (Exception x) {
            log.error(x.getMessage());
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected List<Registry> getRegistryList() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            return DisambiguatorFinder.getAllActiveRegistryRecords(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static class Worker implements Callable<Void> {
        Registry reg;
        UpdateCheckingService service;
        int thisYear;

        public Worker(UpdateCheckingService service, Registry reg, int thisYear) {
            this.service = service;
            this.reg = reg;
            this.thisYear = thisYear;
        }

        @Override
        public Void call() throws Exception {
            service.handleRegistry(thisYear, reg);
            return null;
        }
    }


    public static void main(String[] args) throws Exception {
        Injector injector;
        UpdateCheckingService service = null;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            service = new UpdateCheckingService();
            injector.injectMembers(service);

            //  service.handle();
            service.fixBadUpdateYears();
        } finally {
            JPAInitializer.stopService();
            if (service != null) {
                service.shutdown();
            }
        }
    }

}
