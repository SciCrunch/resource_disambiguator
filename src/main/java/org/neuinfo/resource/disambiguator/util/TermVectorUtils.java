package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.util.SimpleSequentialIDGenerator;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.model.RegistryUpdateStatus;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 2/28/14.
 */
public class TermVectorUtils {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Pattern pattern = Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}");
    Map<String, TermDoc> vocabulary = new HashMap<String, TermDoc>();
    TIntIntHashMap termId2DocCountMap = new TIntIntHashMap();


    public TIntIntHashMap getTermId2DocCountMap() {
        return termId2DocCountMap;
    }

    public TermDoc getTermDoc(String term) {
        return vocabulary.get(term);
    }

    public List<DocVector> prepVocabularyAndDocVectors(IPredicate<RegistrySiteContent> predicate) {
        List<DocVector> docVectors = new ArrayList<DocVector>();
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<RegistrySiteContent> query = em.createQuery(
                    "from RegistrySiteContent r where  r.content is not null and r.flags = :flags",
                    RegistrySiteContent.class);
            final List<RegistrySiteContent> rscList =
                    query.setParameter("flags", RegistrySiteContent.ORIGINAL).getResultList();

            int termIdx = 1;
            Set<String> seenTermSet = new HashSet<String>();
            for (RegistrySiteContent rsc : rscList) {
                if (predicate != null && !predicate.satisfied(rsc)) {
                    System.out.println("skipping " + rsc.getRegistry().getResourceName());
                    continue;
                }
                String url = rsc.getRegistry().getUrl();
                url = Utils.extractUrl(url);
                String content = Utils.normalizeAllWS(rsc.getContent()).trim();
                if (content.length() == 0) {
                    // System.out.println("skipping...");
                    continue;
                }
                DocVector dv = new DocVector(url);
                List<String> tokens = tokenize(content);
                seenTermSet.clear();
                for (String token : tokens) {
                    TermDoc td = vocabulary.get(token);
                    if (td == null) {
                        td = new TermDoc(termIdx, 1);
                        termIdx++;
                        vocabulary.put(token, td);
                    } else {
                        if (!seenTermSet.contains(token)) {
                            td.docCount++;
                        }
                    }
                    dv.addTerm(td.termId);
                    seenTermSet.add(token);
                }
                docVectors.add(dv);
            }
            for (TermDoc td : vocabulary.values()) {
                termId2DocCountMap.put(td.termId, td.docCount);
            }
            return docVectors;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void prepTermId2DocCountMap() {
        for (TermDoc td : vocabulary.values()) {
            termId2DocCountMap.put(td.termId, td.docCount);
        }
    }

    public DocVector prepDocVector(String url, String content) {
        DocVector dv = new DocVector(url);
        List<String> tokens = tokenize(content);
        for (String token : tokens) {
            TermDoc td = vocabulary.get(token);
            if (td != null) {
                dv.addTerm(td.termId);
            }
        }
        return dv;
    }


    public DocVector prepBigramDocVectorFromSentencesWithVocabulary(String url,
                                                                    List<String> sentences,
                                                                    SimpleSequentialIDGenerator idGenerator) {
        DocVector dv = new DocVector(url);

        Set<String> seenTermSet = new HashSet<String>();
        for (String sentence : sentences) {
            List<String> tokens = tokenizeBigrams(sentence);
            for (String token : tokens) {
                TermDoc td = vocabulary.get(token);
                if (td != null) {
                    dv.addTerm(td.termId);
                    if (!seenTermSet.contains(token)) {
                        td.docCount++;
                        seenTermSet.add(token);
                    }
                } else {
                    int termIdx = idGenerator.nextID();
                    td = new TermDoc(termIdx, 1);
                    vocabulary.put(token, td);
                }
            }
        }
        return dv;
    }

    public DocVector prepDocVectorFromSentencesWithVocabulary(String url,
                                                              List<String> sentences, SimpleSequentialIDGenerator idGenerator) {
        DocVector dv = new DocVector(url);

        Set<String> seenTermSet = new HashSet<String>();
        for (String sentence : sentences) {
            List<String> tokens = tokenize(sentence);
            for (String token : tokens) {
                TermDoc td = vocabulary.get(token);
                if (td != null) {
                    dv.addTerm(td.termId);
                    if (!seenTermSet.contains(token)) {
                        td.docCount++;
                        seenTermSet.add(token);
                    }
                } else {
                    int termIdx = idGenerator.nextID();
                    td = new TermDoc(termIdx, 1);
                    vocabulary.put(token, td);
                }
            }
        }
        return dv;
    }

    public void calcCosineSimilarities(List<DocVector> origDocVectors) throws Exception {
        Map<String, DocVector> origDVMap = new HashMap<String, DocVector>();
        for (DocVector dv : origDocVectors) {
            origDVMap.put(dv.url, dv);
        }
        String latestBatchId = null;
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<String> batchIdList = DisambiguatorFinder.getUniqueBatchIds4RegistryUpdateStatus(em);
            latestBatchId = batchIdList.get(0);
            TypedQuery<RegistrySiteContent> query = em.createQuery(
                    "from RegistrySiteContent r where  r.content is not null and r.flags = :flags",
                    RegistrySiteContent.class);
            final List<RegistrySiteContent> rscList =
                    query.setParameter("flags", RegistrySiteContent.LATEST).getResultList();

            for (RegistrySiteContent rsc : rscList) {
                rsc.getRegistry().getResourceName();
                String url = rsc.getRegistry().getUrl();
                url = Utils.extractUrl(url);
                String content = Utils.normalizeAllWS(rsc.getContent()).trim();
                if (content.length() == 0) {
                    // System.out.println("skipping...");
                    continue;
                }
                DocVector dv = new DocVector(url);
                List<String> tokens = tokenize(content);
                for (String token : tokens) {
                    TermDoc td = vocabulary.get(token);
                    if (td != null) {
                        dv.addTerm(td.termId);
                    }
                }
                if (origDVMap.containsKey(url)) {
                    DocVector origDV = origDVMap.get(url);
                    TIntDoubleHashMap origTV = toTFIDFVector(origDV, termId2DocCountMap, origDVMap.size());
                    TIntDoubleHashMap latestTV = toTFIDFVector(dv, termId2DocCountMap, origDVMap.size());
                    double cosSim = calcCosSim(origTV, latestTV);
                    if (Double.isNaN(cosSim)) {
                        System.out.printf(">>>> (%d) %s [%.3f]%n", rsc.getRegistry().getId(), url, cosSim);
                    } else if (cosSim < 0.5) {
                        System.out.printf("%s [%.3f]%n", url, cosSim);
                    }
                    updateSemSimilarity(rsc, cosSim, latestBatchId);
                }
            }
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
                rus.setCosSimilarity(semSim);
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

    public static TIntDoubleHashMap toNonAugmentedTFVector(DocVector dv) {
        TIntDoubleHashMap tfidfMap = new TIntDoubleHashMap();
        for (TIntIntIterator it = dv.termFreqMap.iterator(); it.hasNext(); ) {
            it.advance();
            int termId = it.key();
            double tf = it.value();
            tfidfMap.put(termId, tf);
        }
        return tfidfMap;
    }

    public static TIntDoubleHashMap toTFVector(DocVector dv) {
        int maxFreq = 0;
        for (TIntIntIterator it = dv.termFreqMap.iterator(); it.hasNext(); ) {
            it.advance();
            if (it.value() > maxFreq) {
                maxFreq = it.value();
            }
        }
        TIntDoubleHashMap tfidfMap = new TIntDoubleHashMap();
        for (TIntIntIterator it = dv.termFreqMap.iterator(); it.hasNext(); ) {
            it.advance();
            int termId = it.key();
            // augmented term frequency for long document bias
            double tf = 0.5 + ((0.5 * it.value()) / maxFreq);
            tfidfMap.put(termId, tf);
        }
        return tfidfMap;
    }

    public static TIntDoubleHashMap toTFIDFVector(DocVector dv, TIntIntHashMap termId2DocCountMap,
                                                  int numDocs) {
        int maxFreq = 0;
        for (TIntIntIterator it = dv.termFreqMap.iterator(); it.hasNext(); ) {
            it.advance();
            if (it.value() > maxFreq) {
                maxFreq = it.value();
            }
        }
        TIntDoubleHashMap tfidfMap = new TIntDoubleHashMap();
        for (TIntIntIterator it = dv.termFreqMap.iterator(); it.hasNext(); ) {
            it.advance();
            int termId = it.key();
            // augmented term frequency for long document bias
            double tf = 0.5 + ((0.5 * it.value()) / maxFreq);
            int docCount = termId2DocCountMap.get(termId);
            double tfidf = tf * Math.log(numDocs / (double) docCount);
            tfidfMap.put(termId, tfidf);
        }
        return tfidfMap;
    }

    public static List<String> tokenize(String content) {
        content = content.toLowerCase();
        List<String> tokens = new LinkedList<String>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String token = matcher.group();
            if (!StopWords.isStopWord(token) && !isPunctuation(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public static List<String> tokenizeBigrams(String content) {
        content = content.toLowerCase();
        List<String> tokens;
        String[] toks = content.split("\\s+");
        tokens = Arrays.asList(toks);
        String prevTok = tokens.get(0);
        int len = tokens.size();
        List<String> bigrams = new LinkedList<String>();
        for (int i = 1; i < len; i++) {
            String tok = tokens.get(i);
            StringBuilder sb = new StringBuilder(prevTok.length() + tok.length() + 1);
            sb.append(prevTok).append(' ').append(tok);
            bigrams.add(sb.toString());
            prevTok = tok;
        }
        return bigrams;
    }

    public static List<String> tokenizeBigramsWithoutStopwords(String content) {
        content = content.toLowerCase();
        List<String> tokens;

        String[] toks = content.split("\\s+");
        tokens = Arrays.asList(toks);
        List<String> bigrams = new LinkedList<String>();
        if (tokens.isEmpty()) {
            return bigrams;
        }
        String prevTok = tokens.get(0);
        int len = tokens.size();
        for (int i = 1; i < len; i++) {
            String tok = tokens.get(i);
            if (isPunctuation(tok)) {
                prevTok = null;
                continue;
            }
            if (prevTok == null) {
                prevTok = tok;
                continue;
            }
            if (StopWords.isStopWord(prevTok) || StopWords.isStopWord(tok) || isPunctuation(prevTok)) {
                prevTok = tok;
                continue;
            }

            StringBuilder sb = new StringBuilder(prevTok.length() + tok.length() + 1);
            sb.append(prevTok).append(' ').append(tok);
            bigrams.add(sb.toString());
            prevTok = tok;
        }
        return bigrams;
    }

    public static boolean isPunctuation(String token) {
        if (token.length() == 1) {
            char c = token.charAt(0);
            return !Character.isAlphabetic(c);
        }
        return false;
    }

    public static double calcCosSim(TIntDoubleHashMap tv1, TIntDoubleHashMap tv2) {
        double denum = euclidianNorm(tv1) * euclidianNorm(tv2);
        if (denum == 0) {
            return 0;
        }
        return innerProd(tv1, tv2) / denum;
    }

    public static double innerProd(TIntDoubleHashMap tv1, TIntDoubleHashMap tv2) {
        double sum = 0;
        for (TIntDoubleIterator it = tv1.iterator(); it.hasNext(); ) {
            it.advance();
            if (tv2.containsKey(it.key())) {
                double x = it.value();
                sum += x * tv2.get(it.key());
            }
        }
        return sum;
    }

    public static double euclidianNorm(TIntDoubleHashMap tvMap) {
        double sum = 0;
        for (TIntDoubleIterator it = tvMap.iterator(); it.hasNext(); ) {
            it.advance();
            double x = it.value();
            sum += x * x;
        }
        return Math.sqrt(sum);
    }

    public static class DocVector {
        String url;
        TIntIntHashMap termFreqMap = new TIntIntHashMap();

        public DocVector(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }

        public void addTerm(int termId) {
            if (!termFreqMap.containsKey(termId)) {
                termFreqMap.put(termId, 1);
            } else {
                termFreqMap.put(termId, termFreqMap.get(termId) + 1);
            }
        }
    }


    public static class TermDoc {
        int termId;
        int docCount;

        public TermDoc(int termId, int docCount) {
            this.termId = termId;
            this.docCount = docCount;
        }

        public int getTermId() {
            return termId;
        }

        public int getDocCount() {
            return docCount;
        }
    }

    static void testTokenize() {
        String content = "Pharm || University of Pennsylvania Welcome Education Faculty Research Seminars Publications Contact\n" +
                "Research Areas Cancer Pharmacology Cardiovascular Pharmacology Environmental Health Sciences (EHS)\n" +
                "Neuropharmacology Pharmacogenetics Pharmacological Chemistry Department of Pharmacology Overview\n" +
                "Pharmacology involves the discovery of new drugs, the investigation of how drugs work and the use of\n" +
                "drugs to probe mechanisms of disease. But pharmacology also involves the elucid ation and\n" +
                "manipulation of macromolecular structures, the analysis of regulatory mechanisms in cell biology and\n" +
                "development, and the translation of this information into clinical re search. ";
        List<String> tokens = tokenize(content);
        for (String token : tokens) {
            System.out.println(token);
        }
    }

    public static class FilterPredicate implements IPredicate<RegistrySiteContent> {

        @Override
        public boolean satisfied(RegistrySiteContent obj) {
            if (obj.getContent() == null) {
                return false;
            }
            String content = obj.getContent().trim();

            return content.length() > 80;
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            TermVectorUtils tvu = new TermVectorUtils();
            injector.injectMembers(tvu);

            List<DocVector> docVectors = tvu.prepVocabularyAndDocVectors(new FilterPredicate());

            tvu.calcCosineSimilarities(docVectors);

        } finally {
            JPAInitializer.stopService();
        }
    }
}
