package org.neuinfo.resource.disambiguator.services;

import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.util.DOI2PMIDServiceClient;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by bozyurt on 4/9/14.
 */
public class PMIDService {
    private static final ExecutorService executorService = Executors
            .newFixedThreadPool(10);
    final static int MAX_RECORDS = 50000;
    final static PMIDInfo EMPTY = new PMIDInfo(null, null);
    static Logger log = Logger.getLogger(PMIDService.class);
    private Map<String, PMIDInfo> lruCache = Collections.synchronizedMap(new LinkedHashMap<String, PMIDInfo>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PMIDInfo> eldest) {
            return size() > MAX_RECORDS;
        }
    });


    public Map<String, PMIDInfo> getPMIDs(List<PMIDInfo> articles) {
        List<PMIDInfo> processedList = Collections.synchronizedList(new ArrayList<PMIDInfo>(articles.size()));

        List<Callable<Void>> jobs = new ArrayList<Callable<Void>>(articles.size());
        for (PMIDInfo pi : articles) {
            Worker worker = new Worker(pi, processedList, lruCache);
            jobs.add(worker);
        }
        try {
            long start = System.currentTimeMillis();
            executorService.invokeAll(jobs);

            Map<String, PMIDInfo> map = new HashMap<String, PMIDInfo>();
            for (PMIDInfo pi : processedList) {
                map.put(pi.getKey(), pi);
            }
            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));

            return map;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }


    public static class Worker implements Callable<Void> {
        List<PMIDInfo> processedList;
        PMIDInfo pi;
        Map<String, PMIDInfo> lruCache;

        public Worker(PMIDInfo pi, List<PMIDInfo> processedList, Map<String, PMIDInfo> lruCache) {
            this.pi = pi;
            this.processedList = processedList;
            this.lruCache = lruCache;
        }

        @Override
        public Void call() throws Exception {
            String key = pi.getKey();
            try {
                String pmid;
                PMIDInfo cachedPI = lruCache.get(key);
                if (cachedPI != null) {
                    if (cachedPI != EMPTY) {
                        pi.setPmid(cachedPI.getPmid());
                        processedList.add(pi);
                    }
                } else {
                    pmid = DOI2PMIDServiceClient.getPMID(pi.getPublicationName(), pi.getPubTitle());
                    if (pmid != null) {
                        pi.setPmid(pmid);
                        processedList.add(pi);
                        lruCache.put(key, pi);
                    } else {
                        lruCache.put(key, EMPTY);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                lruCache.put(key, EMPTY);
            }

            return null;
        }
    }

    public static class PMIDInfo {
        String publicationName;
        String pubTitle;
        String pmid;

        public PMIDInfo(String publicationName, String pubTitle) {
            this.publicationName = publicationName;
            this.pubTitle = pubTitle;
        }

        void setPmid(String pmid) {
            this.pmid = pmid;
        }

        public String getPublicationName() {
            return publicationName;
        }

        public String getPubTitle() {
            return pubTitle;
        }

        public String getPmid() {
            return pmid;
        }

        public String getKey() {
            StringBuilder sb = new StringBuilder(128);
            sb.append(pubTitle).append(':').append(publicationName);
            return sb.toString();
        }
    }
}
