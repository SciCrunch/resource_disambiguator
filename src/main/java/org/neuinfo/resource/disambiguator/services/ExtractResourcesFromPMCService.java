package org.neuinfo.resource.disambiguator.services;

import bnlpkit.nlp.common.index.DocumentInfo;
import bnlpkit.nlp.common.index.FileInfo;
import bnlpkit.nlp.common.index.NEInfo;
import bnlpkit.nlp.common.index.SentenceInfo;
import bnlpkit.util.nif.ner.FastNamedEntityRecognizer;
import bnlpkit.util.nif.ner.NERConfig;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.NERPaperPath;
import org.neuinfo.resource.disambiguator.model.Paper;
import org.neuinfo.resource.disambiguator.model.ResourceRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.PaperUrls.ResourceInfo;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.XMLReader;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author bozyurt
 */
public class ExtractResourcesFromPMCService {
    static Logger log = Logger.getLogger(ExtractResourcesFromPMCService.class);

    @Inject
    @IndicatesPrimaryJpa
    Provider<EntityManager> emFactory = null;

    public ExtractResourcesFromPMCService() {
    }

    public void extractResources(String batchNum) throws Exception {
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");

        String indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchNum);
        assert baseDir.isDirectory();
        int baseDirLen = baseDir.getAbsolutePath().length();
        File[] journals = baseDir.listFiles();
        long start = System.currentTimeMillis();
        NERConfig config = new NERConfig();
        config.setCaseNo("case1s");
        config.setTagMode(NERConfig.TagMode.RESOURCE);
        // load resource lookup table from the model jar
        config.setResourceLTFile("ner/models/resources.txt");
        FastNamedEntityRecognizer fast = new FastNamedEntityRecognizer(config);
        fast.setSuppressChunkingUse(true);
        fast.setVerbose(true);

