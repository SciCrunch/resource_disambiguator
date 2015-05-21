package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import commonlib.CSVParser;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.model.UrlStatus;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 2/18/14.
 */
public class URLMentionExpansionUtil {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static final Pattern supPattern = Pattern.compile("suppl?", Pattern.CASE_INSENSITIVE);


    public void handleJournalsAndPublishers(String csvFile) throws Exception {
        Map<String, String> jpHostsMap = new HashMap<String, String>();
        CSVParser parser = new CSVParser();
        parser.extractData(csvFile);
        List<List<String>> rows = parser.getRows();
        for (List<String> row : rows) {
            String host = row.get(0);
            String type = row.get(2);
            if (type.equalsIgnoreCase("journal") || type.equalsIgnoreCase("publisher")
                    || type.equalsIgnoreCase("institution")) {
                System.out.println("host:" + host + " type:" + type);
                jpHostsMap.put(host, type.toLowerCase());
                String lcHost = host.toLowerCase();
                if (!jpHostsMap.containsKey(lcHost)) {
                    jpHostsMap.put(lcHost, type.toLowerCase());
                }
            }
        }
        parser = null;
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("select u.url, u.id from URLRec u where u.registry is null");
            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            int count = 0;
            int supCount = 0;
            while (results.next()) {
                String urlStr = (String) results.get(0);
                Integer urlId = (Integer) results.get(1);
                try {
                    URL url = new URL(urlStr);
                    String host = url.getHost();
                    boolean found = jpHostsMap.containsKey(host);
                    if (!found) {
                        host = normalizeUrl(host);
                        found = jpHostsMap.containsKey(host);
                    }
                    if (found) {
                        String type = jpHostsMap.get(host);
                        System.out.println(">> " + type + ": " + host + " url:" + urlStr);
                        Matcher m = supPattern.matcher(urlStr);
                        if (m.find()) {
                            supCount++;
                            setTypeForUrl(urlId, "suppl");
                        } else {
                            setTypeForUrl(urlId, type);
                        }

                        count++;
                    } else if (host.indexOf("biomedcentral") != -1) {
                        System.out.println(">> not Journal/Publisher: " + host + " url:" + urlStr);
                    }

                } catch (Exception x) {
                    // ignore
                }
            }
            System.out.println("# journal/publisher urls:" + count);
            System.out.println("# supplement urls:" + supCount);

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public void handle(String csvFile) throws Exception {
        Set<String> resourceHosts = new HashSet<String>();
        CSVParser parser = new CSVParser();
        parser.extractData(csvFile);
        List<List<String>> rows = parser.getRows();
        for (List<String> row : rows) {
            String host = row.get(0);
            String type = row.get(2);
            if (type.equalsIgnoreCase("resource")) {
                System.out.println("host:" + host + " type:" + type);
                resourceHosts.add(host);
            }
        }
        parser = null;
        EntityManager em = null;
        StatelessSession session = null;
        Map<String, Registry> seenRegMap = new HashMap<String, Registry>();
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> allRegistryRecords = DisambiguatorFinder.getAllRegistryRecords(em);
            for (Registry reg : allRegistryRecords) {
                String url = reg.getUrl();
                if (url == null) {
                    System.err.println("Resource " + reg.getResourceName() + " has no url");
                    continue;
                }
                url = url.replaceFirst("^http://", "");
                url = url.replaceFirst("/$", "");
                if (url.startsWith("www.")) {
                    url = url.substring(4);
                }
                if (resourceHosts.contains(url)) {
                    seenRegMap.put(url, reg);
                } else {
                    String altUrl = reg.getAlternativeUrl();
                    String oldUrl = reg.getOldUrl();
                    if (altUrl != null) {
                        altUrl = normalizeUrl(altUrl);
                        if (resourceHosts.contains(altUrl)) {
                            seenRegMap.put(altUrl, reg);
                        }
                    }
                    if (oldUrl != null) {
                        oldUrl = normalizeUrl(oldUrl);
                        if (resourceHosts.contains(oldUrl)) {
                            seenRegMap.put(oldUrl, reg);
                        }
                    }
                }
            }
            for (String host : resourceHosts) {
                if (!seenRegMap.containsKey(host)) {
                    System.out.println(host + " is not found in registry");
                }
            }
            System.out.println("# matched items:" + seenRegMap.size());


            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("select u.url, u.id from URLRec u where u.registry is null");
            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            int count = 0;
            while (results.next()) {
                String urlStr = (String) results.get(0);
                Long urlId = (Long) results.get(1);
                try {
                    URL url = new URL(urlStr);
                    String host = url.getHost().toLowerCase();
                    Registry registry = seenRegMap.get(host);
                    if (registry != null) {
                        System.out.println(">> adding " + host + " url:" + urlStr +
                                " registry:" + registry.getResourceName());
                        // associateUrlWithResource(urlId, registry);
                        count++;
                    }

                } catch (Exception x) {
                    // ignore
                }
            }
            System.out.println("# new url - registry associations:" + count);

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    void setTypeForUrl(int urlId, String type) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            TypedQuery<UrlStatus> query = em.createQuery("from UrlStatus u where u.urlID = :urlId", UrlStatus.class);
            List<UrlStatus> list = query.setParameter("urlId", urlId).getResultList();
            if (!list.isEmpty()) {
                UrlStatus us = list.get(0);
                if (type.equals("journal")) {
                    us.setType(UrlStatus.TYPE_JOURNAL);
                } else if (type.equals("institution")) {
                    us.setType(UrlStatus.TYPE_INSTITUTION);
                } else if (type.equals("publisher")) {
                    us.setType(UrlStatus.TYPE_PUBLISHER);
                } else if (type.equals("suppl")) {
                    us.setType(UrlStatus.TYPE_SUPPL);
                }
                em.merge(us);
            }
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }


    }

    void associateUrlWithResource(int urlId, Registry registry) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            URLRec urlRec = em.find(URLRec.class, urlId);
            if (urlRec.getRegistry() == null) {
                urlRec.setRegistry(registry);
                urlRec.setFlags(URLRec.FROM_HOST_EXPANSION);
                em.merge(urlRec);
            }
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static String normalizeUrl(String url) {
        url = url.replaceFirst("^http://", "");
        url = url.replaceFirst("/$", "");
        if (url.startsWith("www.")) {
            url = url.substring(4);
        }
        return url;
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        String homeDir = System.getProperty("user.home");
        String csvFile = homeDir + "/urlChecked_for_Burak.csv";
        try {
            injector = Guice.createInjector(new RDPersistModule());
            URLMentionExpansionUtil util = new URLMentionExpansionUtil();
            injector.injectMembers(util);

            util.handle(csvFile);

            // util.handleJournalsAndPublishers(csvFile);
        } finally {
            JPAInitializer.stopService();
        }
    }

}
