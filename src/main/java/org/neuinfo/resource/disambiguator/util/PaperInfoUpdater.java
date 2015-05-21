package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Paper;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;
import org.neuinfo.resource.disambiguator.services.Paper2TextHandler;
import org.xml.sax.XMLReader;

import javax.persistence.EntityManager;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * adds paper title / journal title to missing papers
 * Created by bozyurt on 1/14/14.
 */
public class PaperInfoUpdater {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(PaperInfoUpdater.class);


    public void handlePaper(int paperId, String batchId) throws Exception {
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");
        String indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchId);
        int baseDirLen = baseDir.getAbsolutePath().length();
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Paper paper = em.find(Paper.class, paperId);
            String filePath = paper.getFilePath();
            String relPath = filePath.substring(baseDirLen);
            File localFile = new File(baseDir, relPath);
            Assertion.assertNotNull(localFile);
            Paper2TextHandler.ArticleInfo ai = parsePaper(localFile);
            System.out.println(ai);
            if (ai.getTitle() != null) {
                if (paper.getTitle() != null && !paper.getTitle().equals(ai.getTitle())) {
                    updatePaper(paper, ai);
                }
            }

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void handle(String batchId) throws Exception {
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");
        String indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchId);
        int baseDirLen = baseDir.getAbsolutePath().length();
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            // List<Paper> papers = DisambiguatorFinder.getAllPapersWithoutTitle(em);
            List<Paper> papers = DisambiguatorFinder.getAllPapers(em);
            log.info("# papers:" + papers.size());
            int count = 0;
            int badTitleCount = 0;
            int missingTitleCount = 0;
            int localFileCount = 0;
            for (Paper paper : papers) {
                String filePath = paper.getFilePath();
                String relPath = filePath.substring(baseDirLen);
                File localFile = new File(baseDir, relPath);
                // System.out.println(localFile);
                if (localFile.isFile()) {
                    localFileCount++;
                    log.info("parsing " + localFile);
                    try {
                        Paper2TextHandler.ArticleInfo ai = parsePaper(localFile);
                        if (ai.getTitle() != null) {
                            if (paper.getTitle() != null && !paper.getTitle().equals(ai.getTitle())) {
                                updatePaper(paper, ai);
                                badTitleCount++;
                            } else if (paper.getTitle() == null) {
                                updatePaper(paper, ai);
                                missingTitleCount++;
                            }
                        }
                    } catch (Exception x) {
                        x.printStackTrace();

                    }
                }
                ++count;
            }
            log.info("Updated badTitleCount:" + badTitleCount);
            log.info("Updated missingTitleCount:" + missingTitleCount);
            log.info("localFileCount:" + localFileCount);
            log.info("# papers:" + papers.size());
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    private void updatePaper(Paper paper, Paper2TextHandler.ArticleInfo ai) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            paper.setTitle(ai.getTitle());
            paper.setJournalTitle(ai.getJournalTitle());
            em.merge(paper);
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            Utils.rollbackTransaction(em);
            t.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    private Paper2TextHandler.ArticleInfo parsePaper(File path) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        SAXParser parser = factory.newSAXParser();

        XMLReader xmlReader = parser.getXMLReader();
        Paper2TextHandler handler = new Paper2TextHandler(path.getAbsolutePath());
        xmlReader.setContentHandler(handler);

        xmlReader.parse(Utils.convertToFileURL(path.getAbsolutePath()));

        return handler.getArticleInfo();
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            PaperInfoUpdater piu = new PaperInfoUpdater();
            injector.injectMembers(piu);
            if (args.length > 0) {
                String batchId = args[0];
                piu.handle(batchId);
            } else {
               // piu.handlePaper(243392, "201403");
                piu.handle("201403");
            }
        } finally {
            JPAInitializer.stopService();
        }
    }
}
