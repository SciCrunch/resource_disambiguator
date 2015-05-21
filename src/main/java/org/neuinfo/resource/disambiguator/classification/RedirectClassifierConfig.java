package org.neuinfo.resource.disambiguator.classification;

import bnlpkit.nlp.common.classification.svm.SVMClassifierFactory;
import bnlpkit.nlp.common.services.config.AbstractConfig;

import java.io.File;

/**
 * Created by bozyurt on 3/29/14.
 */
public class RedirectClassifierConfig extends AbstractConfig {
    private static final long serialVersionUID = 1L;
    protected String svmModelDir;
    protected String svmTestFile;
    protected String svmTrainFile;
    protected String classifierType = SVMClassifierFactory.SVMLIGHT;
    protected String featureIndicesFile;
    protected String trainingXmlFile;
    protected String testingXmlFile;

    public final static String DEFAULT_svmModelDir = "svm";
    public final static String DEFAULT_svmTestFile = "svm/redirect_classify.dat";
    public final static String DEFAULT_svmTrainFile = "svm/redirect_train.dat";
    public final static String DEFAULT_featureIndicesFile = "redirect_feature_indices.xml";

    public RedirectClassifierConfig(String workDir) throws Exception {
        super(workDir);
        init();
        File f = new File(svmModelDir);
        if (!f.exists()) {
            f.mkdirs();
        }
    }

    public String getSvmModelDir() {
        return svmModelDir;
    }

    public void setSvmModelDir(String svmModelDir) {
        this.svmModelDir = svmModelDir;
    }

    public String getSvmTestFile() {
        return svmTestFile;
    }

    public void setSvmTestFile(String svmTestFile) {
        this.svmTestFile = svmTestFile;
    }

    public String getSvmTrainFile() {
        return svmTrainFile;
    }

    public void setSvmTrainFile(String svmTrainFile) {
        this.svmTrainFile = svmTrainFile;
    }

    public String getClassifierType() {
        return classifierType;
    }

    public void setClassifierType(String classifierType) {
        this.classifierType = classifierType;
    }

    public String getFeatureIndicesFile() {
        return featureIndicesFile;
    }

    public void setFeatureIndicesFile(String featureIndicesFile) {
        this.featureIndicesFile = featureIndicesFile;
    }

    public String getTrainingXmlFile() {
        return trainingXmlFile;
    }

    public void setTrainingXmlFile(String trainingXmlFile) {
        this.trainingXmlFile = trainingXmlFile;
    }

    public String getTestingXmlFile() {
        return testingXmlFile;
    }

    public void setTestingXmlFile(String testingXmlFile) {
        this.testingXmlFile = testingXmlFile;
    }
}