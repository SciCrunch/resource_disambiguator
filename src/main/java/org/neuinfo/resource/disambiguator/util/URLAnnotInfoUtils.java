package org.neuinfo.resource.disambiguator.util;

import bnlpkit.util.GenUtils;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.URLAnnotationInfo;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Created by bozyurt on 1/15/14.
 */
public class URLAnnotInfoUtils {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(URLAnnotInfoUtils.class);


    public void updateURLReferencesFromUserRegAnnotations() throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);

            List<URLAnnotationInfo> infoList = DisambiguatorFinder.getUrlAnnotationsWithRegistry(em);
            for (URLAnnotationInfo uai : infoList) {
                updateURLRec(uai);
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void handle() throws Exception {
        EntityManager em = null;
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        List<URLRec> urlRecList = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<URLRec> query = em.createQuery("from URLRec where flags = ?", URLRec.class)
                    .setParameter(1, URLRec.FROM_DEDUP_REG);

            urlRecList = query.getResultList();
        } finally {
            Utils.closeEntityManager(em);
        }
        for (URLRec ur : urlRecList) {
            List<URLRec> list = getUrlRecs(ur);
            if (!list.isEmpty()) {
                System.out.println("URL:" + ur.getUrl() + " PMID:" + ur.getPaper().getPubmedId());
                System.out.println("----------------------------------");
                System.out.println("Description:");
                System.out.println(GenUtils.formatText(ur.getDescription(), 100));
                System.out.println("----------------------------------");

                for (URLRec ur1 : list) {
                    if (ur1.getRegistry() == null) {
                        System.out.println("\t " + ur1.getUrl());
                    }
                }
                System.out.println();
                System.out.print("Do these have the same resource as '" + ur.getUrl() + "' (y/n):");
                String ans = console.readLine().trim();
                if (ans.equalsIgnoreCase("y")) {
                    updateRegistriesFor(list, ur.getRegistry());
                }
            }
        }
    }

    public List<URLRec> getUrlRecs(URLRec ur) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<URLRec> q = em.createQuery("from URLRec where description is not null " +
                    "and description = :desc and id <> :id", URLRec.class)
                    .setParameter("desc", ur.getDescription()).setParameter("id", ur.getId());
            return q.getResultList();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    private void updateRegistriesFor(List<URLRec> list, Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);

            for (URLRec ur1 : list) {
                if (ur1.getRegistry() == null) {
                    ur1.setRegistry(reg);
                    ur1.setFlags(ur1.getFlags() | URLRec.FROM_DEDUP_REG_SIM);
                    em.merge(ur1);
                }
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            Utils.rollbackTransaction(em);
            t.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }

    }


    private void updateURLRec(URLAnnotationInfo uai) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            TypedQuery<URLRec> query = em.createQuery("from URLRec where id = :id", URLRec.class);
            List<URLRec> urlRecs = query.setParameter("id", uai.getUrl().getId()).getResultList();
            if (!urlRecs.isEmpty()) {
                URLRec ur = urlRecs.get(0);
                if (ur.getRegistry() == null) {
                    log.info("setting registry to " + uai.getRegistry().getResourceName()
                            + " for " + ur.getUrl());
                    ur.setRegistry(uai.getRegistry());
                    ur.setFlags(ur.getFlags() | URLRec.FROM_DEDUP_REG);
                    em.merge(ur);
                }
            }
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            Utils.rollbackTransaction(em);
            t.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            URLAnnotInfoUtils u = new URLAnnotInfoUtils();
            injector.injectMembers(u);

             u.updateURLReferencesFromUserRegAnnotations();

           // u.handle();

        } finally {
            JPAInitializer.stopService();
        }

    }
}
