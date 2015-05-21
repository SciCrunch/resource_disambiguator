package org.neuinfo.resource.disambiguator.nlp;

import bnlpkit.nlp.common.*;
import bnlpkit.nlp.common.util.PropBankUtils;
import bnlpkit.nlp.common.util.SRLUtils;
import bnlpkit.nlp.common.util.TagSetUtils;
import bnlpkit.nlp.sbt.SentenceBoundaryClassifierFactory;
import bnlpkit.nlp.sbt.SentenceBoundaryDetector;
import bnlpkit.nlp.tools.sentence.PTTokenInfo;
import bnlpkit.nlp.tools.sentence.ParseTree2SentenceTokenAligner;
import bnlpkit.nlp.tools.sentence.TokenInfo;
import bnlpkit.util.GenUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.AcronymPaperPath;
import org.neuinfo.resource.disambiguator.model.PaperAcronyms;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.Paper2TextHandler;
import org.neuinfo.resource.disambiguator.services.Paper2TextHandler.ArticleInfo;
import org.neuinfo.resource.disambiguator.util.Assertion;
import org.neuinfo.resource.disambiguator.util.TokenizerUtils;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.XMLReader;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FilenameFilter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 9/19/14.
 */
public class AcronymDetectorService {
    @Inject
    @IndicatesPrimaryJpa
    Provider<EntityManager> emFactory = null;

    protected Pattern pattern = Pattern
            .compile("\\(\\s*([A-Z][A-Z\\-]*[A-Z]s?)\\s*\\)");
    protected Pattern secPattern = Pattern.compile("\\(\\s*([A-Z][a-zA-Z0-9\\-\\+]+)\\s*\\)");

    protected Map<String, Acronym> acrSet = new LinkedHashMap<String, Acronym>();
    SentenceBoundaryDetector sbd;
    protected ISyntacticParser syntacticParser;
    static Logger log = Logger.getLogger(AcronymDetectorService.class);

    public AcronymDetectorService(ISyntacticParser syntacticParser) {
        this.syntacticParser = syntacticParser;
        SentenceBoundaryDetector.Config config = new SentenceBoundaryDetector.Config();
        this.sbd = new SentenceBoundaryDetector(config,
                SentenceBoundaryClassifierFactory.SVM_CLASSIFIER,
                CharSetEncoding.UTF8);
    }


