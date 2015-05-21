package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.nlp.common.FrequencyTable;
import bnlpkit.util.FileUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import gnu.trove.TIntDoubleHashMap;
import org.hibernate.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import java.io.BufferedWriter;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 2/13/14.
 */
public class MiscUtils {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Pattern supPattern = Pattern.compile("suppl?", Pattern.CASE_INSENSITIVE);
    Map<String, Registry> regMap = new HashMap<String, Registry>();
    Map<String, Registry> url2RegMap = new HashMap<String, Registry>();


    /**
     * all urls
     *
     * @throws Exception
     */
    public void dumpUrlsGroupedByHost(String csvFile) throws Exception {
        List<HostCluster> hcList = new ArrayList<HostCluster>();
        int bigClusterDataCount = 0;
        EntityManager em = null;
        StatelessSession session = null;
        Map<String, List<String>> host2URLMap = new HashMap<String, List<String>>();
        LinkedHashMap<String, HostCluster> hcMap = new LinkedHashMap<String, HostCluster>();
        try {
            FrequencyTable<String> hostTypeFT = new FrequencyTable<String>();
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("select u.url from URLRec u, UrlStatus s where " +
                    "u.id = s.urlID and s.type <> 1 and s.type <> 2 and s.flags <> 4");

            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            int badUrlCount = 0;
            int count = 0;
            while (results.next()) {
                String urlStr = (String) results.get(0);
                try {
                    URL url = new URL(urlStr);
                    String path = url.getPath();

                    String host = url.getHost();
                    if (host.startsWith("www.")) {
                        host = host.substring(4);
                    }
                    HostCluster hostCluster = hcMap.get(host);
                    if (hostCluster == null) {
                        hostCluster = new HostCluster(host);
                        hcMap.put(host, hostCluster);
                    }
                    hostCluster.addPath(path, urlStr);

                    List<String> list = host2URLMap.get(host);
                    if (list == null) {
                        list = new ArrayList<String>(2);
                        host2URLMap.put(host, list);
                    }
                    list.add(urlStr);
                    String[] toks = host.split("\\.");
                    if (toks.length > 1) {
                        hostTypeFT.addValue(toks[toks.length - 1]);
                    }

                } catch (Exception x) {
                    badUrlCount++;
                }
                count++;
                if ((count % 5000) == 0) {
                    System.out.println("Handled so far:" + count);
                }
            }
            FrequencyTable<String> ft = new FrequencyTable<String>();
            int legitimateHostCount = 0;
            for (String host : host2URLMap.keySet()) {
                List<String> list = host2URLMap.get(host);
                for (int i = 0; i < list.size(); i++) {
                    ft.addValue(host);
                    legitimateHostCount++;
                }
            }
            List<Comparable<String>> sortedKeys = ft.getSortedKeys();
            List<Integer> frequencies = new ArrayList<Integer>(sortedKeys.size());
            int i = 0;
            for (Comparable<String> key : sortedKeys) {
                frequencies.add(ft.getFrequency((String) key));
            }
            Collections.sort(frequencies, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return o2 - o1;
                }
            });
            BufferedWriter out = null;
            try {
                out = FileUtils.getBufferedWriter(csvFile, CharSetEncoding.UTF8);
                for(Integer freq : frequencies) {
                    out.write(freq.toString());
                    out.newLine();
                }
                System.out.println("wrote " + csvFile);
            } finally {
                FileUtils.close(out);
            }
            System.out.println("badUrlCount:" + badUrlCount + " legitimateHostCount:" + legitimateHostCount);
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public void getUrlsGroupedByHost(TermVectorUtils tvu) throws Exception {
        List<HostCluster> hcList = new ArrayList<HostCluster>();
        int bigClusterDataCount = 0;
        EntityManager em = null;
        StatelessSession session = null;
        Map<String, List<String>> host2URLMap = new HashMap<String, List<String>>();
        LinkedHashMap<String, HostCluster> hcMap = new LinkedHashMap<String, HostCluster>();
        try {
            FrequencyTable<String> hostTypeFT = new FrequencyTable<String>();
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();

            final List<Registry> activeRegistryRecords = DisambiguatorFinder.getAllActiveRegistryRecords(em);
            for (Registry reg : activeRegistryRecords) {
                String urlStr = null;
                try {
                    urlStr = reg.getUrl();
                    urlStr = Utils.extractUrl(urlStr);
                    URL url = new URL(urlStr);
                    String host = url.getHost().toLowerCase();
                    regMap.put(host, reg);

                    url2RegMap.put(urlStr, reg);
                } catch (Exception x) {
                    System.err.println("urlStr:" + urlStr);
                    //  x.printStackTrace();
                    // ignore
                }
            }

            //Query q = session.createQuery("select u.url from URLRec u where u.description is null");
            // Query q = session.createQuery("select u.url from URLRec u where u.registry is not null");
            Query q = session.createQuery("select u.url from URLRec u, UrlStatus s where " +
                    "u.registry is  null and u.id = s.urlID and s.type <> 1 and s.type <> 2");

            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            int badUrlCount = 0;
            int count = 0;
            while (results.next()) {
                String urlStr = (String) results.get(0);
                try {
                    URL url = new URL(urlStr);
                    String path = url.getPath();

                    String host = url.getHost();
                    if (host.startsWith("www.")) {
                        host = host.substring(4);
                    }
                    HostCluster hostCluster = hcMap.get(host);
                    if (hostCluster == null) {
                        hostCluster = new HostCluster(host);
                        hcMap.put(host, hostCluster);
                    }
                    hostCluster.addPath(path, urlStr);

                    List<String> list = host2URLMap.get(host);
                    if (list == null) {
                        list = new ArrayList<String>(2);
                        host2URLMap.put(host, list);
                    }
                    list.add(urlStr);
                    String[] toks = host.split("\\.");
                    if (toks.length > 1) {
                        hostTypeFT.addValue(toks[toks.length - 1]);
                    }

                } catch (Exception x) {
                    badUrlCount++;
                }
                count++;
                if ((count % 5000) == 0) {
                    System.out.println("Handled so far:" + count);
                }
            }

            FrequencyTable<String> ft = new FrequencyTable<String>();
            int legitimateHostCount = 0;
            for (String host : host2URLMap.keySet()) {
                List<String> list = host2URLMap.get(host);
                if (list.size() > 10) {
                    // System.out.println("host:" + host + " URL count:" + list.size());
                    for (int i = 0; i < list.size(); i++) {
                        ft.addValue(host);
                    }
                } else {
                    legitimateHostCount++;
                }
            }
            System.out.println("badUrlCount:" + badUrlCount + " legitimateHostCount:" + legitimateHostCount);

            //  ft.dumpSortedByFreq();

            /*
            for (String host : host2URLMap.keySet()) {
                List<String> list = host2URLMap.get(host);
                if (list.size() >= 100) {
                    System.out.println("host:" + host + " URL count:" + list.size());
                    for(String url : list) {
                        System.out.println("\t" + url);
                    }
                }
            }
            */
            System.out.println("=======================================");
            //hostTypeFT.dumpSortedByFreq();

            List<FrequencyInfo<HostCluster>> fList = new ArrayList<FrequencyInfo<HostCluster>>(hcMap.size());
            for (HostCluster hc : hcMap.values()) {
                fList.add(new FrequencyInfo<HostCluster>(hc, hc.getSize()));
            }
            Collections.sort(fList, new Comparator<FrequencyInfo<HostCluster>>() {
                @Override
                public int compare(FrequencyInfo<HostCluster> o1, FrequencyInfo<HostCluster> o2) {
                    return o2.count - o1.count;
                }
            });

            JSONObject root = new JSONObject();
            root.put("name", "Host Clusters");
            JSONArray jsarr = new JSONArray();
            root.put("children", jsarr);

            Connection con = null;
            try {
                File cacheRoot = new File("/var/burak/rd_hc_cache");
                String sqliteDBFile = new File(cacheRoot, "host_cluster.db").getAbsolutePath();
                Class.forName("org.sqlite.JDBC").newInstance();
                String dbURL = "jdbc:sqlite:" + sqliteDBFile;
                con = DriverManager.getConnection(dbURL);
                for (FrequencyInfo<HostCluster> fi : fList) {
                    HostCluster hc = fi.data;
                    if (hc.getSize() >= 100) {
                        hcList.add(hc);

                        final Registry theReg = matches(hc.host);
                        if (theReg != null) {
                            //  System.out.println("Registry:" + theReg.getResourceName() + " (" + theReg.getUrl() + ")");
                            //  System.out.println("-----------------------------------------------");
                        }

                        //  hc.dump();
                        hc.showClusters(con, url2RegMap, theReg);
                        jsarr.put(hc.toJSON());
                        bigClusterDataCount += hc.getSize();
                    }
                }
            } finally {
                close(con);
            }

            //   hostTypeFT.dumpSortedByFreq();

            //  System.out.println(root.toString(2));

            System.out.println("bigClusterDataCount:" + bigClusterDataCount);


        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }

        // UrlIndexer indexer = new UrlIndexer(new File("/var/burak/rd_hc_cache"), tvu);

        //  indexer.download(hcList);
        // List<TermVectorUtils.DocVector> registryDocVectors = indexer.getRegistryDocVectors();

        //indexer.findClusters(hcList, registryDocVectors);
    }


