package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.tools.sentence.SentenceLexer2;
import bnlpkit.nlp.tools.sentence.TokenInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bozyurt on 9/20/14.
 */
public class TokenizerUtils {

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
