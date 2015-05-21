package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.util.Executor;
import bnlpkit.util.FileUtils;
import bnlpkit.util.NumberUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import org.apache.commons.cli.*;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.model.RegistryUpdateStatus;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.util.*;

/**
 * Created by bozyurt on 3/11/14.
 */
public class LDAWrapper {
    private LDAConfig config;
    TObjectIntHashMap vocabulary = new TObjectIntHashMap();
    List<DocVector> trainingDocs = new LinkedList<DocVector>();

    private int termIdx = 0;


    public LDAWrapper(LDAConfig config) {
        this.config = config;
    }

    public DocVector addTrainingDoc(String content) {
        int docId = trainingDocs.size();
        List<String> tokens = TermVectorUtils.tokenize(content);
        DocVector dv = new DocVector(docId);
        for (String token : tokens) {
            int termId;
            if (!vocabulary.containsKey(token)) {
                termId = termIdx;
                vocabulary.put(token, termId);
                termIdx++;
            } else {
                termId = vocabulary.get(token);
            }
            dv.addTerm(termId);
        }
        this.trainingDocs.add(dv);
        return dv;
    }

    public void train(double alpha, int numTopics) throws Exception {
        StringBuilder buffer = new StringBuilder();
        Executor exec = new Executor("lda ", true);
        buffer.append("est ").append(alpha);
        buffer.append(' ').append(numTopics).append(' ');
        buffer.append(config.getSettingsFile()).append(' ');
        buffer.append(config.getTrainDataFile()).append(' ');
        buffer.append("random ");
        buffer.append(config.getOutDir());
        String args = buffer.toString();
        System.out.println("lda " + args);
        int rc = exec.execute3(args, new String[0]);
        if (rc == 0) {
            System.out.println(exec.getOutput());
        }
    }

    public void infer(String testDataFile) throws Exception {
        StringBuilder buffer = new StringBuilder();
        Executor exec = new Executor("lda ", true);
        buffer.append("inf ").append(config.getInferenceSettingsFile()).append(' ');
        buffer.append(config.getOutDir()).append("/final").append(" ");
        buffer.append(testDataFile).append(" ").append(config.getInferenceName());
        String args = buffer.toString();
        System.out.println("lda " + args);
        int rc = exec.execute3(args, new String[0]);
        if (rc == 0) {
            System.out.println(exec.getOutput());
        }
    }


    public void saveTrainingData() throws Exception {
        saveDocVectors(config.getTrainDataFile(), trainingDocs);
    }

    public static void saveDocVectors(String dataFile, List<DocVector> dvList) throws Exception {
        BufferedWriter out = null;
        try {
            out = FileUtils.getBufferedWriter(dataFile, CharSetEncoding.UTF8);
            for (DocVector dv : dvList) {
                StringBuilder sb = new StringBuilder(1024);
                sb.append(dv.getTermVec().size());
                for (TIntIntIterator it = dv.getTermVec().iterator(); it.hasNext(); ) {
                    it.advance();
                    int termId = it.key();
                    int termCount = it.value();
                    sb.append(' ').append(termId).append(':').append(termCount);
                }
                out.write(sb.toString());
                out.newLine();
            }

        } finally {
            FileUtils.close(out);
        }
    }

    public void saveVocabulary() throws Exception {
        BufferedWriter out = null;
        try {
            out = FileUtils.getBufferedWriter(config.getVocabularyFile(), CharSetEncoding.UTF8);
            for (TObjectIntIterator it = vocabulary.iterator(); it.hasNext(); ) {
                it.advance();
                out.write(it.key() + "," + it.value());
                out.newLine();
            }

        } finally {
            FileUtils.close(out);
        }
    }

    public void loadVocabulary() throws Exception {
        BufferedReader in = null;
        try {
            in = FileUtils.getBufferedReader(config.getVocabularyFile(), CharSetEncoding.UTF8);
            String line;
            while ((line = in.readLine()) != null) {
                int idx = line.lastIndexOf(",");
                String term = line.substring(0, idx);
                int termId = NumberUtils.getInt(line.substring(idx + 1));
                vocabulary.put(term, termId);
            }
        } finally {
            FileUtils.close(in);
        }
    }

    public static Map<String, double[]> getTopicDistributions(String gammaFile, Map<Integer,
            String> docId2UrlMap) throws Exception {
        Map<String, double[]> map = new HashMap<String, double[]>();
        BufferedReader in = null;
        try {
            in = FileUtils.getBufferedReader(gammaFile, CharSetEncoding.UTF8);
            String line;
            int docId = 0;
            while ((line = in.readLine()) != null) {
                String[] toks = line.split("\\s+");
                double[] dist = new double[toks.length];
                double sum = 0;
                for (int i = 0; i < toks.length; i++) {
                    dist[i] = NumberUtils.getDouble(toks[i]);
                    sum += dist[i];
                }
                if (sum > 0) {
                    for (int i = 0; i < toks.length; i++) {
                        dist[i] /= sum;
                    }
                }
                String url = docId2UrlMap.get(docId);
                if (url != null) {
                    map.put(url, dist);
                }
                docId++;
            }
            return map;
        } finally {
            FileUtils.close(in);
        }
    }