    public Registry matches(String host) {
        host = host.toLowerCase();
        Registry reg = regMap.get(host);
        if (reg != null) {
            return reg;
        }
        int minLengthDiff = Integer.MAX_VALUE;
        Registry theReg = null;
        for (String hostKey : regMap.keySet()) {
            if (hostKey.indexOf(host) != -1 || host.indexOf(hostKey) != -1) {
                int lengthDiff = Math.abs(hostKey.length() - host.length());
                if (lengthDiff < minLengthDiff) {
                    theReg = regMap.get(hostKey);
                    minLengthDiff = lengthDiff;
                }
            }
        }
        return theReg;
    }

    public static class HostCluster {
        String host;
        Map<String, HostPathCluster> map = new HashMap<String, HostPathCluster>(7);
        List<String> suppURLs = new LinkedList<String>();

        public HostCluster(String host) {
            this.host = host;
        }

        public int getSize() {
            int count = 0;
            for (HostPathCluster hpc : map.values()) {
                count += hpc.urlList.size();
            }
            return count;
        }

        public void addPath(String path, String url) {
            HostPathCluster hpc = map.get(path);
            if (hpc == null) {
                hpc = new HostPathCluster(path);
                map.put(path, hpc);
            }
            hpc.addURL(url);
            Matcher m = supPattern.matcher(url);
            if (m.find()) {
                suppURLs.add(url);
            }
        }


