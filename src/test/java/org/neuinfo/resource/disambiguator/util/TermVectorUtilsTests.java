package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.index.SentenceInfo;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.neuinfo.resource.disambiguator.util.summary.RogueUtils;

import java.util.List;

/**
 * Created by bozyurt on 8/28/14.
 */
public class TermVectorUtilsTests extends TestCase {
    public static final String HOME_DIR = System.getProperty("user.home");

    public TermVectorUtilsTests(String testName) {
        super(testName);
    }


    public void testTokenizeBigrams() throws Exception {
        IdxModelHelper mixin = new IdxModelHelper(HOME_DIR + "/etc/summarization_gs_idx.xml");
        List<SentenceInfo> siList = mixin.getSentencesForDoc(0);
        StringBuilder contentBuf = new StringBuilder(1024);

        for (SentenceInfo si : siList) {
            contentBuf.append(si.getText().getText()).append(' ');
        }
        String content = contentBuf.toString().trim();
        contentBuf = null;
        List<String> bigrams = TermVectorUtils.tokenizeBigrams(content);

        assertNotNull(bigrams);
        assertFalse(bigrams.isEmpty());

        for (String bigram : bigrams) {
            System.out.println(bigram);
        }

        System.out.println("rouge2:" + RogueUtils.calcRogue2(content, content, false));
    }

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new TermVectorUtilsTests("testTokenizeBigrams"));
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

}