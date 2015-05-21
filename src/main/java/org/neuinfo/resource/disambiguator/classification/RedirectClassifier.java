package org.neuinfo.resource.disambiguator.classification;

import bnlpkit.learning.svm.SVMLightWrapper;
import bnlpkit.nlp.common.classification.*;
import bnlpkit.nlp.common.classification.feature.ExtFeatureManager;
import bnlpkit.nlp.common.classification.feature.InstanceFeatures;
import bnlpkit.nlp.common.classification.svm.SVMClassifierFactory;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.neuinfo.resource.disambiguator.util.Assertion;

import java.util.*;

/**
 * Created by bozyurt on 3/29/14.
 */
public class RedirectClassifier {
    RedirectClassifierConfig config;
    ClassifierParams params;
    List<ClassificationStats> csList = new ArrayList<ClassificationStats>();
    static Logger log = Logger.getLogger(RedirectClassifier.class);

    public RedirectClassifier(RedirectClassifierConfig config) {
        this.config = config;
        params = new ClassifierParams();
    }

    public void prepTrainingFeatures(List<DataRec> drList) throws Exception {
        ExtFeatureManager fm = new ExtFeatureManager(config.getFeatureIndicesFile(), null);
        RedirectClassifierFeatureGenerator fg = new RedirectClassifierFeatureGenerator(fm);
        final List<InstanceFeatures> ifList = fg.extractBigramFeatures(drList);
        //final List<InstanceFeatures> ifList = fg.extractFeatures(drList);
        fm.prepareFeatureTypeMap();
        fm.saveFeatureIndicesFile();
        fm.saveSVMFile(config.getSvmTrainFile(), ifList);
    }

    public void prepTestingFeatures(List<DataRec> drList) throws Exception {
        ExtFeatureManager fm = new ExtFeatureManager(config.getFeatureIndicesFile(), null);
        fm.loadFeatureTypeMap();
        RedirectClassifierFeatureGenerator fg = new RedirectClassifierFeatureGenerator(fm);
        final List<InstanceFeatures> ifList = fg.extractBigramFeatures(drList);
        //final List<InstanceFeatures> ifList = fg.extractFeatures(drList);
        fm.prepareFeatureTypeMap();

        fm.saveSVMFile(config.getSvmTestFile(), ifList);
    }

    public void prepFeaturesForTransduction(List<DataRec> trainDrList, List<DataRec> testDrList) throws Exception {
        List<DataRec> drList = new ArrayList<DataRec>();
        drList.addAll(trainDrList);
        for (DataRec dr : testDrList) {
            DataRec unlabeledDR = new DataRec(dr);
            unlabeledDR.setLabel(null);
            drList.add(unlabeledDR);
        }

        ExtFeatureManager fm = new ExtFeatureManager(config.getFeatureIndicesFile(), null);

        RedirectClassifierFeatureGenerator fg = new RedirectClassifierFeatureGenerator(fm);
        final List<InstanceFeatures> ifList = fg.extractBigramFeatures(drList);
        fm.prepareFeatureTypeMap();
        fm.saveFeatureIndicesFile();
        fm.saveSVMFile(config.getSvmTrainFile(), ifList);
    }

    public void trainTransductive(List<DataRec> testDrList) throws Exception {
        String outDir = config.getSvmModelDir();
        System.out.println("outDir:" + outDir);
        String trFile = config.getSvmTrainFile();
        params.putReal("C", 1.0);
        params.putStr("training.file", trFile);
        params.putStr("kernel.type", "linear");
        String svmModelDir = config.getSvmModelDir();
        String mdlFile = svmModelDir + "/transductive.mdl";
        params.putStr("model.file", mdlFile);
        SVMLightWrapper svm = new SVMLightWrapper(params);
        svm.setUseTransduction(true);

        svm.learn();


        String svmTestFile = config.getSvmTestFile();
        params.putStr("model.file", mdlFile);
        params.putStr("testing.file", svmTestFile);
        params.putStr("training.file", config.getSvmTrainFile());
        log.info("svmModelDir:" + svmModelDir);
        log.info("svmTestFile:" + svmTestFile);

        IClassifier classifier = new SVMClassifierFactory().createClassifier(
                config.getClassifierType(), params);

        log.info("running Redirect Filter Classifier " + mdlFile + "\n on "
                + svmTestFile);
        Map<Integer, Float> id2PredMap = classifier.classify();

        ClassificationStats cs = new ClassificationStats();
        List<RedirectPrediction> predList = new ArrayList<RedirectPrediction>();
        for (Map.Entry<Integer, Float> entry : id2PredMap.entrySet()) {
            int id = entry.getKey();
            final RedirectPrediction pred = new RedirectPrediction(id, entry.getValue());
            pred.dr = testDrList.get(id);
            predList.add(pred);
            if (pred.score > 0) {
                if (pred.dr.getLabel().equals("good")) {
                    cs.incrCorrectCount();
                } else {
                    cs.incrFpCount();
                    cs.incrIncorrectCount();
                }
            } else {
                if (pred.dr.getLabel().equals("good")) {
                    cs.incrFnCount();
                    cs.incrIncorrectCount();
                }
            }
        }
        System.out.printf("Precision:%.2f Recall:%.2f F1:%.2f%n", cs.getP(), cs.getR(), cs.getF1());
        csList.add(cs);
    }