        public HostPathCluster getHostPath(String hostPath) {
            return map.get(hostPath);
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject js = new JSONObject();
            js.put("name", host);
            js.put("size", getSize());
            js.put("uniqPaths", map.size());
            JSONArray children = new JSONArray();
            js.put("children", children);
            List<FrequencyInfo<HostPathCluster>> flist = new ArrayList<FrequencyInfo<HostPathCluster>>(map.size());
            for (HostPathCluster hpc : map.values()) {
                flist.add(new FrequencyInfo<HostPathCluster>(hpc, hpc.urlList.size()));
            }
            Collections.sort(flist);
            int count = 0;

            for (FrequencyInfo<HostPathCluster> fi : flist) {
                HostPathCluster hpc = fi.data;
                JSONObject json = new JSONObject();
                children.put(json);
                json.put("path", hpc.hostPath);
                json.put("size", hpc.urlList.size());
                count++;
                if (count > 10) {
                    break;
                }
            }

            return js;
        }

        public List<FrequencyInfo<HostPathCluster>> getPathClustersSortedBySize() {
            List<FrequencyInfo<HostPathCluster>> flist = new ArrayList<FrequencyInfo<HostPathCluster>>(map.size());
            for (HostPathCluster hpc : map.values()) {
                flist.add(new FrequencyInfo<HostPathCluster>(hpc, hpc.urlList.size()));
            }
            Collections.sort(flist);
            return flist;
        }