    public static Map<Integer, String> loadLT(String ltFile) throws Exception {
        Map<Integer, String> ltMap = new HashMap<Integer, String>();
        BufferedReader in = null;
        try {
            in = FileUtils.getBufferedReader(ltFile, CharSetEncoding.UTF8);
            String line;
            while ((line = in.readLine()) != null) {
                int idx = line.indexOf(":");
                int id = NumberUtils.getInt(line.substring(0, idx));
                String value = line.substring(idx + 1);
                ltMap.put(id, value);

            }
            return ltMap;
        } finally {
            FileUtils.close(in);
        }
    }

    public static void saveLT(String outFile, Map<Integer, String> ltMap) throws Exception {
        BufferedWriter out = null;
        try {
            out = FileUtils.getBufferedWriter(outFile, CharSetEncoding.UTF8);
            for (Integer key : ltMap.keySet()) {
                out.write(key + ":" + ltMap.get(key));
                out.newLine();
            }
        } finally {
            FileUtils.close(out);
        }

    }

    public DocVector getTestDocVector(int docId, String content) {
        List<String> tokens = TermVectorUtils.tokenize(content);
        DocVector dv = new DocVector(docId);
        for (String token : tokens) {
            if (vocabulary.containsKey(token)) {
                int termId = vocabulary.get(token);
                dv.addTerm(termId);
            }
        }
        return dv;
    }


    public static class DocVector {
        final int docId;
        TIntIntHashMap termVec = new TIntIntHashMap();

        public DocVector(int docId) {
            this.docId = docId;
        }

        public void addTerm(int termId) {
            if (!termVec.containsKey(termId)) {
                termVec.put(termId, 1);
            } else {
                termVec.put(termId, termVec.get(termId) + 1);
            }
        }

        public int getDocId() {
            return docId;
        }

        public TIntIntHashMap getTermVec() {
            return termVec;
        }
    }//;

    public static class RegistryContentUtil {
        @Inject
        @IndicatesPrimaryJpa
        protected Provider<EntityManager> emFactory;


        List<RegistrySiteContent> getLatest() {
            EntityManager em = null;
            try {
                em = Utils.getEntityManager(emFactory);
                TypedQuery<RegistrySiteContent> query = em.createQuery(
                        "from RegistrySiteContent r where  r.content is not null and r.flags = :flags",
                        RegistrySiteContent.class);
                return query.setParameter("flags", RegistrySiteContent.LATEST).getResultList();
            } finally {
                Utils.closeEntityManager(em);
            }
        }

        public void prepTestingData(LDAWrapper lda, boolean skipInference) throws Exception {

            List<RegistrySiteContent> rscList = getLatest();

            Map<Integer, String> docId2UrlMap = new HashMap<Integer, String>();
            int docId = 0;
            lda.loadVocabulary();

            Set<String> emptyContentUrlSet = new HashSet<String>();
            List<DocVector> dvList = new ArrayList<DocVector>(rscList.size());
            Map<String, RegistrySiteContent> rscMap = new HashMap<String, RegistrySiteContent>();
            for (RegistrySiteContent rsc : rscList) {
                String url = rsc.getRegistry().getUrl();
                url = Utils.extractUrl(url);
                rscMap.put(url, rsc);
                String content = Utils.normalizeAllWS(rsc.getContent()).trim();
                if (content != null && content.length() > 5) {
                    DocVector tdv = lda.getTestDocVector(docId, content);
                    docId2UrlMap.put(tdv.getDocId(), url);
                    dvList.add(tdv);
                    docId++;
                    if (content.length() == 0) {
                        emptyContentUrlSet.add(url);
                    }
                }
            }

            if (!skipInference) {
                String testDataFile = lda.config.getWorkDir() + "/test.dat";

                LDAWrapper.saveDocVectors(testDataFile, dvList);

                lda.infer(testDataFile);
            }
            Map<Integer, String> trDocId2UrlMap = loadLT(lda.config.getWorkDir() + "/docId2url.lt");
            Map<String, double[]> trTopicDistMap = getTopicDistributions(lda.config.getOutDir() + "/final.gamma",
                    trDocId2UrlMap);

            Map<String, double[]> topicDistMap = getTopicDistributions(lda.config.getWorkDir() +
                    "/" + lda.config.getInferenceName() + "-gamma.dat",
                    docId2UrlMap);
            int count = 0;
            String latestBatchId = getLatestBatchId();

            for (String url : trTopicDistMap.keySet()) {
                double[] topicDist = topicDistMap.get(url);
                if (topicDist != null) {
                    double[] trTopicDist = trTopicDistMap.get(url);
                    double jsDiv = TMUtils.calcJSDiv(topicDist, trTopicDist);
                    RegistrySiteContent rsc = rscMap.get(url);
                    Assertion.assertNotNull(rsc);
                    updateSemSimilarity(rsc, Math.max(1.0 - jsDiv, 0), latestBatchId);

                    if (!emptyContentUrlSet.contains(url) && jsDiv > 0.1) {
                        System.out.println(url + " JSDiv:" + jsDiv);
                        count++;
                    }
                }
            }
            System.out.println("count:" + count);

        }

