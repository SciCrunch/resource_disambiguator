package org.neuinfo.resource.disambiguator.nlp;

import bnlpkit.nlp.common.Node;

/**
 * Created by bozyurt on 9/20/14.
 */
public interface ISyntacticParser {
    public Node parseSentence(String sentence) throws Exception;
}