        fast.initialize();
        log.info("starting extracting resources from batch " + batchNum);
        int count = 0;
        int papersWithResourceCount = 0;
        List<File> filePaths = new ArrayList<File>(1000);
        assert journals != null;
        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            getPapers(journalDir, papers);
            filePaths.clear();
            for (File paperPath : papers) {
                System.out.println("handling " + paperPath);

                count++;
                if ((count % 100) == 0) {
                    log.info("Papers handled so far:" + count);
                }
                if (isPaperProcessedBefore(paperPath, baseDirLen)) {
                    log.info("Already processed:" + paperPath);
                    continue;
                }
                filePaths.add(paperPath);
                PaperUrls pu = extractResources(paperPath, fast);
                if (pu != null) {
                    savePaperResources(pu);
                    papersWithResourceCount++;
                }
            }
            savePaperPaths(filePaths, baseDirLen);
        }
        long diff = System.currentTimeMillis() - start;
        log.info("Elapsed time (secs): " + (diff / 1000.0));
        log.info("Finished extracting resources from batch " + batchNum);
        log.info("---------------------------------------------------");
    }


    public PaperUrls extractResources(File paperPath, FastNamedEntityRecognizer fast) throws Exception {
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
                paperPath.getAbsolutePath(), Paper2TextHandler.OpType.NER);
        xmlReader.setContentHandler(handler);

        xmlReader.parse(Utils.convertToFileURL(paperPath.getAbsolutePath()));

        String text = handler.getText();

        FileInfo fi = fast.handleInMem(text);
        boolean hasNE = false;
        for (DocumentInfo di : fi.getDiList()) {
            for (SentenceInfo si : di.getSiList()) {
                if (si.hasNamedEntities()) {
                    hasNE = true;
                    break;
                }
            }
        }

        if (hasNE) {
            PaperUrls pu = new PaperUrls(handler.getPMID(), handler.getPMCID(),
                    paperPath.getAbsolutePath(), null, handler
                    .getArticleInfo().getTitle(), handler.getArticleInfo()
                    .getJournalTitle());
            for (DocumentInfo di : fi.getDiList()) {
                for (SentenceInfo si : di.getSiList()) {
                    if (si.hasNamedEntities()) {
                        String sentence = si.getText().getText();
                        for (NEInfo nei : si.getNeList()) {
                            String entity = nei.extractNE(sentence);
                            ResourceInfo ri = new ResourceInfo(entity, nei.getStartIdx(),
                                    nei.getEndIdx(), sentence);
                            pu.addResource(ri);
                        }

                    }
                }
            }
            return pu;
        }
        return null;
    }

    void savePaperResources(PaperUrls pu) throws Exception {
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
                for (ResourceInfo ri : pu.getResourceList()) {
                    ResourceRec rr = new ResourceRec();
                    rr.setContext(ri.getSentence());
                    rr.setEntity(ri.getEntity());
                    rr.setPaper(paper);
                    rr.setStartIdx(ri.getStartIdx());
                    rr.setEndIdx(ri.getEndIdx());
                    em.persist(rr);
                }
            } else {
                for (ResourceInfo ri : pu.getResourceList()) {
                    boolean found = false;
                    for (ResourceRec rr : paper.getResourceRefs()) {
                        if (rr.getEntity().equals(ri.getEntity())
                                && rr.getStartIdx() == ri.getStartIdx()
                                && rr.getEndIdx() == ri.getEndIdx()) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        ResourceRec rr = new ResourceRec();
                        rr.setContext(ri.getSentence());
                        rr.setEntity(ri.getEntity());
                        rr.setPaper(paper);
                        rr.setStartIdx(ri.getStartIdx());
                        rr.setEndIdx(ri.getEndIdx());
                        em.persist(rr);
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
        TypedQuery<Paper> query = em.createQuery(
                "from Paper p LEFT JOIN FETCH p.resourceRefs rr where p.pubmedId = :pmid", Paper.class);
        query.setParameter("pmid", pmid);
        List<Paper> resultList = query.getResultList();

        return (!resultList.isEmpty() ? resultList.get(0) : null);
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

    private boolean isPaperProcessedBefore(File paperPath, int baseDirLen) {
        String relPath = paperPath.getAbsolutePath().substring(baseDirLen);
        EntityManager em = null;
        try {
            log.info("checking if paper is processed before " + relPath);
            em = Utils.getEntityManager(emFactory);
            TypedQuery<NERPaperPath> q = em.createQuery(
                    "from NERPaperPath p where p.filePath = :path",
                    NERPaperPath.class).setParameter("path", relPath);
            final List<NERPaperPath> ppList = q.getResultList();
            return !ppList.isEmpty();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void savePaperPaths(List<File> paperPaths, int baseDirLen) throws Exception {
        EntityManager em = null;
        try {
            int count = 0;
            log.info("savePaperPaths -- start");
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            for (File paperPath : paperPaths) {
                String relPath = paperPath.getAbsolutePath().substring(baseDirLen);
                TypedQuery<NERPaperPath> query = em.createQuery(
                        "from NERPaperPath p where  p.filePath = :path",
                        NERPaperPath.class);
                final List<NERPaperPath> ppList = query.setParameter("path", relPath).getResultList();
                if (ppList.isEmpty()) {
                    NERPaperPath pp = new NERPaperPath();
                    pp.setFilePath(relPath);
                    pp.setFlags(1);
                    em.persist(pp);
                    count++;
                }
            }
            Utils.commitTransaction(em);
            log.info("savePaperPaths -- saved " + count);
            log.info("savePaperPaths -- end");
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ExtractResourcesFromPMCService", options);
        System.exit(1);
    }

    public static void cli(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option myOption = Option.builder("d").required().hasArg()
                .argName("monthYear").desc("monthYear [eg. 201310]").build();
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
        String monthYear = line.getOptionValue("d");
        Injector injector = Guice.createInjector(new RDPersistModule());

        ExtractResourcesFromPMCService service = new ExtractResourcesFromPMCService();

        try {
            injector.injectMembers(service);

            service.extractResources(monthYear);
        } finally {
            JPAInitializer.stopService();
        }
    }

    public static void testDriver() throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());
        ExtractResourcesFromPMCService service = new ExtractResourcesFromPMCService();
        String batchId = "201401";
        try {
            injector.injectMembers(service);

            service.extractResources(batchId);
        } finally {
            JPAInitializer.stopService();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
       // testDriver();
    }
}
