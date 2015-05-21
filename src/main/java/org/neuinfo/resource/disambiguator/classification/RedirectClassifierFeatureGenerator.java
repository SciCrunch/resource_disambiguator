package org.neuinfo.resource.disambiguator.classification;

import bnlpkit.nlp.common.classification.ExtFeatureType;
import bnlpkit.nlp.common.classification.feature.ExtFeatureManager;
import bnlpkit.nlp.common.classification.feature.InstanceFeatures;
import bnlpkit.nlp.tools.sentence.TokenInfo;
import bnlpkit.nlp.util.SimpleSequentialIDGenerator;

import java.util.*;

/**
 * Created by bozyurt on 3/29/14.
 */
public class RedirectClassifierFeatureGenerator {
    protected ExtFeatureManager featureMan;

    public RedirectClassifierFeatureGenerator(ExtFeatureManager featureMan) {
        this.featureMan = featureMan;
    }

    public List<InstanceFeatures> extractFeatures(List<DataRec> drList) throws Exception {
        List<InstanceFeatures> ifList = new ArrayList<InstanceFeatures>(drList.size());
        Set<String> vocabularySet = new HashSet<String>();
        for (DataRec dr : drList) {
            final List<TokenInfo> tiList = Utils.toTokens2(dr.getContent());
            for (TokenInfo ti : tiList) {
                String tok = ti.getTokValue();
                vocabularySet.add(tok);
            }

        }
        List<String> vocabularyList = new ArrayList<String>(vocabularySet);
        vocabularySet = null;
        Collections.sort(vocabularyList);
        featureMan.addVocabulary(RedirectClassifierFeatureNames.word_bow.toString(), vocabularyList);
        SimpleSequentialIDGenerator idGen = new SimpleSequentialIDGenerator();
        for (DataRec dr : drList) {
            int instanceId = idGen.nextID();
            InstanceFeatures instance = new InstanceFeatures(instanceId);
            final List<TokenInfo> tiList = Utils.toTokens2(dr.getContent());

            featureMan.addFeature(RedirectClassifierFeatureNames.no_toks.toString(),
                    ExtFeatureType.NUMERIC, String.valueOf(tiList.size()), null, instance);
            Map<String, TokenInfo> tiMap = new HashMap<String, TokenInfo>();
            for (TokenInfo ti : tiList) {
                String tok = ti.getTokValue();
                tiMap.put(tok, ti);
            }
            String featureName = RedirectClassifierFeatureNames.word_bow.toString();
            for (TokenInfo ti : tiMap.values()) {
                String tok = ti.getTokValue();
                featureMan.addFeature(featureName, ExtFeatureType.BOW, "1", tok, instance);
            }
            instance.setLabelIdx(0);
            if (dr.getLabel() != null && dr.getLabel().length() > 0) {
                instance.setLabel(dr.getLabel());
                if (dr.getLabel().equals("good")) {
                    instance.setLabelIdx(1);
                } else if (dr.getLabel().equals("bad")) {
                    instance.setLabelIdx(-1);
                }
            }
            ifList.add(instance);
            featureMan.addInstance(instance);

        }
        return ifList;
    }


    public List<InstanceFeatures> extractBigramFeatures(List<DataRec> drList) throws Exception {
        List<InstanceFeatures> ifList = new ArrayList<InstanceFeatures>(drList.size());
        Set<String> vocabularySet = new HashSet<String>();
        for (DataRec dr : drList) {
            final List<TokenInfo> tiList = Utils.toTokens2(dr.getContent());
            for (int i = 1; i < tiList.size(); i++) {
                TokenInfo prevTI = tiList.get(i - 1);
                TokenInfo ti = tiList.get(i);
                String tok = prevTI.getTokValue() + " " + ti.getTokValue();
                vocabularySet.add(tok);
            }
            /*
            for (TokenInfo ti : tiList) {
                String tok = ti.getTokValue();
                vocabularySet.add(tok);
            }
            */


        }
        List<String> vocabularyList = new ArrayList<String>(vocabularySet);
        vocabularySet = null;
        Collections.sort(vocabularyList);
        featureMan.addVocabulary(RedirectClassifierFeatureNames.word_bow.toString(), vocabularyList);
        SimpleSequentialIDGenerator idGen = new SimpleSequentialIDGenerator();
        for (DataRec dr : drList) {
            int instanceId = idGen.nextID();
            InstanceFeatures instance = new InstanceFeatures(instanceId);
            final List<TokenInfo> tiList = Utils.toTokens2(dr.getContent());

            featureMan.addFeature(RedirectClassifierFeatureNames.no_toks.toString(),
                    ExtFeatureType.NUMERIC, String.valueOf(tiList.size()), null, instance);
            Map<String, TokenInfo> tiMap = new HashMap<String, TokenInfo>();
            String featureName = RedirectClassifierFeatureNames.word_bow.toString();
            for (int i = 1; i < tiList.size(); i++) {
                TokenInfo prevTI = tiList.get(i - 1);
                TokenInfo ti = tiList.get(i);
                String tok = prevTI.getTokValue() + " " + ti.getTokValue();
                tiMap.put(tok, ti);
                /*
                tiMap.put(ti.getTokValue(), ti);
                if (i == 1) {
                    tiMap.put(prevTI.getTokValue(), prevTI);
                }
                */

            }
            for (String tok : tiMap.keySet()) {
                featureMan.addFeature(featureName, ExtFeatureType.BOW, "1", tok, instance);
            }
            instance.setLabelIdx(0);
            if (dr.getLabel() != null && dr.getLabel().length() > 0) {
                instance.setLabel(dr.getLabel());
                if (dr.getLabel().equals("good")) {
                    instance.setLabelIdx(1);
                } else if (dr.getLabel().equals("bad")) {
                    instance.setLabelIdx(-1);
                }
            }
            ifList.add(instance);
            featureMan.addInstance(instance);

        }
        return ifList;
    }
}