    public void train() throws Exception {
        String outDir = config.getSvmModelDir();
        System.out.println("outDir:" + outDir);
        String trFile = config.getSvmTrainFile();
        params.putReal("C", 1.0);
        // params.putReal("costFactor", 10.0);
        params.putStr("training.file", trFile);
        params.putStr("kernel.type", "linear");
        //params.putStr("kernel.type", "poly");
        //params.putInt("degree", 2);
        String svmModelDir = config.getSvmModelDir();
        String mdlFile = svmModelDir + "/redirect.mdl";
        params.putStr("model.file", mdlFile);
        trainSVM();
    }

    private void trainSVM() throws Exception {
        ITrainer trainer = new SVMClassifierFactory().createTrainer(config.getClassifierType(),
                params);
        trainer.train();
    }

    public List<RedirectPrediction> runClassifier(List<DataRec> testDrList) throws Exception {
        String svmModelDir = config.getSvmModelDir();
        String mdlFile = svmModelDir + "/redirect.mdl";
        String svmTestFile = config.getSvmTestFile();
        params.putStr("model.file", mdlFile);
        params.putStr("testing.file", svmTestFile);
        params.putStr("training.file", config.getSvmTrainFile());
        log.info("svmModelDir:" + svmModelDir);
        log.info("svmTestFile:" + svmTestFile);

        IClassifier classifier = new SVMClassifierFactory().createClassifier(
                config.getClassifierType(), params);

        log.info("running Redirect Filter Classifier " + mdlFile + "\n on "
                + svmTestFile);
        Map<Integer, Float> id2PredMap = classifier.classify();

        List<RedirectPrediction> predList = new ArrayList<RedirectPrediction>();
        ClassificationStats cs = new ClassificationStats();
        for (Map.Entry<Integer, Float> entry : id2PredMap.entrySet()) {
            int id = entry.getKey();
            final RedirectPrediction pred = new RedirectPrediction(id, entry.getValue());
            pred.dr = testDrList.get(id);
            predList.add(pred);
            if (pred.dr.getLabel() != null) {
                if (pred.score > 0) {
                    if (pred.dr.getLabel().equals("good")) {
                        cs.incrCorrectCount();
                    } else {
                        cs.incrFpCount();
                        cs.incrIncorrectCount();
                    }
                } else {
                    if (pred.dr.getLabel().equals("good")) {
                        cs.incrFnCount();
                        cs.incrIncorrectCount();
                    }
                }
            }
        }

        System.out.printf("Precision:%.2f Recall:%.2f F1:%.2f%n", cs.getP(), cs.getR(), cs.getF1());
        csList.add(cs);
        return predList;
    }

    public void trainTestSVMClassifier(String datasetXmlFile, long seed) throws Exception {
        DatasetWrapper dw = prepDataSetForClassification(datasetXmlFile, seed);

        prepTrainingFeatures(dw.trList);
        train();

        prepTestingFeatures(dw.tstList);

        runClassifier(dw.tstList);
    }

    public void trainTestTransductiveSVM(String datasetXmlFile, long seed) throws Exception {
        DatasetWrapper dw = prepDataSetForClassification(datasetXmlFile, seed);
        prepFeaturesForTransduction(dw.trList, dw.tstList);
        prepTestingFeatures(dw.tstList);
        trainTransductive(dw.tstList);
    }


