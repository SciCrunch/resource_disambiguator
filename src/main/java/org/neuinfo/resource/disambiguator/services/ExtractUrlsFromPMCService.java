package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Paper;
import org.neuinfo.resource.disambiguator.model.PaperPath;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Assertion;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.XMLReader;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.neuinfo.resource.disambiguator.util.Assertion.assertNotNull;

/**
 * 01extract_url_pmc.pl
 *
 * @author bozyurt
 */
public class ExtractUrlsFromPMCService implements IExtractUrlsFromPMCService {
    private String batchNum;
    private String indexRootDir;
    static Logger log = Logger.getLogger(ExtractUrlsFromPMCService.class);

    @Inject
    @IndicatesPrimaryJpa
    Provider<EntityManager> emFactory = null;

    static Pattern urlPattern1 = Pattern
            .compile("(http.+?)((\\.\\s)|\"|<|\\s|\\(|\\)|\\[|\\])");
    static Pattern urlPattern2 = Pattern
            .compile("\\s(\\w+?\\.\\w+?\\.\\w{3})((\\.\\s)|\"|<|\\s|\\(|\\)|\\[|\\])");

    public ExtractUrlsFromPMCService() {
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.neuinfo.resource.disambiguator.services.IExtractUrlsFromPMCService
     * #extractUrls()
     */
    @Override
    public void extractUrls(String batchNum) throws Exception {
        this.batchNum = batchNum;
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");

        indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchNum);
        assert baseDir.isDirectory();
        File[] journals = baseDir.listFiles();
        long start = System.currentTimeMillis();
        int baseDirLen = baseDir.getAbsolutePath().length();
        log.info("starting extracting urls from batch " + batchNum);
        assertNotNull(journals);
        List<File> filePaths = new ArrayList<File>(1000);
        int count = 0;
        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            getPapers(journalDir, papers);
            filePaths.clear();
            for (File paperPath : papers) {
                System.out.println("handling " + paperPath);
                if (paperPath.getAbsolutePath().indexOf("%20") != -1) {
                    System.out.println("Bad file name found! skipping " + paperPath.getAbsolutePath());
                    continue;
                }
                filePaths.add(paperPath);
                count++;
                if ((count % 1000) == 0) {
                    log.info("Papers handled so far:" + count);
                }
                if (isPaperProcessedBefore(paperPath, baseDirLen)) {
                    log.info("Already processed:" + paperPath);
                    continue;
                }
                PaperUrls pu = extractURLs(paperPath);
                if (!pu.urls.isEmpty()) {
                    log.info(pu);
                    savePaperURLs(pu);
                }
            }
            savePaperPaths(filePaths, baseDirLen);
        }
        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished extracting urls from batch " + batchNum);
        log.info("---------------------------------------------------");
    }

    public void extractUrls4Year(String batchNum, String year) throws Exception {
        this.batchNum = batchNum;
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");

        indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchNum);
        assert baseDir.isDirectory();
        File[] journals = baseDir.listFiles();
        long start = System.currentTimeMillis();
        int baseDirLen = baseDir.getAbsolutePath().length();
        log.info("starting extracting urls from batch " + batchNum);
        assertNotNull(journals);
        List<File> filePaths = new ArrayList<File>(1000);
        int count = 0;
        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            getPapers(journalDir, papers);
            filePaths.clear();
            for (Iterator<File> it = papers.iterator(); it.hasNext(); ) {
                File f = it.next();
                if (f.getName().indexOf("_" + year) == -1) {
                    it.remove();
                }
            }

            for (File paperPath : papers) {
                System.out.println("handling " + paperPath);
                filePaths.add(paperPath);
                count++;
                if ((count % 1000) == 0) {
                    log.info("Papers handled so far:" + count);
                }
                PaperUrls pu = extractURLs(paperPath);
                if (!pu.urls.isEmpty()) {
                    log.info(pu);
                    savePaperURLs(pu);
                }
            }
            savePaperPaths(filePaths, baseDirLen);
        }
        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished extracting urls from batch " + batchNum);
        log.info("---------------------------------------------------");
    }

    public void extractUrlsTest(String batchNum) throws Exception {
        this.batchNum = batchNum;
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");

        indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchNum);
        assert baseDir.isDirectory();
        File[] journals = baseDir.listFiles();
        long start = System.currentTimeMillis();
        int baseDirLen = baseDir.getAbsolutePath().length();
        log.info("starting extracting urls from batch " + batchNum);
        assertNotNull(journals);
        List<File> filePaths = new ArrayList<File>(1000);
        int count = 0;
        int notProcessed = 0;
        int accountedRefUrlCount = 0;
        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            getPapers(journalDir, papers);
            filePaths.clear();
            for (File paperPath : papers) {
                count++;
                System.out.println("handling " + paperPath);
                filePaths.add(paperPath);
                if (isPaperProcessedBefore(paperPath, baseDirLen)) {
                    log.info("Already processed:" + paperPath);
                    PaperUrls pu = extractURLs(paperPath);
                    System.out.println(pu);
                    if (!pu.urls.isEmpty()) {
                        boolean ok = savePaperURLsTest(pu, "http://sourceforge.net");
                        if (ok) {
                            accountedRefUrlCount++;
                        } else {
                            System.out.println("*** NOT FOUND");
                            extractURLs(paperPath);
                        }
                    }
                } else {
                    notProcessed++;
                }
            }
        }
        System.out.println("count:" + count + " notProcessed:" + notProcessed + " accountedRefUrlCount:" + accountedRefUrlCount);
    }

    public void updateContextInfoForUrls(String batchNum) throws Exception {
        this.batchNum = batchNum;
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");

        indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchNum);
        assert baseDir.isDirectory();
        int baseDirLen = baseDir.getAbsolutePath().length();
        File[] journals = baseDir.listFiles();

        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            getPapers(journalDir, papers);
            for (File paperPath : papers) {
                if (isPaperProcessedBefore(paperPath, baseDirLen)) {
                    log.info("Already processed:" + paperPath);
                    continue;
                }
                PaperUrls pu = extractURLs(paperPath);
                if (!pu.urls.isEmpty()) {
                    log.info(pu);
                    updatePaperURLContext(pu);
                }
            }
        }
    }

    private boolean isPaperProcessedBefore(File paperPath, int baseDirLen) {
        String relPath = paperPath.getAbsolutePath().substring(baseDirLen);
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<PaperPath> q = em.createQuery(
                    "from PaperPath p  where p.filePath = :path and p.flags = 0",
                    PaperPath.class);
            List<PaperPath> ppList = q.setParameter("path",
                    relPath).getResultList();
            if (ppList.isEmpty()) {
                TypedQuery<Paper> query = em.createQuery(
                        "from Paper p  where p.filePath = :path",
                        Paper.class);
                List<Paper> paperList = query.setParameter("path",
                        paperPath.getAbsolutePath()).getResultList();

                return (!paperList.isEmpty());
            }
            return true;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void getPapers(File journalDir, List<File> paperList) {
        File[] files = journalDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    getPapers(f, paperList);
                } else {
                    paperList.add(f);
                }
            }
        }
    }

    void updatePaperURLContext(PaperUrls pu) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            Paper paper = getMatchingPaper(em, pu.getPMID());

            if (paper != null) {
                for (URLLocInfo url : pu.urls) {
                    // O(n^2) is OK under the assumption # of urls in a paper is
                    // small (< 10)
                    URLRec theUR = null;
                    for (URLRec ur : paper.getUrls()) {
                        if (ur.getUrl().equals(url.getUrl())
                                && ur.getBatchId().equals(this.batchNum)) {
                            theUR = ur;
                            break;
                        }
                    }
                    if (theUR != null) {
                        theUR.setContext(url.getTextOccured());
                        em.merge(theUR);
                    }
                }
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        }

    }

    void savePaperPaths(List<File> paperPaths, int baseDirLen) throws Exception {
        EntityManager em = null;
        try {
            log.info("savePaperPaths -- start " + paperPaths.size());
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            for (File paperPath : paperPaths) {
                String relPath = paperPath.getAbsolutePath().substring(baseDirLen);
                TypedQuery<PaperPath> query = em.createQuery(
                        "from PaperPath p  where p.filePath = :path",
                        PaperPath.class);
                List<PaperPath> ppList = query.setParameter("path",
                        relPath).getResultList();
                if (ppList.isEmpty()) {
                    PaperPath pp = new PaperPath();
                    pp.setFilePath(relPath);
                    pp.setFlags(0);
                    em.persist(pp);
                }
            }
            Utils.commitTransaction(em);
            log.info("savePaperPaths -- end");
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        } finally {
            Utils.closeEntityManager(em);
        }

    }

    boolean savePaperURLsTest(PaperUrls pu, String refURL) throws Exception {
        EntityManager em = null;
        boolean foundRefUrl = false;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            Paper paper = getMatchingPaper(em, pu.getPMID());
            if (paper == null) {
                System.out.println("No matching paper:>>" + pu.getPMID());
            } else {
                for (URLLocInfo url : pu.urls) {
                    boolean found = false;
                    // O(n^2) is OK under the assumption # of urls in a paper is
                    // small (< 10)
                    for (URLRec ur : paper.getUrls()) {
                        if (url.getUrl().startsWith(refURL)) {
                            foundRefUrl = true;
                        }
                        if (ur.getUrl().equals(url.getUrl())) {
                            found = true;

                            break;
                        }
                    }

                    if (!found) {
                        System.out.println("new URL:" + url);
                    }
                }
            }

            Utils.commitTransaction(em);
            return foundRefUrl;
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
        return false;
    }

    void savePaperURLs(PaperUrls pu) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            Paper paper = getMatchingPaper(em, pu.getPMID());
            if (paper == null) {
                paper = new Paper();
                paper.setFilePath(pu.getFilePath());
                paper.setPubmedId(pu.getPMID());
                paper.setPmcId(pu.getPMCID());
                paper.setTitle(pu.getTitle());
                paper.setJournalTitle(pu.getJournalTitle());

                em.persist(paper);

                for (URLLocInfo url : pu.urls) {
                    URLRec ur = new URLRec();
                    ur.setBatchId(batchNum);
                    ur.setPaper(paper);
                    ur.setUrl(url.getUrl());
                    ur.setContext(url.getTextOccured());
                    em.persist(ur);
                }
            } else {
                for (URLLocInfo url : pu.urls) {
                    boolean found = false;
                    // O(n^2) is OK under the assumption # of urls in a paper is
                    // small (< 10)
                    for (URLRec ur : paper.getUrls()) {
                        // && ur.getBatchId().equals(this.batchNum))
                        if (ur.getUrl().equals(url.getUrl())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        URLRec ur = new URLRec();
                        ur.setBatchId(batchNum);
                        ur.setPaper(paper);
                        ur.setUrl(url.getUrl());
                        ur.setContext(url.getTextOccured());
                        em.persist(ur);
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

    Paper getMatchingPaper(EntityManager em, String pmid) {
        TypedQuery<Paper> query = em
                .createQuery(
                        "from Paper p LEFT JOIN FETCH p.urls ur where p.pubmedId = :pmid",
                        Paper.class).setParameter("pmid", pmid);
        List<Paper> resultList = query.getResultList();

        return (!resultList.isEmpty() ? resultList.get(0) : null);
    }

    public static String normalizeUrl(String url) {
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.replaceFirst("/$", "");
        }
        if (url.endsWith(".")) {
            url = url.substring(0, url.length() - 1);
        }
        url = url.trim();
        return url;
    }

    /**
     * We need real URLs which point to sources not to the journal or metadata
     * or non-text such as image, pdf etc, also check if the url is formatted
     * correctly
     *
     * @param url
     * @return
     */
    public static boolean isBadUrl(String url) {
        boolean bad = (url.indexOf("www.w3.org") != -1
                || url.indexOf("creativecommons") != -1
                || url.indexOf("dx.doi") != -1
                || url.indexOf("www.elsevier.com") != -1 || !Utils
                .isHtmlPage(url));
        if (!bad) {
            bad = !Utils.isValidURLFormat(url);
        }
        return bad;
    }

    /**
     * we need to set some schema params because paper XML not well formed and
     * missing dtd
     *
     * @param paperPath
     * @return
     * @throws Exception
     */
    public PaperUrls extractURLs(File paperPath) throws Exception {
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
                paperPath.getAbsolutePath());
        xmlReader.setContentHandler(handler);


        xmlReader.parse(Utils.convertToFileURL(paperPath.getAbsolutePath()));
        String text = handler.getText();
        List<String> paraList = handler.getParaList();

        Set<String> uniqSet = new HashSet<String>();
        List<URLLocInfo> masterULIList = new ArrayList<URLLocInfo>();

        for (String para : paraList) {
            Matcher matcher = urlPattern1.matcher(para);

            while (matcher.find()) {
                String url = normalizeUrl(matcher.group(1));
                if (!uniqSet.contains(url) && !isBadUrl(url)) {
                    Assertion.assertNotEmpty(para, "paragraph was empty");
                    masterULIList.add(new URLLocInfo(url, para));
                    uniqSet.add(url);
                }
            }

            matcher = urlPattern2.matcher(text);
            while (matcher.find()) {
                String url = normalizeUrl(matcher.group(1));
                if (!uniqSet.contains(url) && !isBadUrl(url)) {
                    Assertion.assertNotEmpty(para, "paragraph was empty");
                    masterULIList.add(new URLLocInfo(url, para));
                    uniqSet.add(url);
                }
            }

        } // para
        for (URLLocInfo uli : handler.getUliList()) {
            String url = uli.getUrl();
            url = normalizeUrl(url);
            if (!uniqSet.contains(url) && !isBadUrl(url)) {
                // Assertion.assertNotEmpty(uli.getTextOccured(),
                // "ULI paragraph was empty");
                masterULIList.add(new URLLocInfo(url, uli.getTextOccured()));
                uniqSet.add(url);
            }
        }

        return new PaperUrls(handler.getPMID(), handler.getPMCID(),
                paperPath.getAbsolutePath(), masterULIList, handler
                .getArticleInfo().getTitle(), handler.getArticleInfo()
                .getJournalTitle());
    }

    public static void testDrive() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setFeature("http://xml.org/sax/features/validation", false);

        String path = "/var/indexes/PMC_OAI_201310/Hum_Brain_Mapp/Hum_Brain_Mapp_2012_May_23_33(5)_1212-1224.nxml";
        SAXParser parser = factory.newSAXParser();

        XMLReader xmlReader = parser.getXMLReader();
        Paper2TextHandler handler = new Paper2TextHandler(path);
        xmlReader.setContentHandler(handler);

        xmlReader.parse(Utils.convertToFileURL(path));
        String text = handler.getText();
        Matcher matcher = urlPattern1.matcher(text);
        List<String> urls = new ArrayList<String>();
        while (matcher.find()) {
            urls.add(normalizeUrl(matcher.group(1)));
        }

        matcher = urlPattern2.matcher(text);
        while (matcher.find()) {
            urls.add(normalizeUrl(matcher.group(1)));
        }
        for (String url : urls) {
            log.info("url:" + url);
        }

        log.info(handler.getUrls());

        log.info("PMID:" + handler.getPMID());
    }

    public static class URLLocInfo {
        final String url;
        final String textOccured;

        public URLLocInfo(String url, String textOccured) {
            super();
            this.url = url;
            this.textOccured = textOccured;
        }

        public String getUrl() {
            return url;
        }

        public String getTextOccured() {
            return textOccured;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("URLLocInfo [");
            if (url != null) {
                builder.append("url=");
                builder.append(url);
                builder.append(", ");
            }
            if (textOccured != null) {
                builder.append("textOccured=");
                builder.append(textOccured);
            }
            builder.append("]");
            return builder.toString();
        }

    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ExtractUrlsFromPMCService", options);
        System.exit(1);
    }

    public static void cli(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option myOption = Option.builder("d").required().hasArg()
                .argName("monthYear").desc("monthYear [eg. 201310]").build();
        Option cmdOption = Option.builder("u")
                .desc("update context data of the existing urls").build();
        Option yearOption = Option.builder("y").hasArg().argName("year")
                .desc("the year [e.g. 2014] for papers to include").build();
        Options options = new Options();
        options.addOption(help);
        options.addOption(myOption);
        options.addOption(cmdOption);
        options.addOption(yearOption);
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
        String monthYear = line.getOptionValue("d");
        Injector injector = Guice.createInjector(new RDPersistModule());

        ExtractUrlsFromPMCService service = new ExtractUrlsFromPMCService();

        injector.injectMembers(service);
        String year = null;
        if (line.hasOption('y')) {
            year = line.getOptionValue('y');
        }
        try {
            if (line.hasOption("u")) {
                service.updateContextInfoForUrls(monthYear);
            } else if (year != null) {
                service.extractUrls4Year(monthYear, year);
            } else {
                service.extractUrls(monthYear);
            }
        } finally {
            JPAInitializer.stopService();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
        // testDriver();
    }

    static void testDriver() throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());

        ExtractUrlsFromPMCService service = new ExtractUrlsFromPMCService();

        injector.injectMembers(service);

        try {
            service.extractUrlsTest("201407");
        } finally {
            JPAInitializer.stopService();
        }
    }
}
