package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.nlp.common.index.DocumentInfo;
import bnlpkit.nlp.common.index.FileInfo;
import bnlpkit.nlp.common.index.SentenceInfo;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.neuinfo.resource.disambiguator.nlp.OpenNLPParser;

/**
 * Created by bozyurt on 10/2/14.
 */
public class SyntacticParserTests extends TestCase {

    public SyntacticParserTests(String name) {
        super(name);
    }


    public void testOpenNLPParsingPerformance() throws Exception {
        String idxXmlFile = "/var/data/antibody_literature_ner/antibody/PMID_9885254_ner_lt.xml";
        FileInfo fi = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);

        OpenNLPParser parser = new OpenNLPParser(200);
        long startTime = System.currentTimeMillis();
        for(DocumentInfo di : fi.getDiList()) {
            for(SentenceInfo si : di.getSiList()) {
                String sentence = si.getText().getText();
                try {
                    parser.parseSentence(sentence);
                } catch(Throwable t) {
                    // no op
                }
            }
        }
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("elapsed time (msecs) :" + elapsed);
    }


    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new SyntacticParserTests("testOpenNLPParsingPerformance"));
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