    public void extractSaveAcronyms(String batchNum) throws Exception {
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");

        String indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchNum);
        Assertion.assertTrue(baseDir.isDirectory());
        int baseDirLen = baseDir.getAbsolutePath().length();
        File[] journals = baseDir.listFiles();
        long start = System.currentTimeMillis();
        log.info("starting extracting acronyms from batch " + batchNum);
        int count = 0;
        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            getPapers(journalDir, papers);
            for (File paperPath : papers) {
                if (isPaperProcessedBefore(paperPath, baseDirLen)) {
                    // log.info("already processed " + paperPath + " Skipping");
                    count++;
                    continue;
                }
                System.out.println("handling " + paperPath);

                Pair<ArticleInfo, String> pair = extractContent(paperPath);
                checkArticle4Acronyms(pair);

                count++;
                if ((count % 100) == 0) {
                    log.info("Papers handled so far:" + count);
                }
                savePaperPath(paperPath, baseDirLen, pair.getFirst().getPMID());
            }
        }
        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished paper specific acronym detection for batch " + batchNum);
        log.info("---------------------------------------------------");
    }

    public void extractSecondaryAcronymsByBatch(String batchNum) throws Exception {
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");

        String indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchNum);
        Assertion.assertTrue(baseDir.isDirectory());
        Set<String> knownAcrSet = getKnownAcronyms();
        int baseDirLen = baseDir.getAbsolutePath().length();
        File[] journals = baseDir.listFiles();
        long start = System.currentTimeMillis();
        log.info("starting extracting secondary acronyms from batch " + batchNum);
        int count = 0;
        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            getPapers(journalDir, papers);
            for (File paperPath : papers) {
                System.out.println("handling " + paperPath);
                Pair<ArticleInfo, String> pair = extractContent(paperPath);
                List<Acronym> acronyms = checkArticle4SecondaryAcronyms(pair, knownAcrSet);
                if (acronyms != null && !acronyms.isEmpty()) {
                    ArticleInfo ai = pair.getFirst();
                    savePaperAcronyms(acronyms, ai.getPMID());
                }
                count++;
                if ((count % 100) == 0) {
                    log.info("Papers handled so far:" + count);
                }
                savePaperPath(paperPath, baseDirLen, pair.getFirst().getPMID());
            }
        }
        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished paper specific acronym detection for batch " + batchNum);
        log.info("---------------------------------------------------");
    }

    public void extractSecondaryAcronyms(File oaiRootDir) throws Exception {
        Set<String> knownAcrSet = getKnownAcronyms();
        Assertion.assertTrue(oaiRootDir.isDirectory());
        File[] journals = oaiRootDir.listFiles();
        long start = System.currentTimeMillis();
        log.info("starting extracting secondary acronyms from " + oaiRootDir.getAbsolutePath());
        int count = 0;
        boolean exitLoop = false;
        List<Acronym> foundAcronyms = new ArrayList<Acronym>();
        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            getPapers(journalDir, papers);
            for (File paperPath : papers) {
                System.out.println("handling " + paperPath);
                Pair<ArticleInfo, String> pair = extractContent(paperPath);
                List<Acronym> acronyms = checkArticle4SecondaryAcronyms(pair, knownAcrSet);
                if (acronyms != null && !acronyms.isEmpty()) {
                    foundAcronyms.addAll(acronyms);
                }
                count++;
                if ((count % 100) == 0) {
                    log.info("Papers handled so far:" + count);
                }
                if (count > 500) {
                    exitLoop = true;
                    break;
                }
            }
            if (exitLoop) {
                break;
            }
        }
        System.out.println("Found Secondary Acronyms\n=============================");
        for (Acronym a : foundAcronyms) {
            System.out.println(a);
        }

        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished paper specific acronym detection for dir " + oaiRootDir.getAbsolutePath());
        log.info("---------------------------------------------------");
    }

    Set<String> getKnownAcronyms() {
        EntityManager em = null;
        Set<String> knownAcrSet = new HashSet<String>();
        try {
            em = Utils.getEntityManager(emFactory);
            Query query = em.createQuery("select a.acronym from Acronym a");
            List<?> resultList = query.getResultList();
            for (Object o : resultList) {
                knownAcrSet.add((String) o);
            }
        } finally {
            Utils.closeEntityManager(em);
        }
        return knownAcrSet;
    }

    private boolean isPaperProcessedBefore(File paperPath, int baseDirLen) {
        String relPath = paperPath.getAbsolutePath().substring(baseDirLen);
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<AcronymPaperPath> query = em.createQuery("from AcronymPaperPath p where p.filePath = :path",
                    AcronymPaperPath.class);

            return !query.setParameter("path", relPath).getResultList().isEmpty();

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void savePaperPath(File paperPath, int baseDirLen, String pmid) throws Exception {
        EntityManager em = null;
        try {
            String relPath = paperPath.getAbsolutePath().substring(baseDirLen);
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            TypedQuery<AcronymPaperPath> query = em.createQuery("from AcronymPaperPath p where p.filePath = :path",
                    AcronymPaperPath.class);
            List<AcronymPaperPath> resultList = query.setParameter("path", relPath).getResultList();
            if (resultList.isEmpty()) {
                AcronymPaperPath app = new AcronymPaperPath();
                app.setFilePath(relPath);
                app.setPubmedId(pmid);
                em.persist(app);
            }
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    private void getPapers(File journalDir, List<File> paperList) {
        File[] files = journalDir.listFiles();
        assert files != null;
        for (File f : files) {
            if (f.isDirectory()) {
                getPapers(f, paperList);
            } else {
                paperList.add(f);
            }
        }
    }

    Pair<Paper2TextHandler.ArticleInfo, String> extractContent(File paperPath) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setFeature("http://xml.org/sax/features/validation", false);

        SAXParser parser = factory.newSAXParser();

        XMLReader xmlReader = parser.getXMLReader();
        Paper2TextHandler handler = new Paper2TextHandler(
                paperPath.getAbsolutePath(), Paper2TextHandler.OpType.URL);
        xmlReader.setContentHandler(handler);

        xmlReader.parse(Utils.convertToFileURL(paperPath.getAbsolutePath()));

        ArticleInfo ai = handler.getArticleInfo();

        return new Pair<ArticleInfo, String>(ai, handler.getText());
    }


    List<Acronym> checkArticle4SecondaryAcronyms(Pair<ArticleInfo, String> pair, Set<String> knownAcrSet) throws Exception {
        ArticleInfo ai = pair.getFirst();
        String pmid = ai.getPMID();

        String content = pair.getSecond();
        Matcher m = secPattern.matcher(content);
        boolean found = false;
        while (m.find()) {
            String acr = m.group(1).trim();
            if (!knownAcrSet.contains(acr)) {
                found = true;
                break;
            }
        }

        if (!found) {
            return null;
        }
        System.out.println("handing " + pmid + "...");
        List<Acronym> paperAcronyms = new ArrayList<Acronym>(5);
        try {
            List<String> sentences = sbd.tagSentenceBoundariesStreaming(content, false);
            Set<String> seenAcrSet = new HashSet<String>();
            // clear at every paper
            acrSet.clear();
            for (String sentence : sentences) {
                m = secPattern.matcher(sentence);
                if (m.find() && !knownAcrSet.contains(m.group(1).trim())) {
                    try {
                        sentence = TokenizerUtils.toWSDelimTokenSentence(sentence);

                        // parse the sentence
                        Node rootNode = syntacticParser.parseSentence(sentence);
                        List<AcronymPTLocWrapper> list = handleSecAcronyms(sentence, rootNode, knownAcrSet);
                        if (list != null) {
                            for (AcronymPTLocWrapper w : list) {
                                String key = prepKey(w.getAcronym());
                                if (!seenAcrSet.contains(key)) {
                                    seenAcrSet.add(key);
                                    paperAcronyms.add(w.getAcronym());
                                }
                            }
                        }
                    } catch (Throwable t) {
                        System.out.println(t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            log.error("skipping " + ai.getPMID() + " [" + ai.getFilePath() + "]");
        }
        return paperAcronyms;
    }

    void checkArticle4Acronyms(Pair<ArticleInfo, String> pair) throws Exception {
        ArticleInfo ai = pair.getFirst();
        String pmid = ai.getPMID();

        String content = pair.getSecond();
        Matcher m = pattern.matcher(content);
        if (!m.find()) {
            return;
        }

        if (isPaperProcessedBefore(pmid)) {
            System.out.println("Already processed skipping " + pmid);
            return;
        }
        System.out.println("handing " + pmid + "...");
        List<Acronym> paperAcronyms = new ArrayList<Acronym>(5);
        try {
            List<String> sentences = sbd.tagSentenceBoundariesStreaming(content, false);
            Set<String> seenAcrSet = new HashSet<String>();
            // clear at every paper
            acrSet.clear();
            for (String sentence : sentences) {
                m = pattern.matcher(sentence);
                if (m.find()) {
                    try {
                        sentence = TokenizerUtils.toWSDelimTokenSentence(sentence);

                        // parse the sentence
                        Node rootNode = syntacticParser.parseSentence(sentence);

                        List<AcronymPTLocWrapper> list = handleAcronyms(sentence, rootNode);
                        if (list != null) {
                            for (AcronymPTLocWrapper w : list) {
                                String key = prepKey(w.getAcronym());
                                if (!seenAcrSet.contains(key)) {
                                    seenAcrSet.add(key);
                                    paperAcronyms.add(w.getAcronym());
                                }
                            }
                        }
                    } catch (Throwable t) {
                        System.out.println(t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            log.error("skipping " + ai.getPMID() + " [" + ai.getFilePath() + "]");
        }
        if (!paperAcronyms.isEmpty()) {
            savePaperAcronyms(paperAcronyms, ai.getPMID());
        }
    }


    void checkArticle4AcronymsLocal(Pair<ArticleInfo, String> pair, List<Acronym> acrList) throws Exception {
        ArticleInfo ai = pair.getFirst();
        String pmid = ai.getPMID();

        String content = pair.getSecond();
        Matcher m = pattern.matcher(content);
        if (!m.find()) {
            return;
        }

        System.out.println("handing " + pmid + "...");
        List<Acronym> paperAcronyms = new ArrayList<Acronym>(5);
        try {
            List<String> sentences = sbd.tagSentenceBoundariesStreaming(content, false);
            Set<String> seenAcrSet = new HashSet<String>();

            // clear at every paper
            acrSet.clear();
            for (String sentence : sentences) {
                m = pattern.matcher(sentence);
                if (m.find()) {
                    try {
                        sentence = TokenizerUtils.toWSDelimTokenSentence(sentence);

                        // parse the sentence
                        Node rootNode = syntacticParser.parseSentence(sentence);

                        List<AcronymPTLocWrapper> list = handleAcronyms(sentence, rootNode);
                        if (list != null) {
                            for (AcronymPTLocWrapper w : list) {
                                String key = prepKey(w.getAcronym());
                                if (!seenAcrSet.contains(key)) {
                                    seenAcrSet.add(key);
                                    paperAcronyms.add(w.getAcronym());
                                }
                            }
                        }
                    } catch (Throwable t) {
                        System.out.println(t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {

        }
        acrList.addAll(paperAcronyms);
    }

    boolean isPaperProcessedBefore(String pmid) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<PaperAcronyms> list = getMatchingPaperAcronyms(em, pmid);
            return !list.isEmpty();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void savePaperAcronyms(List<Acronym> paperAcronyms, String pmid) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            List<PaperAcronyms> matchingPaperAcronyms = getMatchingPaperAcronyms(em, pmid);
            if (matchingPaperAcronyms.isEmpty()) {
                for (Acronym a : paperAcronyms) {
                    PaperAcronyms pa = new PaperAcronyms();
                    pa.setAcronym(a.getAcronym());
                    pa.setExpansion(a.getExpansion(0).getExpansion());
                    pa.setPubmedId(pmid);
                    em.persist(pa);
                }
            } else {
                Set<String> existingSet = new HashSet<String>(17);
                for (PaperAcronyms pa : matchingPaperAcronyms) {
                    String key = pa.getAcronym() + ":" + pa.getExpansion();
                    existingSet.add(key);
                }
                for (Acronym a : paperAcronyms) {
                    String key = a.getAcronym() + ":" + a.getExpansion(0).getExpansion();
                    if (!existingSet.contains(key)) {
                        PaperAcronyms pa = new PaperAcronyms();
                        pa.setAcronym(a.getAcronym());
                        pa.setExpansion(a.getExpansion(0).getExpansion());
                        pa.setPubmedId(pmid);
                        em.persist(pa);
                    }
                }
            }

            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }

    }

    List<PaperAcronyms> getMatchingPaperAcronyms(EntityManager em, String pmid) {
        TypedQuery<PaperAcronyms> query = em.createQuery("from PaperAcronyms p where p.pubmedId = :pmid",
                PaperAcronyms.class);
        return query.setParameter("pmid", pmid).getResultList();
    }

    public void showAcronyms() {
        for (Acronym acr : this.acrSet.values()) {
            System.out.println(acr);
        }
    }


    List<AcronymPTLocWrapper> handleSecAcronyms(String sentence, Node rootNode, Set<String> knownAcrSet) throws Exception {
        int parseType = PropBankUtils.CHARNIAK_PT;
        List<PTTokenInfo> pttList = ParseTree2SentenceTokenAligner
                .toTokens(rootNode, parseType);
        List<TokenInfo> tiList = ParseTree2SentenceTokenAligner
                .toTokens(sentence, parseType);
        try {
            ParseTree2SentenceTokenAligner aligner = new ParseTree2SentenceTokenAligner(
                    pttList, tiList);
            aligner.align();
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("Skipping sentence "
                    + GenUtils.formatText(sentence, 120));
            return null;
        }
        List<AcronymPTLocWrapper> list = new LinkedList<AcronymPTLocWrapper>();
        List<Node> npList = new ArrayList<Node>(2);
        SRLUtils.findNounPhrases(rootNode, npList);
        for (Node npNode : npList) {
            String phraseStr = ParseTreeManager.parseTree2Sentence(
                    npNode).trim();
            Matcher m = secPattern.matcher(phraseStr);
            int count = 0;
            while (m.find()) {
                String acrStr = m.group(1).trim();
                if (knownAcrSet.contains(acrStr)) {
                    break;
                }
                if (count == 0) {
                    System.out.println(phraseStr);
                }
                AcronymPTLocWrapper acronymPTLocWrapper = findAcronym(npNode, count, "");
                if (acronymPTLocWrapper != null) {
                    list.add(acronymPTLocWrapper);
                }
                count++;
            }
        }
        return list;
    }

    List<AcronymPTLocWrapper> handleAcronyms(String sentence, Node rootNode) throws Exception {
        int parseType = PropBankUtils.CHARNIAK_PT;
        List<PTTokenInfo> pttList = ParseTree2SentenceTokenAligner
                .toTokens(rootNode, parseType);
        List<TokenInfo> tiList = ParseTree2SentenceTokenAligner
                .toTokens(sentence, parseType);
        try {
            ParseTree2SentenceTokenAligner aligner = new ParseTree2SentenceTokenAligner(
                    pttList, tiList);
            aligner.align();
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("Skipping sentence "
                    + GenUtils.formatText(sentence, 120));
            return null;
        }
        List<AcronymPTLocWrapper> list = new LinkedList<AcronymPTLocWrapper>();
        List<Node> npList = new ArrayList<Node>(2);
        SRLUtils.findNounPhrases(rootNode, npList);
        for (Node npNode : npList) {
            String phraseStr = ParseTreeManager.parseTree2Sentence(
                    npNode).trim();
            Matcher m = pattern.matcher(phraseStr);
            int count = 0;
            while (m.find()) {
                if (count == 0) {
                    System.out.println(phraseStr);
                }
                AcronymPTLocWrapper acronymPTLocWrapper = findAcronym(npNode, count, "");
                if (acronymPTLocWrapper != null) {
                    list.add(acronymPTLocWrapper);
                }
                count++;
            }
        }
        return list;
    }

    public static String escapeParens(String capturedGroup) {
        String[] toks = capturedGroup.split("\\s+");
        StringBuilder sb = new StringBuilder(toks[0]).append(' ');
        if (toks[1].charAt(0) == '(') {
            sb.append("-LRB-").append(toks[1].substring(1));
        } else if (toks[1].charAt(0) == ')') {
            sb.append("-RRB-").append(toks[1].substring(1));
        }
        return sb.toString();
    }


    protected AcronymPTLocWrapper findAcronym(Node npNode,
                                              int leftParenSkipCount, String source) {
        StringBuilder acronymSB = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        List<Node> acronymSpanningNodes = new ArrayList<Node>(1);
        List<Node> sList = new ArrayList<Node>(10);
        createSurfaceList(npNode, sList);
        Acronym acr = null;

        int i = 0;
        int startIdx = -1;
        int count = 0;
        for (Node leafNode : sList) {
            if (isLeftParen(leafNode)) {
                count++;
                if (count > leftParenSkipCount) {
                    startIdx = i;
                    break;
                }
            }
            i++;
        }
        if (startIdx >= 0) {
            // get acronym
            ListIterator<Node> iter = sList.listIterator(startIdx + 1);
            while (iter.hasNext()) {
                Node leafNode = iter.next();
                if (isRightParen(leafNode)) {
                    break;
                } else {
                    acronymSB.append(leafNode.getToken());
                    acronymSpanningNodes.add(leafNode);
                }
            }
            iter = sList.listIterator(startIdx);
            List<Node> expList = new ArrayList<Node>(4);
            int idx = startIdx;
            while (iter.hasPrevious()) {
                Node leafNode = iter.previous();
                idx--;
                int tagCode = TagSetUtils.getPOSTagCode(leafNode.getTag());
                if (TagSetUtils.isNoun(leafNode.getTag())
                        || tagCode == POSTagSet.JJ
                        || tagCode == POSTagSet.VBN
                        || tagCode == POSTagSet.VBP
                        || tagCode == POSTagSet.VBG
                        || tagCode == POSTagSet.VB
                        || tagCode == POSTagSet.FW
                        || (tagCode == POSTagSet.IN && isEligibleProposition(leafNode
                        .getToken()))
                        || tagCode == POSTagSet.TO
                        || (tagCode == POSTagSet.CC && leafNode.getToken()
                        .equals("and"))) {
                    if (tagCode == POSTagSet.CC) {
                        if (idx > 0) {
                            // if an conjunction is encountered both conjoined
                            // words
                            // must start with upper case letters to the an
                            // acronym.
                            Node prev = sList.get(idx - 1);
                            if (!Character.isUpperCase(prev.getToken()
                                    .charAt(0))
                                    || !Character.isUpperCase(sList
                                    .get(idx + 1).getToken().charAt(0))) {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                    expList.add(leafNode);
                } else {
                    System.out.println("Stopping at " + leafNode.getTag());
                    break;
                }
            }
            Collections.reverse(expList);
            for (Node n : expList) {

                sb.append(n.getToken()).append(' ');
            }

            String expansion = sb.toString().trim();
            String acronym = acronymSB.toString();
            System.out.println("Acronym:" + acronym);
            System.out.println("Expansion: " + expansion);
            String revExpansion = checkValidity(acronym, expansion, true);
            if (revExpansion == null) {
                // check also without dash expansion
                revExpansion = checkValidity(acronym, expansion, false);
                if (revExpansion != null) {
                    if (!revExpansion.equals(expansion)) {
                        System.out.println(">> Revised expansion:"
                                + revExpansion);
                    }
                    acr = new Acronym(acronym);
                    String expansionPt = toLispRep(expList, revExpansion);

                    Acronym.AcrExpansion acrExp = new Acronym.AcrExpansion(revExpansion,
                            expansionPt, source, "1");
                    acr.addExpansion(acrExp);
                    String key = prepKey(acr);
                    if (!acrSet.containsKey(key)) {
                        acrSet.put(key, acr);
                        acr.getExpansion(0).setFreq(1);
                    } else {
                        Acronym a = acrSet.get(key);
                        Acronym.AcrExpansion exp = a.getExpansion(0);
                        exp.setFreq(exp.getFreq() + 1);
                    }
                } else {
                    System.out.println("*** INVALID ***");
                    return null;
                }
            } else {
                if (!revExpansion.equals(expansion)) {
                    System.out.println(">> Revised expansion:" + revExpansion);
                }
                acr = new Acronym(acronym);
                String expansionPt = toLispRep(expList, revExpansion);
                Acronym.AcrExpansion acrExp = new Acronym.AcrExpansion(revExpansion,
                        expansionPt, source, "1");
                acr.addExpansion(acrExp);
                String key = prepKey(acr);
                if (!acrSet.containsKey(key)) {
                    acrSet.put(key, acr);
                    acr.getExpansion(0).setFreq(1);
                } else {
                    Acronym a = acrSet.get(key);
                    Acronym.AcrExpansion exp = a.getExpansion(0);
                    exp.setFreq(exp.getFreq() + 1);
                }
            }
        }
        return new AcronymPTLocWrapper(acronymSpanningNodes, acr);
    }

    public static String prepKey(Acronym acr) {
        StringBuilder sb = new StringBuilder(100);
        sb.append(acr.getAcronym()).append(':');
        sb.append(acr.getExpansion(0).getExpansion());
        return sb.toString();
    }

    protected String toLispRep(List<Node> expList, String revExpansion) {
        String[] toks = revExpansion.split("\\s+");
        if (toks.length == expList.size()) {
            return toLispRep(expList);
        } else {
            List<Node> revExpList = new ArrayList<Node>(expList.size());
            int startIdx = findMatchingNodeIdx(toks[0], expList, 0);
            if (startIdx != -1) {
                int endIdx = findMatchingNodeIdx(toks[toks.length - 1],
                        expList, startIdx);
                if (endIdx != -1) {
                    for (int i = startIdx; i <= endIdx; i++) {
                        revExpList.add(expList.get(i));
                    }
                    return toLispRep(revExpList);
                }
            }
            boolean found = false;
            int startOffset = 0;
            while (!found) {
                revExpList.clear();
                boolean ok = true;
                int lastIdx = -1;
                int i = 0;
                while (i < toks.length) {
                    String tok = toks[i];
                    int idx = findMatchingNodeIdx(tok, expList, startOffset);
                    if (lastIdx == -1) {
                        startOffset = idx;
                    }

                    if (idx == -1 || (lastIdx >= 0 && idx != (lastIdx + 1))) {
                        if (lastIdx >= 0
                                && idx != -1
                                && isEligibleProposition(expList.get(
                                lastIdx + 1).getToken())) {
                            idx = lastIdx + 1;
                            --i;
                        } else {
                            ok = false;
                            break;
                        }
                    }
                    revExpList.add(expList.get(idx));
                    lastIdx = idx;
                    i++;
                }
                if (!ok) {
                    if (startOffset < 0) {
                        System.err
                                .println("No alignment with parse tree. Returning empty parse tree");
                        return "";
                    }
                    startOffset++;
                    if (startOffset >= toks.length) {
                        throw new RuntimeException(
                                "Should not happen! But did!");
                    }
                } else {
                    found = true;
                }
            }
            return toLispRep(revExpList);
        }
    }

    protected String toLispRep(List<Node> expList) {
        StringBuilder sb = new StringBuilder();
        sb.append("(FRAG");
        for (Node n : expList) {
            sb.append(' ').append(n.toLispString());
        }
        sb.append(')');
        return sb.toString();

    }

    protected int findMatchingNodeIdx(String tok, List<Node> nodeList,
                                      int startOffset) {
        for (int i = startOffset; i < nodeList.size(); i++) {
            Node n = nodeList.get(i);
            if (tok.equals(n.getToken()) || n.getToken().endsWith(tok)
                    || n.getToken().startsWith(tok)) {
                return i;
            }

        }
        return -1;
    }

    protected String removeTillPivot(String expansion, int pivotIdx) {
        char[] carr = expansion.toCharArray();
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (char aCarr : carr) {
            if (idx < pivotIdx
                    && (aCarr == '-' || Character.isWhitespace(aCarr))) {
                idx++;
                continue;
            }
            if (idx == pivotIdx) {
                sb.append(aCarr);
            }
        }
        return sb.toString();
    }

    protected String conditionAcronym(String acronym) {
        acronym = acronym.replaceAll("-", "");
        if (acronym.endsWith("s"))
            acronym = acronym.substring(0, acronym.length() - 1);
        return acronym;
    }

    protected String checkValidity(String acronym, String expansion,
                                   boolean withDashExpansion) {
        acronym = conditionAcronym(acronym);
        String[] toks;
        if (withDashExpansion)
            toks = expansion.split("[\\s|-]+");
        else
            toks = expansion.split("\\s+");
        int pivotIdxBeforeStrip = findFirstPivotIdx(acronym, toks);
        // remove any occurrence of eligible propositions and conjunctions;
        toks = stripIneligibleToks(toks);
        StringBuilder buffer = new StringBuilder();
        if (toks.length == acronym.length()) {
            // check if acronym letter is the first letter of each tok
            int pivotIdx = findFirstPivotIdx(acronym, toks);
            if (pivotIdx == 0) {

                if (checkIfFLBAcronym(toks, acronym, buffer, 0)) {
                    return expansion;
                } else {
                    if (checkValidity2(acronym, 0, toks, pivotIdx)) {
                        return expansion;
                    }
                    return null;
                }
            } else {
                if (pivotIdx > 0) {
                    if (checkValidity2(acronym, 0, toks, pivotIdx)) {
                        return removeTillPivot(expansion, pivotIdxBeforeStrip);
                    }
                } else
                    return null;
            }
        } else {
            // check if there is any token starting with the first letter of
            // potential acronym
            int pivotIdx = findFirstPivotIdx(acronym, toks);
            if (pivotIdx >= 0) {
                if (toks.length - pivotIdx == acronym.length()) {

                    if (checkIfFLBAcronym(toks, acronym, buffer, pivotIdx)) {
                        return buffer.toString().trim();
                    }
                    if (checkValidity2(acronym, 1, toks, pivotIdx)) {
                        return removeTillPivot(expansion, pivotIdxBeforeStrip);
                    }
                } else {
                    if (checkIfFLBAcronym(toks, acronym, buffer, pivotIdx)) {
                        return buffer.toString().trim();
                    }
                    if (checkValidity2(acronym, 0, toks, pivotIdx)) {
                        return removeTillPivot(expansion, pivotIdxBeforeStrip);
                    }
                }
            }
        }
        return null;
    }

    protected boolean checkIfFLBAcronym(String[] toks, String acronym,
                                        StringBuilder sb, int startOffset) {
        char[] acrArr = acronym.toLowerCase().toCharArray();
        if (toks.length < acrArr.length)
            return false;
        int diff = toks.length - acrArr.length;
        int offset = startOffset;
        while (offset <= diff) {
            boolean ok = true;
            for (int i = offset; i < toks.length; i++) {
                if (toks[i].length() == 0 || (i - offset) >= acrArr.length) {
                    ok = false;
                    break;
                }
                char c = Character.toLowerCase(toks[i].charAt(0));
                if (c != acrArr[i - offset]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                for (int i = offset; i < toks.length; i++) {
                    sb.append(toks[i]).append(' ');
                }
                return true;
            }
            offset++;
        }
        return false;
    }

    protected String[] stripIneligibleToks(String[] toks) {
        List<String> stripped = new ArrayList<String>(toks.length);
        for (String tok : toks) {
            if (tok.equals("of") || tok.equals("in") || tok.equals("and")
                    || tok.equals("to") || tok.equals("for"))
                continue;
            stripped.add(tok);
        }
        String[] strippedArr = new String[stripped.size()];
        return stripped.toArray(strippedArr);
    }

    protected int findFirstPivotIdx(String acronym, String[] toks) {
        if (acronym.length() == 0)
            return -1;
        char firstChar = acronym.toLowerCase().charAt(0);
        int pivotIdx = -1;
        for (int i = 0; i < toks.length; i++) {
            if (toks[i].length() == 0)
                continue;
            char c = Character.toLowerCase(toks[i].charAt(0));
            if (firstChar == c) {
                pivotIdx = i;
                break;
            }
        }
        return pivotIdx;
    }

    boolean checkValidity2(String acronym, int acrStartIdx, String[] toks,
                           int pivotIdx) {
        acronym = conditionAcronym(acronym);
        char[] acrArr = acronym.toLowerCase().toCharArray();
        String[] remToks = new String[toks.length - pivotIdx];
        for (int i = pivotIdx; i < toks.length; i++)
            remToks[i - pivotIdx] = toks[i].toLowerCase();
        int k = 0;
        int acrIdx = acrStartIdx;
        int len = remToks.length;
        while (k < len) {
            if (acrIdx >= acrArr.length) {
                return true;
            }
            boolean hasNext = k + 1 < len;

            char c = remToks[k].charAt(0);
            if (c == acrArr[acrIdx]) {
                if (hasNext && acrIdx + 1 < acrArr.length
                        && remToks[k + 1].charAt(0) == acrArr[acrIdx + 1]) {
                    k++;
                }
                acrIdx++;
            } else {
                int idx = remToks[k].indexOf(acrArr[acrIdx]);
                if (idx == -1) {
                    return false;
                }
                acrIdx++;
                if (acrIdx >= acrArr.length) {
                    return true;
                }
                if (!hasNext) {
                    int i = acrIdx;
                    while (i < acrArr.length) {
                        idx = remToks[k].indexOf(acrArr[i], idx + 1);
                        if (idx == -1)
                            return false;
                        i++;
                    }
                    return true;
                } else {
                    // there are more tokens
                    AcrPair pivot = findFirstLetterMatch(remToks, k + 1, acrArr,
                            acrIdx);
                    if (pivot == null) {
                        return false;
                    }
                    int i = acrIdx;
                    while (i < pivot.acrIdx) {
                        idx = remToks[k].indexOf(acrArr[i], idx + 1);
                        if (idx == -1)
                            return false;
                        i++;
                    }
                    acrIdx = pivot.acrIdx;
                    k++;
                }

            }
        }
        return false;
    }

    protected AcrPair findFirstLetterMatch(String[] toks, int startIdx,
                                           char[] acr, int acrStartIdx) {
        for (int i = acrStartIdx; i < acr.length; i++) {
            if (toks[startIdx].charAt(0) == acr[i])
                return new AcrPair(i, startIdx);
        }
        return null;
    }

    public boolean isLeftParen(Node nd) {
        return nd.getToken().equals("-LRB-");
    }

    public boolean isRightParen(Node nd) {
        return nd.getToken().equals("-RRB-");
    }

    public static boolean isEligibleProposition(String tok) {
        return tok.equals("in") || tok.equals("of") || tok.equals("for")
                || tok.equals("to");
    }

    public void createSurfaceList(Node node, List<Node> sList) {
        if (node == null)
            return;
        if (!node.hasChildren()) {
            sList.add(node);
        }
        for (Node child : node.getChildren()) {
            createSurfaceList(child, sList);
        }
    }

    public static class AcronymPTLocWrapper {
        List<Node> acronymSpanningNodes;
        Acronym acronym;

        public AcronymPTLocWrapper(List<Node> acronymSpanningNodes,
                                   Acronym acronym) {
            super();
            this.acronymSpanningNodes = acronymSpanningNodes;
            this.acronym = acronym;
        }

        public List<Node> getAcronymSpanningNodes() {
            return acronymSpanningNodes;
        }

        public Node getLastAcronymNode() {
            return acronymSpanningNodes.get(acronymSpanningNodes.size() - 1);
        }

        public Acronym getAcronym() {
            return acronym;
        }
    }// ;

    class AcrPair {
        int acrIdx;
        int tokIdx;

        AcrPair(int acrIdx, int tokIdx) {
            this.acrIdx = acrIdx;
            this.tokIdx = tokIdx;
        }

    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("AcronymDetectorService", options);
        System.exit(1);
    }

    public static void cli(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option myOption = Option.builder("d").required().hasArg()
                .argName("batchId").desc("batchId [eg. 201310]").build();
        Option secOption = Option.builder("s").desc("secondary acronym extraction").build();
        Options options = new Options();
        options.addOption(help);
        options.addOption(myOption);
        options.addOption(secOption);
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
        String batchId = line.getOptionValue("d");
        boolean secondary = line.hasOption("s");
        Injector injector = Guice.createInjector(new RDPersistModule());


        ISyntacticParser sp = new OpenNLPParser(250);
        AcronymDetectorService service = new AcronymDetectorService(sp);

        try {
            injector.injectMembers(service);
            if (secondary) {
               service.extractSecondaryAcronymsByBatch(batchId);
            } else {
                service.extractSaveAcronyms(batchId);
            }
        } finally {
            JPAInitializer.stopService();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
        // testDrive();
        //testSecAcronyms();
    }

    static void testSecAcronyms() throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());
        ISyntacticParser sp = new OpenNLPParser(250);
        AcronymDetectorService ad = new AcronymDetectorService(sp);
        File rootDir = new File("/var/indexes/PMC_OAI_201310");
        try {
            injector.injectMembers(ad);
            ad.extractSecondaryAcronyms(rootDir);
        } finally {
            JPAInitializer.stopService();
        }

    }

    static void testDrive() throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());

        //ISyntacticParser sp = new StanfordLexicalParser(150);
        ISyntacticParser sp = new OpenNLPParser(250);

        AcronymDetectorService ad = new AcronymDetectorService(sp);
        File rootDir = new File("/var/indexes/PMC_OAI_201310/Hum_Genet");
        File[] files = rootDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".nxml");
            }
        });
        System.out.print("Press a key to continue:");
        System.in.read();
        try {
            injector.injectMembers(ad);
            int count = 0;
            List<Acronym> acrList = new ArrayList<Acronym>(100);
            for (File paperPath : files) {
                Pair<ArticleInfo, String> pair = ad.extractContent(paperPath);
                // System.out.println(GenUtils.formatText(pair.getSecond(), 120));
                // System.out.println(pair.getSecond());
                ad.checkArticle4AcronymsLocal(pair, acrList);
                count++;
                if (count >= 10) {
                    break;
                }
            }
            System.out.println("Extracted Acronyms");
            System.out.println("------------------");
            for (Acronym acr : acrList) {
                System.out.println(acr);
            }
        } finally {
            JPAInitializer.stopService();
        }
    }
}