        public void showClusters(Connection con, Map<String, Registry> url2RegMap, Registry theReg) throws Exception {
            System.out.println();
            System.out.println("==========================================================");
            System.out.println(host + " (" + getSize() + ") # of unique paths:" + map.size());
            if (!this.suppURLs.isEmpty()) {
                System.out.println(">> # of supplemental URLs:" + suppURLs.size());
            }
            System.out.println("-------------------------------");
            if (theReg != null) {
                System.out.println("Registry:" + theReg.getResourceName() + " (" + theReg.getUrl() + ")");
                System.out.println("-----------------------------------------------");
            }
            List<FrequencyInfo<HostPathCluster>> flist = getPathClustersSortedBySize();
            int count = 0;
            for (FrequencyInfo<HostPathCluster> fi : flist) {
                HostPathCluster hpc = fi.data;
                Map<String, CandidateRegistry> crMap = new HashMap<String, CandidateRegistry>();
                for (String url : hpc.urlList) {
                    HostClustSim hcSim = UrlIndexer.findHostClustSimRec(con, url);
                    if (hcSim != null) {
                        CandidateRegistry cr = crMap.get(hcSim.closestRegistryUrl);
                        if (cr == null) {
                            cr = new CandidateRegistry(hcSim.closestRegistryUrl);
                            crMap.put(hcSim.closestRegistryUrl, cr);
                        }
                        cr.update(hcSim.sim);
                    }
                }
                List<CandidateRegistry> crList = new ArrayList<CandidateRegistry>(crMap.values());

                Collections.sort(crList);
                CandidateRegistry theCR = null;
                if (!crList.isEmpty()) {
                    theCR = crList.get(0);
                    //  System.out.println(theCR);
                }
                System.out.println("\t" + hpc.hostPath + " (" + hpc.urlList.size() + ")");
                System.out.println("\t" + hpc.urlList.get(0));
                if (theCR != null && theCR.maxSim >= 0.8) {
                    Registry reg = url2RegMap.get(theCR.closestRegistryUrl);
                    if (reg == null) {
                        // System.err.println(">>" + theCR.closestRegistryUrl);
                    } else {
                        System.out.println("\tThe Most similar Registry Entry:" +
                                reg.getResourceName() + " [" + reg.getNifId() + "] sim="
                                + theCR.maxSim + " url:" + reg.getUrl());
                    }
                }
                count++;
                if (count > 10) {
                    break;
                }
            }

        }

        public void dump() {
            System.out.println(host + " (" + getSize() + ") # of unique paths:" + map.size());
            if (!this.suppURLs.isEmpty()) {
                System.out.println(">> # of supplemental URLs:" + suppURLs.size());
            }
            System.out.println("-------------------------------");

            List<FrequencyInfo<HostPathCluster>> flist = new ArrayList<FrequencyInfo<HostPathCluster>>(map.size());
            for (HostPathCluster hpc : map.values()) {
                flist.add(new FrequencyInfo<HostPathCluster>(hpc, hpc.urlList.size()));
            }
            Collections.sort(flist);
            int count = 0;

            for (FrequencyInfo<HostPathCluster> fi : flist) {
                HostPathCluster hpc = fi.data;
                System.out.println("\t" + hpc.hostPath + " (" + hpc.urlList.size() + ")");
                System.out.println("\t" + hpc.urlList.get(0));
                count++;
                if (count > 10) {
                    break;
                }
            }
            System.out.println("-------------------------------\n");
        }
    }

    public static class CandidateRegistry implements Comparable<CandidateRegistry> {
        String closestRegistryUrl;
        double maxSim = 0;
        double minSim = 10;
        int count = 0;
        double sum = 0;

        public CandidateRegistry(String closestRegistryUrl) {
            this.closestRegistryUrl = closestRegistryUrl;
        }

        void update(double sim) {
            if (sim > maxSim) {
                maxSim = sim;
            }
            if (sim < minSim) {
                minSim = sim;
            }
            sum += sim;
            count++;
        }


