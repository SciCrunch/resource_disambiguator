package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.neuinfo.resource.disambiguator.model.PaperReference;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

public class DOI2PMIDMappingChecker {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;

    public DOI2PMIDMappingChecker() {
    }

    public void checkPaper(String pmid) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<PaperReference> prList = DisambiguatorFinder
                    .getMatchingPaperReferences(em, 1, pmid);
            if (!prList.isEmpty()) {
                PaperReference pr = prList.get(0);
                System.out.println(pr);
                String pmid2 = DOI2PMIDServiceClient.getPMID(
                        pr.getPublicationName(), pr.getTitle());
                System.out.println("old pmid:" + pmid + " new PMID:" + pmid2);
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void handle() throws Exception {
        EntityManager em = null;
        List<PaperReference> prList = null;
        try {
            em = Utils.getEntityManager(emFactory);

            prList = DisambiguatorFinder
                    .getMatchingPaperReferences(em, 1);
        } finally {
            Utils.closeEntityManager(em);
        }
        int badCount = 0;
        List<PaperReference> badPrList = new ArrayList<PaperReference>();
        for (PaperReference pr : prList) {
            if (pr.getTitle() == null || pr.getTitle().trim().length() == 0) {
                badCount++;
                badPrList.add(pr);
                continue;
            }
            System.out.println(">> " + pr.getTitle());
            String pmid = DOI2PMIDServiceClient.getPMID(
                    pr.getPublicationName(), pr.getTitle());
            if (pmid != null) {
                System.out.println(pr.getPublicationName());

                System.out.println("PMID:" + pmid);

                System.out.println("---------------------------------");
                pr.setPubmedId(pmid);
                updatePaperReference(pr);
            } else {
                badCount++;
                badPrList.add(pr);
            }
        }
        System.out.println("# of bad PMID mapping count:" + badCount);
        removeBadPaperReferences(badPrList);
    }

    void updatePaperReference(PaperReference pr) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            em.merge(pr);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void removeBadPaperReferences(List<PaperReference> badPrList) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            for (PaperReference pr : badPrList) {
                em.remove(pr);
            }
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());
        DOI2PMIDMappingChecker checker = new DOI2PMIDMappingChecker();
        try {
            injector.injectMembers(checker);

            checker.handle();

            // checker.checkPaper("21120546");
        } finally {
            JPAInitializer.stopService();
        }

    }

}