    DatasetWrapper prepDataSetForClassification(String datasetXmlFile, long seed) throws Exception {
        SAXBuilder saxBuilder = new SAXBuilder();
        Document doc = saxBuilder.build(datasetXmlFile);
        Element rootNode = doc.getRootElement();
        List<Element> children = rootNode.getChildren("data");
        List<DataRec> drList = new ArrayList<DataRec>(children.size());
        for (Element el : children) {
            drList.add(DataRec.fromXml(el));
        }
        for (Iterator<DataRec> it = drList.iterator(); it.hasNext(); ) {
            DataRec dr = it.next();
            if (dr.getLabel() == null || dr.getLabel().length() == 0) {
                it.remove();
            }
        }

        Map<DataInstance, DataRec> adapterMap = new HashMap<DataInstance, DataRec>();
        List<DataInstance> diList = new ArrayList<DataInstance>(drList.size());
        int instanceId = 0;
        for (DataRec dr : drList) {
            DataInstance di = new DataInstance(dr.getLabel().equals("good") ? 1 : -1, dr.getLabel(), 0,
                    String.valueOf(instanceId));
            instanceId++;
            diList.add(di);
            adapterMap.put(di, dr);
        }
        DataSet ds = new DataSet(diList);

        seed = seed < 0 ? 84643621L : seed;
        CrossValidationFilter cv = new CrossValidationFilter(ds, 0.3, seed);
        diList = null;
        DataSet[] dataSets = cv.filter();
        List<DataRec> trList = new ArrayList<DataRec>(dataSets[0].size());
        List<DataRec> tstList = new ArrayList<DataRec>(dataSets[1].size());
        for (DataInstance di : dataSets[0].getInstanceList()) {
            DataRec dr = adapterMap.get(di);
            Assertion.assertNotNull(dr);
            trList.add(dr);
        }
        for (DataInstance di : dataSets[1].getInstanceList()) {
            DataRec dr = adapterMap.get(di);
            Assertion.assertNotNull(dr);
            tstList.add(dr);
        }
        adapterMap = null;
        dataSets = null;

        return new DatasetWrapper(trList, tstList);
    }

    public void showStats() {
        double Pavg = 0, Ravg = 0, F1avg = 0;
        for (ClassificationStats cs : csList) {
            System.out.printf("Precision:%.2f Recall:%.2f F1:%.2f%n", cs.getP(), cs.getR(), cs.getF1());
            Pavg += cs.getP();
            Ravg += cs.getR();
            F1avg += cs.getF1();
        }
        int N = csList.size();
        Pavg /= N;
        Ravg /= N;
        F1avg /= N;
        System.out.printf("Average Precision:%.2f Recall:%.2f F1:%.2f%n", Pavg, Ravg, F1avg);

    }

    public static class DatasetWrapper {
        final List<DataRec> trList;
        final List<DataRec> tstList;

        public DatasetWrapper(List<DataRec> trList, List<DataRec> tstList) {
            this.trList = trList;
            this.tstList = tstList;
        }

        public List<DataRec> getTrList() {
            return trList;
        }

        public List<DataRec> getTstList() {
            return tstList;
        }
    }


    public static class RedirectPrediction implements Comparable<RedirectPrediction> {
        int instanceId;
        float score;
        DataRec dr;

        public RedirectPrediction(int instanceId, float score) {
            this.instanceId = instanceId;
            this.score = score;
        }

        public int getInstanceId() {
            return instanceId;
        }

        public float getScore() {
            return score;
        }

        public DataRec getDr() {
            return dr;
        }

        @Override
        public int compareTo(RedirectPrediction o) {
            return Float.compare(score, o.score);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("RedirectPrediction{");
            sb.append("instanceId=").append(instanceId);
            sb.append(", score=").append(score);
            sb.append(", dr=").append(dr);
            sb.append('}');
            return sb.toString();
        }
    }

    public static void main(String[] args) throws Exception {
        String homeDir = System.getProperty("user.home");
        String datasetXmlFile = homeDir + "/redirect_tr_annotated.xml";

        RedirectClassifierConfig config = new RedirectClassifierConfig("/tmp/redirect");
        RedirectClassifier rc = new RedirectClassifier(config);
        long[] seeds = new long[]{175440811, 82854371, 147558854, 149760888, 132377134,
                124735586, 168553522, 159116695, 118780595, 109241064};

        for (long seed : seeds) {
            rc.trainTestSVMClassifier(datasetXmlFile, seed);
            // rc.trainTestTransductiveSVM(datasetXmlFile, seed);
        }

        rc.showStats();


    }
}