        @Override
        public int compareTo(CandidateRegistry o) {
            return o.count - count;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("CandidateRegistry{");
            sb.append("closestRegistryUrl='").append(closestRegistryUrl).append('\'');
            sb.append(", maxSim=").append(maxSim);
            sb.append(", minSim=").append(minSim);
            sb.append(", count=").append(count);
            sb.append(", sum=").append(sum);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class HostClustSim {
        String url;
        String closestRegistryUrl;
        double sim;

        public HostClustSim(String url, String closestRegistryUrl, double sim) {
            this.url = url;
            this.closestRegistryUrl = closestRegistryUrl;
            this.sim = sim;
        }
    }

    public static class UrlIndexer {
        File cacheRoot;
        TermVectorUtils tvu;

        public UrlIndexer(File cacheRoot, TermVectorUtils tvu) {
            this.cacheRoot = cacheRoot;
            this.tvu = tvu;
        }

        public List<TermVectorUtils.DocVector> getRegistryDocVectors() {
            final List<TermVectorUtils.DocVector> docVectors = tvu.prepVocabularyAndDocVectors(null);
            return docVectors;
        }

        String getCachePathFromDB(Connection con, String url) throws SQLException {
            PreparedStatement pst = null;
            try {
                pst = con.prepareStatement("select cache_path from hc_cache where url = ?");
                pst.setString(1, url);
                ResultSet rs = pst.executeQuery();
                String cachePath = null;
                if (rs.next()) {
                    cachePath = rs.getString(1);
                }
                rs.close();
                return cachePath;
            } finally {
                close(pst);
            }
        }

        public static void insertSimRecord(Connection con, String url, String closestRegistryUrl, double similarity) throws SQLException {
            PreparedStatement pst = null;
            try {
                pst = con.prepareStatement(
                        "insert into hc_sim (url, closest_reg_url, similarity) values(?,?,?)");
                pst.setString(1, url);
                pst.setString(2, closestRegistryUrl);
                pst.setDouble(3, similarity);
                pst.execute();
            } finally {
                close(pst);
            }
        }

        public static HostClustSim findHostClustSimRec(Connection con, String url) throws SQLException {
            PreparedStatement pst = null;
            try {
                pst = con.prepareStatement("select url,closest_reg_url, similarity  from hc_sim where url = ?");
                pst.setString(1, url);
                ResultSet rs = pst.executeQuery();
                HostClustSim hcs = null;
                if (rs.next()) {
                    hcs = new HostClustSim(rs.getString(1), rs.getString(2), rs.getDouble(3));
                }
                rs.close();
                return hcs;
            } finally {
                close(pst);
            }
        }

        public void findClusters(List<HostCluster> hcList, List<TermVectorUtils.DocVector> registryVectors) throws Exception {
            Connection con;
            String sqliteDBFile = new File(cacheRoot, "host_cluster.db").getAbsolutePath();
            Class.forName("org.sqlite.JDBC").newInstance();
            String dbURL = "jdbc:sqlite:" + sqliteDBFile;
            con = DriverManager.getConnection(dbURL);
            try {
                Set<String> seenUrlSet = new HashSet<String>();

                int maxTotal = 100;
                for (HostCluster hc : hcList) {
                    int totCount = 0;
                    List<FrequencyInfo<HostPathCluster>> fiList = hc.getPathClustersSortedBySize();
                    int maxSize = (int) Math.round(Math.max(maxTotal / (double) fiList.size(), 1.0));
                    for (FrequencyInfo<HostPathCluster> fi : fiList) {
                        HostPathCluster hpc = fi.data;
                        List<String> urlList = filterUrlList(hpc.urlList, 5);

                        int count = 0;
                        for (String url : urlList) {
                            // only up to maxTotal unique urls per host
                            if (count > maxSize || totCount > maxTotal) {
                                continue;
                            }
                            HostClustSim hcsim = findHostClustSimRec(con, url);
                            if (hcsim != null) {
                                if (!seenUrlSet.contains(url)) {
                                    seenUrlSet.add(url);
                                    count++;
                                    totCount++;
                                }
                                continue;
                            }

                            if (!seenUrlSet.contains(url)) {
                                seenUrlSet.add(url);
                                String cachePath = getCachePathFromDB(con, url);
                                if (cachePath != null) {
                                    String content = FileUtils.loadAsString(cachePath, CharSetEncoding.UTF8);
                                    TermVectorUtils.DocVector docVector = tvu.prepDocVector(url, content);
                                    TermVectorUtils.DocVector closestRV = null;
                                    double max = 0;
                                    for (TermVectorUtils.DocVector registryVec : registryVectors) {
                                        double cosSim = calcCosSim(registryVec, docVector, registryVectors.size());
                                        if (cosSim > max) {
                                            max = cosSim;
                                            closestRV = registryVec;
                                        }
                                    }
                                    System.out.println("url:" + url);
                                    if (closestRV != null) {
                                        System.out.println("closest Registry:" + closestRV.url + " (" + max + ")");
                                        insertSimRecord(con, url, closestRV.url, max);
                                    }

                                    count++;
                                    totCount++;
                                }
                            }
                        }
                    }
                }
            } finally {
                close(con);
            }

        }

        public double calcCosSim(TermVectorUtils.DocVector dv1, TermVectorUtils.DocVector dv2, int docSize) {
            TIntDoubleHashMap tv1 = TermVectorUtils.toTFIDFVector(dv1, tvu.getTermId2DocCountMap(), docSize);
            TIntDoubleHashMap tv2 = TermVectorUtils.toTFIDFVector(dv2, tvu.getTermId2DocCountMap(), docSize);
            double cosSim = TermVectorUtils.calcCosSim(tv1, tv2);
            return cosSim;
        }

        public List<String> filterUrlList(List<String> urlList, int maxQueryCount) {
            List<String> list = new ArrayList<String>(urlList.size());
            int count = 0;
            for (String url : urlList) {
                if (Utils.isValidURLFormat(url)) {
                    try {
                        URL theURL = new URL(url);
                        if (theURL.getQuery() != null && count < maxQueryCount) {
                            list.add(url);
                        } else if (theURL.getQuery() == null) {
                            list.add(url);
                        }
                        count++;

                    } catch (MalformedURLException e) {
                        // ignore
                    }
                }
            }
            return list;
        }

        public void download(List<HostCluster> hcList) throws Exception {
            Connection con = null;
            String sqliteDBFile = new File(cacheRoot, "host_cluster.db").getAbsolutePath();
            Class.forName("org.sqlite.JDBC").newInstance();
            String dbURL = "jdbc:sqlite:" + sqliteDBFile;
            con = DriverManager.getConnection(dbURL);
            Statement st = null;
            PreparedStatement pst = null;
            try {
                /*
                st = con.createStatement();
                st.setQueryTimeout(10);
                st.executeUpdate("drop table if exists hc_cache");
                System.out.println("---------------------");
                st.executeUpdate("create table hc_cache (url text primary key, " +
                        "cache_path text not null)");
                st.close();
                */
                Set<String> seenUrlSet = new HashSet<String>();
                for (HostCluster hc : hcList) {
                    List<FrequencyInfo<HostPathCluster>> fiList = hc.getPathClustersSortedBySize();
                    int count = 0;
                    for (FrequencyInfo<HostPathCluster> fi : fiList) {
                        HostPathCluster hpc = fi.data;
                        List<String> urlList = filterUrlList(hpc.urlList, 5);
                        for (String url : urlList) {
                            if (!seenUrlSet.contains(url)) {
                                seenUrlSet.add(url);
                                String cp = getCachePathFromDB(con, url);
                                if (cp == null) {
                                    URL theURL = new URL(url);


                                    File cachePath = downloadURL(theURL);
                                    System.out.println("downloaded " + url);
                                    if (cachePath != null) {
                                        try {
                                            pst = con.prepareStatement("insert into hc_cache (url,cache_path) values(?,?)");
                                            pst.setString(1, url);
                                            pst.setString(2, cachePath.getAbsolutePath());

                                            pst.execute();
                                        } finally {
                                            close(pst);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                close(con);
            }
        }

        public File downloadURL(URL url) {
            String urlStr = url.toString();
            URLValidator validator = new URLValidator(urlStr,
                    OpMode.FULL_CONTENT);

            try {
                URLContent urlContent = validator.checkValidity(false);
                if (urlContent != null) {
                    String content = urlContent.getContent();
                    File cacheFile = new File(cacheRoot, Utils.toFileName(urlStr) + ".txt");
                    FileUtils.saveText(content, cacheFile.getAbsolutePath(), CharSetEncoding.UTF8);
                    return cacheFile;
                }
            } catch (Exception x) {
                x.printStackTrace();
            }
            return null;
        }


    }

    static void close(PreparedStatement pst) {
        if (pst != null) {
            try {
                pst.close();
            } catch (Exception x) {
            }
        }
    }

    public static void close(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (Exception x) {
            }
        }
    }

    public static class FrequencyInfo<T> implements Comparable<FrequencyInfo<T>> {
        T data;
        int count;

        public FrequencyInfo(T data, int count) {
            this.data = data;
            this.count = count;
        }

        @Override
        public int compareTo(FrequencyInfo<T> o) {
            return o.count - count;
        }
    }


    public static class HostPathCluster {
        String hostPath;
        List<String> urlList = new LinkedList<String>();

        public HostPathCluster(String hostPath) {
            this.hostPath = hostPath;
        }

        public void addURL(String url) {
            urlList.add(url);
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            MiscUtils mu = new MiscUtils();
            injector.injectMembers(mu);
            TermVectorUtils tvu = new TermVectorUtils();
            injector.injectMembers(tvu);

            // mu.getUrlsGroupedByHost(tvu);

            mu.dumpUrlsGroupedByHost("/tmp/url_freqs.csv");


        } finally {
            JPAInitializer.stopService();
        }
    }
}
