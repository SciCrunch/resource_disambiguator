package org.neuinfo.resource.disambiguator.classification;

import bnlpkit.nlp.tools.sentence.SentenceLexer2;
import bnlpkit.nlp.tools.sentence.TokenInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bozyurt on 3/29/14.
 */
public class Utils {

    public static List<TokenInfo> toTokens2(String text) throws IOException {
        List<TokenInfo> tiList = new ArrayList<TokenInfo>();
        SentenceLexer2 sl = new SentenceLexer2(text);
        TokenInfo ti;
        while ((ti = sl.getNextTI()) != null) {
            String tok = ti.getTokValue();
            if (tok.length() > 1) {
                if (tok.endsWith(".")) {
                    if (countChar(tok, '.') == 1) {
                        int len = ti.getTokValue().length();
                        TokenInfo ti1 = new TokenInfo(ti.getTokValue()
                                .substring(0, len - 1), ti.getStart(),
                                ti.getEnd() - 1
                        );
                        tiList.add(ti1);
                        TokenInfo ti2 = new TokenInfo(".", ti.getEnd() - 1,
                                ti.getEnd());
                        tiList.add(ti2);
                    }
                } else {
                    tiList.add(ti);
                }
            }
        }
        return tiList;
    }

    /**
     * do stopword removal and lowercasing also. Discard single char tokens.
     *
     * @param text
     * @return
     * @throws IOException
     */
    public static List<TokenInfo> toTokens(String text) throws IOException {
        List<TokenInfo> tiList = new ArrayList<TokenInfo>();
        SentenceLexer2 sl = new SentenceLexer2(text);
        TokenInfo ti = null;
        while ((ti = sl.getNextTI()) != null) {
            String tok = ti.getTokValue().toLowerCase();
            if (tok.length() > 1 && !stopWordSet.contains(tok)) {
                if (tok.endsWith(".")) {
                    if (countChar(tok, '.') == 1) {
                        int len = ti.getTokValue().length();
                        TokenInfo ti1 = new TokenInfo(ti.getTokValue()
                                .substring(0, len - 1), ti.getStart(),
                                ti.getEnd() - 1
                        );
                        tiList.add(ti1);
                        TokenInfo ti2 = new TokenInfo(".", ti.getEnd() - 1,
                                ti.getEnd());
                        tiList.add(ti2);
                    }
                } else {
                    tiList.add(ti);
                }
            }
        }
        return tiList;
    }

    static int countChar(String s, char c) {
        int len = s.length(), count = 0;
        for (int i = 0; i < len; i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    public static final Set<String> stopWordSet = new HashSet<String>();

    public static final String[] stopWords = new String[]{"a", "able",
            "about", "across", "after", "all", "almost", "also", "am", "among",
            "an", "and", "any", "are", "as", "at", "be", "because", "been",
            "but", "by", "can", "cannot", "could", "dear", "did", "do", "does",
            "either", "else", "ever", "every", "for", "from", "get", "got",
            "had", "has", "have", "he", "her", "hers", "him", "his", "how",
            "however", "i", "if", "in", "into", "is", "it", "its", "just",
            "least", "let", "like", "likely", "may", "me", "might", "most",
            "must", "my", "neither", "no", "nor", "not", "of", "off", "often",
            "on", "only", "or", "other", "our", "own", "rather", "said", "say",
            "says", "she", "should", "since", "so", "some", "than", "that",
            "the", "their", "them", "then", "there", "these", "they", "this",
            "tis", "to", "too", "twas", "us", "wants", "was", "we", "were",
            "what", "when", "where", "which", "while", "who", "whom", "why",
            "will", "with", "would", "yet", "you", "your"};

    static {
        for (String sw : stopWords) {
            stopWordSet.add(sw);
        }
    }

}
