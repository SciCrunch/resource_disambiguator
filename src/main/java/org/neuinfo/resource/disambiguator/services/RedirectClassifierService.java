package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.neuinfo.resource.disambiguator.classification.DataRec;
import org.neuinfo.resource.disambiguator.classification.RedirectClassifier;
import org.neuinfo.resource.disambiguator.classification.RedirectClassifierConfig;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.RegistryRedirectAnnotInfo;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Assertion;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.*;

/**
 * Created by bozyurt on 4/9/14.
 */
public class RedirectClassifierService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory = null;
    RedirectClassifierConfig config;
    Map<String, Registry> registryMap = new HashMap<String, Registry>();
    static Logger log = Logger.getLogger(RedirectClassifierService.class);

    public RedirectClassifierService(RedirectClassifierConfig config) {
        this.config = config;
    }


    public void train(String datasetXmlFile) throws Exception {
        List<DataRec> drList = prepDataSetForTraining(datasetXmlFile);
        RedirectClassifier classifier = new RedirectClassifier(config);

        classifier.prepTrainingFeatures(drList);

        classifier.train();
    }

    public void classify() throws Exception {
        prepRegistryMap();
        final List<DataRec> testList = prepDataSetForClassification();
        RedirectClassifier classifier = new RedirectClassifier(config);

        classifier.prepTestingFeatures(testList);

        final List<RedirectClassifier.RedirectPrediction> predictions = classifier.runClassifier(testList);

        for (RedirectClassifier.RedirectPrediction prediction : predictions) {
            float score = prediction.getScore();
            if (score >= 0.8) {
                DataRec dr = prediction.getDr();
                Registry registry = registryMap.get(dr.getRegistryNifID());
                Assertion.assertNotNull(registry);
                RegistryRedirectAnnotInfo rrai = new RegistryRedirectAnnotInfo();
                rrai.setClassiferScore(new Double(score));
                rrai.setLabel("good");
                rrai.setRegistry(registry);
                rrai.setModifiedBy("admin");
                saveUpdateRegistryRedirectAnnotInfo(rrai);
            }
        }
    }

    void saveUpdateRegistryRedirectAnnotInfo(RegistryRedirectAnnotInfo rrai) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            TypedQuery<RegistryRedirectAnnotInfo> query = em.createQuery(
                    "from RegistryRedirectAnnotInfo r where r.registry.id = :id", RegistryRedirectAnnotInfo.class);
            List<RegistryRedirectAnnotInfo> resultList = query.setParameter("id", rrai.getRegistry().getId())
                    .getResultList();
            if (resultList.isEmpty()) {
                em.persist(rrai);
            } else {
                Assertion.assertTrue(resultList.size() == 1);
                RegistryRedirectAnnotInfo existingRRAI = resultList.get(0);
                existingRRAI.setLabel(rrai.getLabel());
                existingRRAI.setModifiedBy(rrai.getModifiedBy());
                existingRRAI.setNotes(rrai.getNotes());
                existingRRAI.setRedirectUrl(rrai.getRedirectUrl());
                existingRRAI.setClassiferScore(rrai.getClassiferScore());
                existingRRAI.setStatus(rrai.getStatus());
                existingRRAI.setModificationTime(rrai.getModificationTime());
                em.merge(existingRRAI);
            }
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            log.error("saveRegistryRedirectAnnotInfo", x);
        } finally {
            Utils.closeEntityManager(em);
        }

    }


    public void prepRegistryMap() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);

            List<Registry> registryRecords = DisambiguatorFinder
                    .getAllActiveRegistryRecords(em);
            for (Registry reg : registryRecords) {
                String nifid = reg.getNifId();
                if (nifid != null) {
                    this.registryMap.put(nifid, reg);
                }
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    List<DataRec> prepDataSetForTraining(String datasetXmlFile) throws Exception {
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
        return drList;
    }

    List<DataRec> prepDataSetForClassification() throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<RegistrySiteContent> query = em.createQuery(
                    "from RegistrySiteContent r where r.flags = 1 and r.content is not null", RegistrySiteContent.class);

            List<RegistrySiteContent> resultList = query.getResultList();
            List<DataRec> drList = new ArrayList<DataRec>(resultList.size());
            for (RegistrySiteContent rsc : resultList) {
                String registryNifId = rsc.getRegistry().getNifId();
                String url = rsc.getRegistry().getUrl();

                DataRec dr = new DataRec(registryNifId, rsc.getContent(), url, null, "rsc");

                drList.add(dr);
            }
            return drList;
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            RedirectClassifierConfig config = new RedirectClassifierConfig("/var/tmp/redirect");
            String homeDir = System.getProperty("user.home");
            String datasetXmlFile = homeDir + "/redirect_tr_annotated.xml";
            RedirectClassifierService service = new RedirectClassifierService(config);
            injector.injectMembers(service);

           // service.train(datasetXmlFile);

            service.classify();

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
