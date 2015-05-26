package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.nlp.common.index.DocumentInfo;
import bnlpkit.nlp.common.index.SentenceInfo;
import bnlpkit.nlp.common.index.TextInfo;
import bnlpkit.nlp.sbt.SentenceBoundaryClassifierFactory;
import bnlpkit.nlp.sbt.SentenceBoundaryDetector;
import bnlpkit.nlp.sbt.SentenceBoundaryDetector.Config;
import bnlpkit.nlp.tools.sentence.SentenceLexer2;
import bnlpkit.nlp.tools.sentence.TokenInfo;
import bnlpkit.util.FileUtils;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import org.neuinfo.resource.disambiguator.util.HtmlHandler2;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts sentences and applies POS tagging to a given document and creates
 * a <code>DocumentInfo</code> representation for the processed document.
 * <p/>
 * Created by bozyurt on 7/28/14.
 */
public class DocPreprocessor {
    POSTaggerME tagger;
    SentenceBoundaryDetector sbd;

    public DocPreprocessor() throws IOException {
        InputStream pin = null;
        POSModel model;
        try {
            pin = getClass().getClassLoader().getResourceAsStream(
                    "opennlp/models/en-pos-maxent.bin");
            model = new POSModel(pin);
            this.tagger = new POSTaggerME(model);
        } finally {
            FileUtils.close(pin);
        }
        Config config = new Config();
        config.setModelFile(System.getProperty("user.home") + "/dev/java/bnlpkit/preprocess/sbt/classifier.mdl");
        this.sbd = new SentenceBoundaryDetector(config,
                SentenceBoundaryClassifierFactory.SVM_CLASSIFIER, CharSetEncoding.UTF8);
    }


    public DocumentInfo preprocess(String docContent, int docIdx, String nifId) throws Exception {
        List<String> sentences = sbd.tagSentenceBoundaries(docContent, false);
        DocumentInfo di = new DocumentInfo(docIdx, nifId);
        int sentIdx = 1;
        for (String sentence : sentences) {
            prepSentenceInfo(di, sentIdx, sentence, null);
            sentIdx++;
        }
        return di;
    }

    public DocumentInfo preprocess(List<HtmlHandler2.ContentSection> sectionList,
                                   int docIdx, String nifId) throws Exception {
        DocumentInfo di = new DocumentInfo(docIdx, nifId);
        int sentIdx = 1;
        for (HtmlHandler2.ContentSection cs : sectionList) {
            if (cs.getType() == HtmlHandler2.ContentSection.TITLE) {
                String sentence = cs.getContent();
                prepSentenceInfo(di, sentIdx, sentence, "title");
                sentIdx++;
            } else {
                String content = cs.getContent();
                if (content.length() < 40) {
                    prepSentenceInfo(di, sentIdx, content, cs.getType() == HtmlHandler2.ContentSection.PARA ? "para" : null);
                    sentIdx++;
                } else {
                    List<String> sentences = sbd.tagSentenceBoundaries(content, false);
                    String para = null;
                    if (cs.getType() == HtmlHandler2.ContentSection.PARA) {
                        para = "PARA";
                    }
                    boolean first = true;
                    for (String sentence : sentences) {
                        String desc = null;
                        if (para != null && first) {
                            desc = para;
                            first = false;
                        }
                        prepSentenceInfo(di, sentIdx, sentence, desc);
                        sentIdx++;
                    }
                }
            }
        }
        return di;
    }

    void prepSentenceInfo(DocumentInfo di, int sentIdx, String sentence, String desc) throws IOException {
        sentence = toWSDelimTokenSentence(sentence);
        String[] toks = sentence.split("\\s+");
        String[] tags = tagger.tag(toks);
        String pt = toLispNotation(toks, tags);

        SentenceInfo si = new SentenceInfo(di.getDocIdx(), sentIdx,
                new TextInfo(sentence, sentIdx), new TextInfo(pt, sentIdx));
        if (desc != null) {
            si.setDescription(desc);
        }
        di.addSentenceInfo(si, di.getDocIdx());
    }

    public static String toLispNotation(String[] toks, String[] posTags) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("(S1 ");
        for (int i = 0; i < toks.length; i++) {
            sb.append(" (").append(posTags[i]).append(' ')
                    .append(replaceParens(toks[i])).append(')');
        }
        sb.append(")");
        return sb.toString();
    }

    static String replaceParens(String tok) {
        if (tok.equals("(")) {
            return "-LRB-";
        } else if (tok.equals(")")) {
            return "-RRB-";
        }
        return tok;
    }

    public static String toWSDelimTokenSentence(String sentence)
            throws IOException {
        List<TokenInfo> tiList = toTokens(sentence);
        StringBuilder sb = new StringBuilder(sentence.length() + 100);
        for (TokenInfo ti : tiList) {
            sb.append(ti.getTokValue()).append(' ');
        }
        return sb.toString().trim();
    }

    public static List<TokenInfo> toTokens(String sentence)
            throws IOException {
        List<TokenInfo> tiList = new ArrayList<TokenInfo>();
        SentenceLexer2 sl = new SentenceLexer2(sentence);
        TokenInfo ti;
        while ((ti = sl.getNextTI()) != null) {
            tiList.add(ti);
        }
        return tiList;
    }
}
