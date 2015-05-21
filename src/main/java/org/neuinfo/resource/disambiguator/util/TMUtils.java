package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.nlp.common.index.DocumentInfo;
import bnlpkit.nlp.common.index.FileInfo;
import bnlpkit.nlp.common.index.SentenceInfo;
import bnlpkit.util.FileUtils;
import bnlpkit.util.GenUtils;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.*;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.model.RegistryUpdateStatus;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 2/26/14.
 */
public class TMUtils {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    ParallelTopicModel topicModel;
    final static double ln2 = Math.log(2);


    public TMUtils() {
    }

    public TMUtils(String modelFile) throws Exception {
        this.topicModel = ParallelTopicModel.read(new File(modelFile));

    }

    public void handle() {
        Alphabet dataAlphabet = topicModel.getAlphabet();
        FeatureSequence tokens = (FeatureSequence) topicModel.getData().get(0).instance.getData();
        LabelSequence topics = topicModel.getData().get(0).topicSequence;
        Formatter formatter = new Formatter(new StringBuilder(128), Locale.US);
        for (int pos = 0; pos < tokens.getLength(); pos++) {
            formatter.format("%s-%s ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(pos)),
                    topics.getIndexAtPosition(pos));
        }
        System.out.println(formatter);
        int numTopics = topicModel.getNumTopics();


        // topic distribution for the first instance
        double[] topicDist = topicModel.getTopicProbabilities(0);
        ArrayList<TreeSet<IDSorter>> topicSortedWords = topicModel.getSortedWords();
        for (int topic = 0; topic < numTopics; topic++) {
            Iterator<IDSorter> it = topicSortedWords.get(topic).iterator();
            formatter = new Formatter(new StringBuilder(128), Locale.US);
            formatter.format("%d\t%.3f\t", topic, topicDist[topic]);
            int rank = 0;
            while (it.hasNext() && rank < 10) {
                IDSorter idCountPair = it.next();
                formatter.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()),
                        idCountPair.getWeight());
                rank++;
            }
            System.out.println(formatter);
        }
    }

    public void calcSemanticSimilarities(String trDataFile) throws Exception {
        Map<String, Integer> map = new HashMap<String, Integer>();
        Map<Integer, String> contentMap = new HashMap<Integer, String>();
        List<String> lines = GenUtils.loadStringList(trDataFile, CharSetEncoding.UTF8);
        int i = 0;
        for (String line : lines) {
            int idx = line.indexOf(' ');
            Assertion.assertTrue(idx != -1);
            String url = line.substring(0, idx);
            map.put(url, i);
            String content = line.substring(idx + 2).trim();
            contentMap.put(i, content);
            i++;
        }
        lines = null;
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<String> batchIdList = DisambiguatorFinder.getUniqueBatchIds4RegistryUpdateStatus(em);

            TypedQuery<RegistrySiteContent> query = em.createQuery(
                    "from RegistrySiteContent r where r.flags = :flags",
                    RegistrySiteContent.class);
            final List<RegistrySiteContent> rscList =
                    query.setParameter("flags", RegistrySiteContent.LATEST).getResultList();


            TopicInferencer inferencer = TopicInferencer.read(new File("/tmp/tm_inferencer.ser"));
            int count = 0;
            for (RegistrySiteContent rsc : rscList) {
                rsc.getRegistry().getResourceName();
                String url = rsc.getRegistry().getUrl();
                url = Utils.extractUrl(url);
                if (!map.containsKey(url)) {
                    continue;
                }
                if (!url.startsWith("http://www.med.upenn.edu/pharm")) {
                    continue;
                }
                Integer trInstanceIdx = map.get(url);
                // System.out.println("handling url:" + url);
                String content = Utils.normalizeAllWS(rsc.getContent());
                if (content.trim().length() == 0) {
                    // System.out.println("skipping...");
                    continue;
                }
                double[] topicDist = topicModel.getTopicProbabilities(trInstanceIdx);
                String origContent = contentMap.get(trInstanceIdx);
                SerialPipes pipeline = prepProcessPipeline();
                InstanceList testInstanceList = new InstanceList(pipeline);
                testInstanceList.addThruPipe(new Instance(origContent, null, "test instance", null));

                double[] latestTopicDist = inferencer.getSampledDistribution(testInstanceList.get(0), 100, 1, 10);
                // double Kpq = calcKLDiv(topicDist, latestTopicDist);
                double JSDiv = calcJSDiv(topicDist, latestTopicDist);
                //  if (JSDiv > 0.005) {
                //    System.out.printf("%s [%s] JSDiv:%.2f%n", url, rsc.getRegistry().getResourceName(),
                //            JSDiv);
                double semSim = Math.max(1.0 - JSDiv, 0);
                if (url.startsWith("http://www.med.upenn.edu/pharm")) {
                    dumpTopicStats(topicDist);
                    for (i = 0; i < topicDist.length; i++) {
                        System.out.printf("%d %.3f - %.3f%n", i, topicDist[i], latestTopicDist[i]);
                    }
                    System.out.println("===============================================");
                    dumpTopicStats(latestTopicDist);
                    System.out.println("----------------------------------");

                    System.out.println(GenUtils.formatText(origContent, 100));
                    System.out.println("\n----------------------------------");
                    System.out.println(GenUtils.formatText(content, 100));

                    Set<String> origSet = DocSimilarityUtils.prepShingles(origContent, 5);
                    Set<String> curSet = DocSimilarityUtils.prepShingles(content, 5);

                    double jc = DocSimilarityUtils.calcJaccardIndex(origSet, curSet);
                    System.out.println("JC:" + jc);
                }

                //  updateSemSimilarity(rsc, semSim, batchIdList.get(0));

                ++count;
            }
            //  }
            System.out.println("# of semantically changed (from original) resource sites:" + count);
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


    public static double calcJSDiv(double[] d1, double[] d2) {
        double[] mean = new double[d1.length];
        for (int i = 0; i < d1.length; i++) {
            mean[i] = (d1[i] + d2[i]) / 2.0;
        }

        Assertion.assertTrue(isAProbabilityDist(d1));
        Assertion.assertTrue(isAProbabilityDist(d2));
        Assertion.assertTrue(isAProbabilityDist(mean));

        return (calcKLDiv(d1, mean) + calcKLDiv(d2, mean)) / 2.0;
    }

    public static boolean isAProbabilityDist(double[] v) {
        double sum = 0;
        for (double p : v) {
            sum += p;
        }
        return Math.abs(sum - 1.0) < 0.0001;
    }

    public static double calcKLDiv(double[] d1, double[] d2) {
        double sum = 0;
        double p, q;
        for (int i = 0; i < d1.length; i++) {
            p = d1[i];
            q = d2[i];
            if (q > 0) {
                sum += p * (Math.log(p / q) / ln2);
            } else {
                if (q == 0 && p > 0) {
                    sum += p * (Math.log(p / 0.0001) / ln2);
                }
            }
        }

        return sum;
    }

    private SerialPipes prepProcessPipeline() {
        List<Pipe> pipeList = new ArrayList<Pipe>();

        pipeList.add(new CharSequenceLowercase());
        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")));
        TokenSequenceRemoveStopwords sw = new TokenSequenceRemoveStopwords(false, false);
        sw.addStopWords(StopWords.stopWords);
        pipeList.add(sw);
        pipeList.add(new TokenSequence2FeatureSequence());

        return new SerialPipes(pipeList);
    }

    public void prepTrainingData(String trFilePath, String idxXmlFile) throws Exception {
        final FileInfo theFI = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);
        BufferedWriter out = null;
        try {
            out = FileUtils.getBufferedWriter(trFilePath, CharSetEncoding.UTF8);
            for (DocumentInfo di : theFI.getDiList()) {
                StringBuilder contentBuf = new StringBuilder(4096);
                for (SentenceInfo si : di.getSiList()) {
                    contentBuf.append(si.getText().getText()).append(' ');
                }
                StringBuilder sb = new StringBuilder(1024);
                sb.append(di.getPMID()).append(" X ").append(contentBuf.toString().trim());
                out.write(sb.toString());
                out.newLine();
            }

        } finally {
            FileUtils.close(out);
        }
        System.out.println("Saved training data to " + trFilePath);
    }

    /**
     * @param trFilePath       path to the training file to be written
     * @param rscFlag          <code>RegistrySiteContent.ORIGINAL</code> or <code>RegistrySiteContent.LATEST</code>
     * @param maxNumOfEnteries
     * @throws Exception
     */
    public void prepTrainingData(String trFilePath, int rscFlag, int maxNumOfEnteries) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<RegistrySiteContent> query = em.createQuery(
                    "from RegistrySiteContent r where r.flags = :flags", RegistrySiteContent.class
            );
            final List<RegistrySiteContent> rscList =
                    query.setParameter("flags", rscFlag).getResultList();
            BufferedWriter out = null;
            try {
                out = FileUtils.getBufferedWriter(trFilePath, CharSetEncoding.UTF8);
                int count = 0;
                for (RegistrySiteContent rsc : rscList) {
                    rsc.getRegistry().getResourceName();
                    String url = rsc.getRegistry().getUrl();
                    url = Utils.extractUrl(url);
                    System.out.println("handling url:" + url);
                    if (rsc.getContent() == null || rsc.getContent().trim().length() == 0) {
                        System.out.println("skipping...");
                        continue;
                    }
                    String content = Utils.normalizeAllWS(rsc.getContent());
                    if (content.trim().length() == 0) {
                        System.out.println("skipping...");
                        continue;
                    }

                    StringBuilder sb = new StringBuilder(1024);
                    sb.append(url).append(" X ").append(content);
                    out.write(sb.toString());
                    out.newLine();
                    if (maxNumOfEnteries > 0 && count >= maxNumOfEnteries) {
                        break;
                    }
                    count++;
                }
            } finally {
                FileUtils.close(out);
            }
            System.out.println("Saved training data to " + trFilePath);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void dumpTopicStats(double[] topicDist) {
        Alphabet dataAlphabet = topicModel.getAlphabet();
        Formatter formatter;
        int numTopics = topicModel.getNumTopics();
        ArrayList<TreeSet<IDSorter>> topicSortedWords = topicModel.getSortedWords();
        for (int topic = 0; topic < numTopics; topic++) {
            Iterator<IDSorter> it = topicSortedWords.get(topic).iterator();
            formatter = new Formatter(new StringBuilder(128), Locale.US);
            formatter.format("%d\t%.3f\t", topic, topicDist[topic]);
            int rank = 0;
            while (it.hasNext() && rank < 5) {
                IDSorter idCountPair = it.next();
                formatter.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()),
                        idCountPair.getWeight());
                rank++;
            }
            System.out.println(formatter);
        }
    }

    static void dumpTopicStats(double[] topicDist, ParallelTopicModel model) {
        Alphabet dataAlphabet = model.getAlphabet();
        Formatter formatter;
        int numTopics = model.getNumTopics();
        ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
        for (int topic = 0; topic < numTopics; topic++) {
            Iterator<IDSorter> it = topicSortedWords.get(topic).iterator();
            formatter = new Formatter(new StringBuilder(128), Locale.US);
            formatter.format("%d\t%.3f\t", topic, topicDist[topic]);
            int rank = 0;
            while (it.hasNext() && rank < 5) {
                IDSorter idCountPair = it.next();
                formatter.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()),
                        idCountPair.getWeight());
                rank++;
            }
            System.out.println(formatter);
        }
    }


    public Set<String> getTopicWordsForDocument(String docContent) {
        return getTopicWordsForDocument(docContent, 100);
    }

    public Set<String> getTopicWordsForDocument(String docContent, int maxPerTopic) {
        TopicInferencer inferencer = this.topicModel.getInferencer();
        List<Pipe> pipeList = new ArrayList<Pipe>();

        pipeList.add(new CharSequenceLowercase());
        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")));
        TokenSequenceRemoveStopwords sw = new TokenSequenceRemoveStopwords(false, false);
        sw.addStopWords(StopWords.stopWords);
        pipeList.add(sw);
        pipeList.add(new TokenSequence2FeatureSequence());
        InstanceList testInstances = new InstanceList(new SerialPipes(pipeList));
        testInstances.addThruPipe(new Instance(docContent, null, "test instance", null));
        double[] testProbs = inferencer.getSampledDistribution(testInstances.get(0), 10, 1, 5);
       // dumpTopicStats(testProbs, this.topicModel);

        List<String> tokens = TermVectorUtils.tokenize(docContent);
        Set<String> tokenSet = new HashSet<String>(tokens);

        Set<String> docTopicWordSet = new HashSet<String>();
        Alphabet dataAlphabet = this.topicModel.getAlphabet();
        int numTopics = this.topicModel.getNumTopics();
        ArrayList<TreeSet<IDSorter>> topicSortedWords = this.topicModel.getSortedWords();
        for (int topic = 0; topic < numTopics; topic++) {
            if (testProbs[topic] > 0) {
                Iterator<IDSorter> it = topicSortedWords.get(topic).iterator();
                int rank = 0;
                while (it.hasNext() && rank < maxPerTopic) {
                    IDSorter idCountPair = it.next();
                    String topicToken = dataAlphabet.lookupObject(idCountPair.getID()).toString();
                    //  idCountPair.getWeight());
                    if (tokenSet.contains(topicToken)) {
                        docTopicWordSet.add(topicToken);
                    }
                    rank++;
                }
            }
        }
        return docTopicWordSet;
    }

    public static void doLDA(String trDataFile, String ldaModelFile, String inferencerFile) throws Exception {
        List<Pipe> pipeList = new ArrayList<Pipe>();

        pipeList.add(new CharSequenceLowercase());
        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")));
        TokenSequenceRemoveStopwords sw = new TokenSequenceRemoveStopwords(false, false);
        sw.addStopWords(StopWords.stopWords);
        pipeList.add(sw);
        pipeList.add(new TokenSequence2FeatureSequence());

        InstanceList instances = new InstanceList(new SerialPipes(pipeList));

        Reader in = null;

        try {
            List<String> lines = GenUtils.loadStringList(trDataFile, CharSetEncoding.UTF8);

            in = new InputStreamReader(new FileInputStream(trDataFile), "UTF-8");

            instances.addThruPipe(new CsvIterator(in, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"),
                    3, 2, 1)); // data (3), label (2), name (1) fields extracted

            double alpha_t = 0.01;
            double beta_w = 0.005;

            int numTopics = 100;
            ParallelTopicModel model = new ParallelTopicModel(numTopics, alpha_t * numTopics, beta_w);
            model.setNumThreads(4);
            model.setNumIterations(1000);
            model.addInstances(instances);
            model.estimate();

//            model.write(new File("/tmp/topic_model.ser"));
            model.write(new File(ldaModelFile));

            Alphabet dataAlphabet = instances.getDataAlphabet();

            FeatureSequence tokens = (FeatureSequence) model.getData().get(0).instance.getData();
            LabelSequence topics = model.getData().get(0).topicSequence;
            Formatter formatter = new Formatter(new StringBuilder(128), Locale.US);
            for (int pos = 0; pos < tokens.getLength(); pos++) {
                formatter.format("%s-%s ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(pos)),
                        topics.getIndexAtPosition(pos));
            }
            System.out.println(formatter);

            // topic distribution for the first instance
            double[] topicDist = model.getTopicProbabilities(0);
            ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
            for (int topic = 0; topic < numTopics; topic++) {
                Iterator<IDSorter> it = topicSortedWords.get(topic).iterator();
                formatter = new Formatter(new StringBuilder(128), Locale.US);
                formatter.format("%d\t%.3f\t", topic, topicDist[topic]);
                int rank = 0;
                while (it.hasNext() && rank < 5) {
                    IDSorter idCountPair = it.next();
                    formatter.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()),
                            idCountPair.getWeight());
                    rank++;
                }
                System.out.println(formatter);
            }

            // test
            StringBuilder topic0Text = new StringBuilder(256);
            Iterator<IDSorter> iterator = topicSortedWords.get(0).iterator();
            InstanceList testInstances = new InstanceList(instances.getPipe());
            int rank = 0;
            while (iterator.hasNext() && rank < 5) {
                IDSorter idCountPair = iterator.next();
                topic0Text.append(dataAlphabet.lookupObject(idCountPair.getID())).append(' ');
                rank++;
            }

            String instance1Content = extractContentPart(lines.get(0));


            testInstances.addThruPipe(new Instance(instance1Content, null, "test instance", null));


            TopicInferencer inferencer = model.getInferencer();

            ObjectOutputStream out = null;
            try {
                //out = new ObjectOutputStream(new FileOutputStream("/tmp/tm_inferencer.ser"));
                out = new ObjectOutputStream(new FileOutputStream(inferencerFile));
                out.writeObject(inferencer);

            } catch (Exception x) {
                x.printStackTrace();
            } finally {
                FileUtils.close(out);
            }

            double[] testProbs = inferencer.getSampledDistribution(testInstances.get(0), 10, 1, 5);
            System.out.println("0\t" + testProbs[0]);

            dumpTopicStats(topicDist, model);
            System.out.println("===============================================");
            dumpTopicStats(testProbs, model);
        } finally {
            FileUtils.close(in);
        }
    }

    public static void testDriver() throws Exception {
        //String trDataFile = "/tmp/ap.txt";
        String trDataFile = "/tmp/registry_tr.txt";

        List<Pipe> pipeList = new ArrayList<Pipe>();

        pipeList.add(new CharSequenceLowercase());
        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")));
        TokenSequenceRemoveStopwords sw = new TokenSequenceRemoveStopwords(false, false);
        sw.addStopWords(StopWords.stopWords);
        pipeList.add(sw);
        pipeList.add(new TokenSequence2FeatureSequence());

        InstanceList instances = new InstanceList(new SerialPipes(pipeList));

        Reader in = null;

        try {
            List<String> lines = GenUtils.loadStringList(trDataFile, CharSetEncoding.UTF8);

            in = new InputStreamReader(new FileInputStream(trDataFile), "UTF-8");

            instances.addThruPipe(new CsvIterator(in, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"),
                    3, 2, 1)); // data (3), label (2), name (1) fields extracted

            double alpha_t = 0.01;
            double beta_w = 0.005;

            int numTopics = 100;
            ParallelTopicModel model = new ParallelTopicModel(numTopics, alpha_t * numTopics, beta_w);
            model.setNumThreads(4);
            model.setNumIterations(1000);
            model.addInstances(instances);
            model.estimate();

            model.write(new File("/tmp/topic_model.ser"));

            Alphabet dataAlphabet = instances.getDataAlphabet();

            FeatureSequence tokens = (FeatureSequence) model.getData().get(0).instance.getData();
            LabelSequence topics = model.getData().get(0).topicSequence;
            Formatter formatter = new Formatter(new StringBuilder(128), Locale.US);
            for (int pos = 0; pos < tokens.getLength(); pos++) {
                formatter.format("%s-%s ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(pos)),
                        topics.getIndexAtPosition(pos));
            }
            System.out.println(formatter);

            // topic distribution for the first instance
            double[] topicDist = model.getTopicProbabilities(0);
            ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
            for (int topic = 0; topic < numTopics; topic++) {
                Iterator<IDSorter> it = topicSortedWords.get(topic).iterator();
                formatter = new Formatter(new StringBuilder(128), Locale.US);
                formatter.format("%d\t%.3f\t", topic, topicDist[topic]);
                int rank = 0;
                while (it.hasNext() && rank < 5) {
                    IDSorter idCountPair = it.next();
                    formatter.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()),
                            idCountPair.getWeight());
                    rank++;
                }
                System.out.println(formatter);
            }

            // test
            StringBuilder topic0Text = new StringBuilder(256);
            Iterator<IDSorter> iterator = topicSortedWords.get(0).iterator();
            InstanceList testInstances = new InstanceList(instances.getPipe());
            int rank = 0;
            while (iterator.hasNext() && rank < 5) {
                IDSorter idCountPair = iterator.next();
                topic0Text.append(dataAlphabet.lookupObject(idCountPair.getID())).append(' ');
                rank++;
            }

            String instance1Content = extractContentPart(lines.get(0));


            testInstances.addThruPipe(new Instance(instance1Content, null, "test instance", null));


            TopicInferencer inferencer = model.getInferencer();

            ObjectOutputStream out = null;
            try {
                out = new ObjectOutputStream(new FileOutputStream("/tmp/tm_inferencer.ser"));
                out.writeObject(inferencer);

            } catch (Exception x) {
                x.printStackTrace();
            } finally {
                FileUtils.close(out);
            }

            double[] testProbs = inferencer.getSampledDistribution(testInstances.get(0), 10, 1, 5);
            System.out.println("0\t" + testProbs[0]);

            dumpTopicStats(topicDist, model);
            System.out.println("===============================================");
            dumpTopicStats(testProbs, model);
        } finally {
            FileUtils.close(in);
        }
    }

    public static String extractContentPart(String line) {
        int idx = line.indexOf(' ');
        return line.substring(idx + 2).trim();
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            TMUtils tmu = new TMUtils("/tmp/topic_model.ser");
            injector.injectMembers(tmu);
            //tmu.prepTrainingData("/tmp/registry_tr.txt", RegistrySiteContent.ORIGINAL);

            //tmu.prepTrainingData("/tmp/registry_latest.txt", RegistrySiteContent.LATEST);

            // tmu.handle();

            tmu.calcSemanticSimilarities("/tmp/registry_tr.txt");

            testDriver();
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            JPAInitializer.stopService();
        }

    }

}