        String getLatestBatchId() {
            EntityManager em = null;
            try {
                em = Utils.getEntityManager(emFactory);
                List<String> batchIdList = DisambiguatorFinder.getUniqueBatchIds4RegistryUpdateStatus(em);
                return batchIdList.get(0);
            } finally {
                Utils.closeEntityManager(em);
            }
        }

        public void prepTrainingData(LDAWrapper lda) throws Exception {
            EntityManager em = null;
            try {
                em = Utils.getEntityManager(emFactory);
                TypedQuery<RegistrySiteContent> query = em.createQuery(
                        "from RegistrySiteContent r where  r.content is not null and r.flags = :flags",
                        RegistrySiteContent.class);
                final List<RegistrySiteContent> rscList =
                        query.setParameter("flags", RegistrySiteContent.ORIGINAL).getResultList();
                Map<Integer, String> docId2UrlMap = new HashMap<Integer, String>();
                for (RegistrySiteContent rsc : rscList) {
                    String url = rsc.getRegistry().getUrl();
                    url = Utils.extractUrl(url);
                    String content = Utils.normalizeAllWS(rsc.getContent()).trim();
                    if (content != null && content.length() > 5) {
                        DocVector dv = lda.addTrainingDoc(content);
                        docId2UrlMap.put(dv.getDocId(), url);
                    }
                }

                lda.saveVocabulary();
                lda.saveTrainingData();

                saveLT(lda.config.getWorkDir() + "/docId2url.lt", docId2UrlMap);

                lda.train(0.01, 100);

            } finally {
                Utils.closeEntityManager(em);
            }
        }

        void updateSemSimilarity(RegistrySiteContent rsc, double semSim, String batchId) {
            EntityManager em = null;
            try {
                em = Utils.getEntityManager(emFactory);
                Utils.beginTransaction(em);
                TypedQuery<RegistryUpdateStatus> query = em.createQuery("from RegistryUpdateStatus s where " +
                        "s.registry.id = :rid and s.batchId = :batchId",
                        RegistryUpdateStatus.class).setParameter("rid", rsc.getRegistry().getId())
                        .setParameter("batchId", batchId);
                List<RegistryUpdateStatus> resultList = query.getResultList();
                for (RegistryUpdateStatus rus : resultList) {
                    rus.setSemanticSimilarity(semSim);
                    em.merge(rus);
                }
                Utils.commitTransaction(em);
            } catch (Exception x) {
                x.printStackTrace();
                Utils.rollbackTransaction(em);
            } finally {

                Utils.closeEntityManager(em);
            }
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("LDAWrapper", options);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option myOption = Option.builder("c").required().hasArg()
                .argName("command").desc("one of [train|infer|sim]").build();
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
        assert line != null;
        if (line.hasOption("h")) {
            usage(options);
        }

        String command = line.getOptionValue("c");


        String homeDir = System.getProperty("user.home");
        File ldaInstallDir = new File(homeDir + "/tools/lda-c-dist");
        LDAConfig config = new LDAConfig("/var/burak/rd_lda_wd");

        config.setSettingsFile(new File(ldaInstallDir, "settings.txt").getAbsolutePath());
        config.setInferenceSettingsFile(new File(ldaInstallDir, "inf-settings.txt").getAbsolutePath());

        config.setVocabularyFile(config.getWorkDir() + "/vocabulary.txt");
        config.setOutDir(config.getWorkDir() + "/update_tr");
        config.setTrainDataFile(config.getWorkDir() + "/train.dat");

        LDAWrapper lda = new LDAWrapper(config);
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());

            RegistryContentUtil rcu = new RegistryContentUtil();
            injector.injectMembers(rcu);

            if (command.equals("train")) {
                rcu.prepTrainingData(lda);
            } else if (command.equals("infer")) {
                rcu.prepTestingData(lda, false);
            } else if (command.equals("sim")) {
                rcu.prepTestingData(lda, true);
            }
        } finally {
            JPAInitializer.stopService();
        }

    }
}
