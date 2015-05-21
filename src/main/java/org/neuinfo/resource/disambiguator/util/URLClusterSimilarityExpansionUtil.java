package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.util.FileUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import java.io.BufferedReader;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p></p>
 * Host clusters with cos similarity based alignment to the registry content curated data
 * <p/>
 * Created by bozyurt on 3/14/14.
 */
public class URLClusterSimilarityExpansionUtil {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    Map<String, Registry> registryMap = new HashMap<String, Registry>();


    public void prepRegistryMap() throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> registryList = DisambiguatorFinder.getAllActiveRegistryRecords(em);
            for (Registry reg : registryList) {
                String url = Utils.extractUrl(reg.getUrl());
                registryMap.put(url, reg);
                registryMap.put(reg.getNifId(), reg);
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    public List<HostCluster> parse(String clusterTextFile) throws Exception {
        List<HostCluster> hcList = new ArrayList<HostCluster>();
        BufferedReader in = null;
        try {
            in = FileUtils.getBufferedReader(clusterTextFile, CharSetEncoding.UTF8);
            String line;
            HostCluster curHC = null;
            String prevLine = null;
            while ((line = in.readLine()) != null) {
                if (line.indexOf("# of unique paths:") != -1) {
                    HostCluster hc = extractHostCluster(line);
                    curHC = hc;
                    if (hc != null) {
                        hcList.add(hc);
                    }
                } else if (line.indexOf("Registry:") == 0) {
                    String regCandidateUrl = Utils.extractUrl(line.substring(9));
                    Registry reg = registryMap.get(regCandidateUrl);
                    Assertion.assertNotNull(reg);
                    if (curHC != null && curHC.nifId == null) {
                        curHC.registry = reg;
                    }
                } else if (line.indexOf("The Most similar Registry Entry:") != -1) {

                    ClusterPath cp = extractClusterPath(line, prevLine);
                    if (cp != null && curHC != null) {
                        curHC.add(cp);
                    }
                }
                prevLine = line;
            }

            for (HostCluster hc : hcList) {
                System.out.println(hc);
            }
            return hcList;
        } finally {
            Utils.close(in);
        }
    }


    public void associateUrlsWithRegistryRegardlessOfURLStatus(List<HostCluster> hcList) throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("select u.url, u.id from URLRec u where " +
                    "u.registry is  null");

            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            int assocCount = 0;
            int count = 1;
            int missingRegCount = 0;
            while (results.next()) {
                String urlStr = (String) results.get(0);
                if (!Utils.isValidURLFormat(urlStr)) {
                    continue;
                }
                Long urlId = (Long) results.get(1);
                String theNifId = null;
                for (HostCluster hc : hcList) {
                    String nifId = hc.getRegNifId(urlStr);
                    if (nifId != null) {
                        theNifId = nifId;
                        break;
                    }
                }
                if (theNifId != null) {
                    Registry reg = registryMap.get(theNifId);
                    if (reg != null) {
                        Assertion.assertNotNull(reg);
                        assocCount++;
                        saveAssociation(urlId, reg);
                    } else {
                        missingRegCount++;
                    }
                }
                if ((count % 500) == 0) {
                    System.out.println("handled so far:" + count);
                }
                count++;
            }
            System.out.printf("assocCount:%d missingRegCount:%d%n", assocCount, missingRegCount);
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public void associateUrlsWithRegistry(List<HostCluster> hcList) throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("select u.url, u.id from URLRec u, UrlStatus s where " +
                    "u.registry is  null and u.id = s.urlID and s.type <> 1 and s.type <> 2");

            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            int assocCount = 0;
            int count = 1;
            int missingRegCount = 0;
            while (results.next()) {
                String urlStr = (String) results.get(0);
                if (!Utils.isValidURLFormat(urlStr)) {
                    continue;
                }
                Long urlId = (Long) results.get(1);
                String theNifId = null;
                for (HostCluster hc : hcList) {
                    String nifId = hc.getRegNifId(urlStr);
                    if (nifId != null) {
                        theNifId = nifId;
                        break;
                    }
                }
                if (theNifId != null) {
                    Registry reg = registryMap.get(theNifId);
                    if (reg != null) {
                        Assertion.assertNotNull(reg);
                        assocCount++;
                        saveAssociation(urlId, reg);
                    } else {
                        missingRegCount++;
                    }
                }
                if ((count % 500) == 0) {
                    System.out.println("handled so far:" + count);
                }
                count++;
            }
            System.out.printf("assocCount:%d missingRegCount:%d%n", assocCount, missingRegCount);
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    void saveAssociation(Long urlId, Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            URLRec urlRec = em.find(URLRec.class, urlId);

            Assertion.assertTrue(urlRec.getRegistry() == null);
            urlRec.setRegistry(reg);

            em.merge(urlRec);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();

        } finally {
            Utils.closeEntityManager(em);
        }

    }

