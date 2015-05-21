package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.services.config.AbstractConfig;

/**
 * Created by bozyurt on 3/11/14.
 */
public class LDAConfig extends AbstractConfig {
    protected String settingsFile;
    protected String inferenceSettingsFile;
    protected String outDir;
    protected String inferenceName = "test_rd";
    protected String vocabularyFile;
    protected String trainDataFile;


    public LDAConfig(String workDir) {
        super(workDir);
    }

    public String getSettingsFile() {
        return settingsFile;
    }

    public void setSettingsFile(String settingsFile) {
        this.settingsFile = settingsFile;
    }

    public String getInferenceSettingsFile() {
        return inferenceSettingsFile;
    }

    public void setInferenceSettingsFile(String inferenceSettingsFile) {
        this.inferenceSettingsFile = inferenceSettingsFile;
    }

    public String getOutDir() {
        return outDir;
    }

    public void setOutDir(String outDir) {
        this.outDir = outDir;
    }

    public String getInferenceName() {
        return inferenceName;
    }

    public void setInferenceName(String inferenceName) {
        this.inferenceName = inferenceName;
    }

    public String getVocabularyFile() {
        return vocabularyFile;
    }

    public void setVocabularyFile(String vocabularyFile) {
        this.vocabularyFile = vocabularyFile;
    }

    public String getTrainDataFile() {
        return trainDataFile;
    }

    public void setTrainDataFile(String trainDataFile) {
        this.trainDataFile = trainDataFile;
    }
}
