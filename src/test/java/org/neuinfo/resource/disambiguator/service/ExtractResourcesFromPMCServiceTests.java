package org.neuinfo.resource.disambiguator.service;

import bnlpkit.nlp.sbt.Lexer;
import bnlpkit.nlp.tools.sentence.SentenceLexer2;
import bnlpkit.nlp.tools.sentence.TokenInfo;
import bnlpkit.util.GenUtils;
import junit.framework.TestSuite;


import org.neuinfo.resource.disambiguator.BaseTestCase;
import org.neuinfo.resource.disambiguator.services.ExtractResourcesFromPMCService;
import org.neuinfo.resource.disambiguator.services.Paper2TextHandler;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bozyurt on 12/19/13.
 */
public class ExtractResourcesFromPMCServiceTests extends BaseTestCase {
    public ExtractResourcesFromPMCServiceTests(String testName) {
        super(testName);
    }


    public void testExtractResources() throws Exception {
        ExtractResourcesFromPMCService service = new ExtractResourcesFromPMCService();
        this.injector.injectMembers(service);
        String batchId = "201310";
        service.extractResources(batchId);
    }

    public void testLexer() throws Exception {
        String sentence = "Genome sequence data have been deposited at the " +
                "European Genome-Phenome Archive ( http://www.ebi.ac.uk/ega/ at the EBI) with accession number " +
                "EGAD00001000138. SNP6 array data have been deposited with ArrayExpress Archive (EBI, accession " +
                "number E-MTAB-1087).";
        List<TokenInfo> tiList = toTokens(sentence);
        for(TokenInfo ti : tiList) {
            System.out.println(ti);
        }

    }

    static List<TokenInfo> toTokens(String sentence)
			throws IOException {
		List<TokenInfo> tiList = new ArrayList<TokenInfo>();
		SentenceLexer2 sl = new SentenceLexer2(sentence);
		TokenInfo ti;
		while ((ti = sl.getNextTI()) != null) {
			tiList.add(ti);
		}
		return tiList;
	}

    public void testMethodSectionExtraction() throws Exception {
        File baseDir = new File("/var/indexes/PMC_OAI_201310/Cell");
        baseDir = new File("/var/indexes/PMC_OAI_201407/Sensors_(Basel)");
        assertTrue(baseDir.isDirectory());
        File[] papers = baseDir.listFiles();
        for (File paperPath : papers) {
            System.out.println("handling " + paperPath);
            String methodText = extractMethodSection(paperPath);
            if (methodText != null && methodText.trim().length() > 0) {
                System.out.println(GenUtils.formatText(methodText, 100));
                break;
            }
        }

    }

    public static String extractMethodSection(File paperPath) throws Exception {
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

        return handler.getText();
    }

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
      //   suite.addTest(new ExtractResourcesFromPMCServiceTests("testExtractResources"));
       //  suite.addTest(new ExtractResourcesFromPMCServiceTests("testLexer"));
          suite.addTest(new ExtractResourcesFromPMCServiceTests("testMethodSectionExtraction"));
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }


}