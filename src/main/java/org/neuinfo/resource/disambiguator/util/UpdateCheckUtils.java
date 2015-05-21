package org.neuinfo.resource.disambiguator.util;

import bnlpkit.util.GenUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.model.RegistryUpdateStatus;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * Created by bozyurt on 2/7/14.
 */
public class UpdateCheckUtils {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;


    public void handle() throws Exception {
        List<RegistryUpdateStatus> rusList = findRus(0, 0.1);

        int count = 0;
        for (RegistryUpdateStatus rus : rusList) {
            Registry registry = rus.getRegistry();
            String urlStr = registry.getUrl();
            System.out.println("registry: " + urlStr + " " + registry.getResourceName());
            String curContent = getContent(registry);
            check4ContentChanges(curContent, rus);
            if (count > 3) {
                break;
            }
            count++;
        }
    }


    public List<RegistryUpdateStatus> findRus(double lb, double ub) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<RegistryUpdateStatus> query = em.createQuery(
                    "from RegistryUpdateStatus where similarity > :lb and similarity <= :ub",
                    RegistryUpdateStatus.class);
            query.setParameter("lb", lb).setParameter("ub", ub);

            return query.getResultList();
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    public String getContent(Registry registry) throws Exception {
        String urlStr = registry.getUrl();
        urlStr = Utils.normalizeUrl(urlStr);
        URL url = new URL(urlStr);
        DescriptionExtractor de = new DescriptionExtractor(url,
                OpMode.FULL_CONTENT);

        String content = de.getContent();
        return content.trim();
    }

    void check4ContentChanges(String content, RegistryUpdateStatus rus) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            RegistrySiteContent rsc = DisambiguatorFinder.getRegistrySiteContent(em, rus.getRegistry(),
                    RegistrySiteContent.ORIGINAL);
            assert rsc != null;
            String origContent;

            origContent = rsc.getContent().trim();

            Set<String> origSet = DocSimilarityUtils.prepShingles(origContent, 5);
            Set<String> curSet = DocSimilarityUtils.prepShingles(content, 5);

            double jc = DocSimilarityUtils.calcJaccardIndex(origSet, curSet);
            double containment1 = DocSimilarityUtils.calcContainment(curSet, origSet);
            double containment2 = DocSimilarityUtils.calcContainment(origSet, curSet);
            double containment = Math.max(containment1, containment2);

            System.out.println("jc:" + jc);
            System.out.println("Original\n===================");
            System.out.println(GenUtils.formatText(origContent, 100));

            System.out.println("Current Content\n===================");
            System.out.println(GenUtils.formatText(content, 100));
            System.out.println("------------------------------------------------------------");
        } finally {
            Utils.closeEntityManager(em);
        }

    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            UpdateCheckUtils ucu = new UpdateCheckUtils();
            injector.injectMembers(ucu);

            ucu.handle();

        } finally {
            JPAInitializer.stopService();
        }
    }
}



