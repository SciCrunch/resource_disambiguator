package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.nlp.common.index.DocumentInfo;
import bnlpkit.nlp.common.index.FileInfo;
import bnlpkit.nlp.common.index.SentenceInfo;

import java.util.List;

/**
 * Created by bozyurt on 8/28/14.
 */
public class IdxModelHelper {
    private FileInfo theFI;

    public IdxModelHelper(String idxXmlFile) throws Exception {
        theFI = new FileInfo(idxXmlFile, CharSetEncoding.UTF8);
    }

    public List<SentenceInfo>  getSentencesForDoc(int docIdx) {
        for (DocumentInfo di : theFI.getDiList()) {
            if (di.getDocIdx() == docIdx) {
                return di.getSiList();
            }
        }
        return null;
    }
}
