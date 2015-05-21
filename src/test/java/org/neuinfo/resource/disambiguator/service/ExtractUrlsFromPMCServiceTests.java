package org.neuinfo.resource.disambiguator.service;

import junit.framework.TestSuite;
import org.neuinfo.resource.disambiguator.BaseTestCase;
import org.neuinfo.resource.disambiguator.model.Paper;
import org.neuinfo.resource.disambiguator.services.ExtractUrlsFromPMCService;
import org.neuinfo.resource.disambiguator.services.Paper2TextHandler;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author bozyurt
 */
public class ExtractUrlsFromPMCServiceTests extends BaseTestCase {
    static Pattern urlPattern1 = Pattern
            .compile("(http.+?)((\\.\\s)|\"|<|\\s|\\(|\\)|\\[|\\])");
    static Pattern urlPattern2 = Pattern
            .compile("\\s(\\w+?\\.\\w+?\\.\\w{3})((\\.\\s)|\"|<|\\s|\\(|\\)|\\[|\\])");

    public ExtractUrlsFromPMCServiceTests(String testName) {
        super(testName);
    }

    public void testExtractUrls() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setFeature("http://xml.org/sax/features/validation", false);

        String path = "/var/indexes/PMC_OAI_201310/Hum_Brain_Mapp/Hum_Brain_Mapp_2012_May_23_33(5)_1212-1224.nxml";

        path = "/var/indexes/PMC_OAI_201310/BMC_Med/BMC_Med_2011_Apr_8_9_34.nxml";
        SAXParser parser = factory.newSAXParser();

        XMLReader xmlReader = parser.getXMLReader();
        Paper2TextHandler handler = new Paper2TextHandler(path);
        xmlReader.setContentHandler(handler);

        xmlReader.parse(Utils.convertToFileURL(path));
        String text = handler.getText();
        Matcher matcher = urlPattern1.matcher(text);
        List<String> urls = new ArrayList<String>();
        while (matcher.find()) {
            urls.add(ExtractUrlsFromPMCService.normalizeUrl(matcher.group(1)));
        }

        matcher = urlPattern2.matcher(text);
        while (matcher.find()) {
            urls.add(ExtractUrlsFromPMCService.normalizeUrl(matcher.group(1)));
        }
        for (String url : urls) {
            System.out.println("url:" + url);
            System.out.println("Variations\n--------------");
            if (url.indexOf("www.") != -1) {
                int idx = url.indexOf("www.");
                String url1 = url.substring(idx + 4);
                url1 = "http://" + url1;
                System.out.println("\t" + url1);
            }
            int dotCount = Utils.numOfMatches(url, '.');
            if (dotCount == 1) {
                String url1 = url.replaceFirst("http://", "http://www.");
                url1 = "http://" + url1;
                System.out.println("\t" + url1);
            }


        }

        System.out.println(handler.getUrls());

        System.out.println("PMID:" + handler.getPMID());
    }

    public void testPaperDuplicationCheck() throws Exception {
        EntityManager em = emFactory.get();
        String batchId = "201310";
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");
        String indexRootDir = props.getProperty("index.rootdir");
        File baseDir = new File(indexRootDir, "PMC_OAI_" + batchId);
        File[] journals = baseDir.listFiles();
        int processedCount = 0;
        int totCount = 0;
        assertNotNull(journals);
        for (File journalDir : journals) {
            List<File> papers = new ArrayList<File>();
            ExtractUrlsFromPMCService.getPapers(journalDir, papers);
            for (File paperPath : papers) {
                TypedQuery<Paper> query = em.createQuery(
                        "from Paper p  where p.filePath = :path",
                        Paper.class);
                List<Paper> paperList = query.setParameter("path", paperPath.getAbsolutePath()).getResultList();
                if (!paperList.isEmpty()) {
                    Paper p = paperList.get(0);
                    System.out.println("Already processed: " + p);
                    processedCount++;
                }
                totCount++;
            }
            if (processedCount > 5) {
                break;
            }
        }

        System.out.printf("processedCount:%d totCount:%d%n", processedCount, totCount);
    }

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new ExtractUrlsFromPMCServiceTests("testExtractUrls"));
        //suite.addTest(new ExtractUrlsFromPMCServiceTests("testPaperDuplicationCheck"));
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

}