    public static HostCluster extractHostCluster(String line) {
        String[] toks = line.split("\\s+");
        String host = toks[0];
        Pattern p = Pattern.compile("\\*\\*([\\w\\-_]+)\\*\\*");
        Matcher matcher = p.matcher(line);
        if (matcher.find()) {
            String nifId = matcher.group(1);
            if (nifId.equalsIgnoreCase("bad")) {
                return null;
            }
            return new HostCluster(host, nifId);
        }
        if (host.trim().length() == 0) {
            return null;
        }
        return new HostCluster(host, null);
    }

    public static ClusterPath extractClusterPath(String line, String prevLine) {
        Pattern p = Pattern.compile("\\[([\\w\\-_]+)\\]");
        Matcher matcher = p.matcher(line);
        if (matcher.find()) {
            String nifId = matcher.group(1);
            return new ClusterPath(prevLine.trim(), nifId);
        }
        return null;
    }

    public static class HostCluster {
        String host;
        String nifId;
        Registry registry;
        List<ClusterPath> cpList = new LinkedList<ClusterPath>();

        public HostCluster(String host, String nifId) {
            this.host = host;
            this.nifId = nifId;
        }

        public void add(ClusterPath cp) {
            cpList.add(cp);
        }

        public String getRegNifId(String urlStr) {
            try {
                URL url = new URL(urlStr);
                String path = url.getPath();

                String hostStr = url.getHost();
                if (hostStr.startsWith("www.")) {
                    hostStr = hostStr.substring(4);
                }
                if (hostStr.equals(host)) {
                    // check
                    ClusterPath theCP = null;
                    for (ClusterPath cp : cpList) {
                        if (cp.url.equals(urlStr)) {
                            theCP = cp;
                            break;
                        }
                    }
                    if (nifId == null && registry != null) {
                        nifId = registry.getNifId();
                    }

                    if (theCP != null) {
                        if (path != null && path.length() == 0) {
                            // for specific path is found but it is same as the host, then host has precedence
                            return nifId;
                        }
                        return theCP.nifId;
                    }
                    return nifId;
                } else {
                    return null;
                }
            } catch (Exception x) {
                x.printStackTrace();
                return null;
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("HostCluster{");
            sb.append("host='").append(host).append('\'');
            if (nifId != null) {
                sb.append(", nifId='").append(nifId).append('\'');
            } else if (registry != null) {
                sb.append(", nifId='").append(registry.getNifId()).append('\'');
            }
            for (ClusterPath cp : cpList) {
                sb.append("\n\t").append(cp);
            }
            sb.append('}');
            return sb.toString();
        }
    }

    public static class ClusterPath {
        String url;
        String nifId;

        public ClusterPath(String url, String nifId) {
            this.url = url;
            this.nifId = nifId;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ClusterPath{");
            sb.append("url='").append(url).append('\'');
            sb.append(", nifId='").append(nifId).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }//;

    public static void main(String[] args) throws Exception {
        Injector injector;
        String homeDir = System.getProperty("user.home");
        String clusterTextFile = homeDir + "/Downloads/unaccounted_url_cluster_registry_alignment.txt";
        try {
            injector = Guice.createInjector(new RDPersistModule());
            URLClusterSimilarityExpansionUtil util = new URLClusterSimilarityExpansionUtil();
            injector.injectMembers(util);
            util.prepRegistryMap();

            List<HostCluster> hcList = util.parse(clusterTextFile);
            // util.associateUrlsWithRegistry(hcList);
            util.associateUrlsWithRegistryRegardlessOfURLStatus(hcList);

        } finally {
            JPAInitializer.stopService();
        }
    }

}
