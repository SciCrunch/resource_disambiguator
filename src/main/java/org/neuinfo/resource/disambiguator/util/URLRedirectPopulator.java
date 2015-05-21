package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.hibernate.criterion.Restrictions;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.model.URLRedirectRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 *
 * Created by bozyurt on 12/20/13.
 */
public class URLRedirectPopulator {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(URLRedirectPopulator.class);

    public URLRedirectPopulator() {}

    public void handle() {
        StatelessSession session = null;
        EntityManager em = null;
        try {
            long start = System.currentTimeMillis();
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Criteria criteria = session.createCriteria(URLRec.class).add(Restrictions.gt("score", (double) 0));
            criteria.setReadOnly(true).setFetchSize(1000).setCacheable(false);
            ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);


            while(results.next()) {
                URLRec ur = (URLRec) results.get(0);
                handleRedirectionIfAny(ur);
            }

            long diff = System.currentTimeMillis() - start;
            log.info("Elapsed time (secs): " + (diff / 1000.0));
            log.info("Finished redirect url detection");
            log.info("---------------------------------------------------");

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    private void handleRedirectionIfAny(URLRec ur) {
        URLValidator validator = new URLValidator(ur.getUrl(), OpMode.FULL_CONTENT);
        try {
            URLContent urlContent = validator.checkValidity(true);
            if (urlContent != null) {
                log.info("checked url " + ur.getUrl());
            }
            if (urlContent != null && urlContent.getFinalRedirectURI() != null) {
                EntityManager em = null;
                try {
                    em = Utils.getEntityManager(emFactory);
                    Utils.beginTransaction(em);
                    TypedQuery<URLRedirectRec> query = em.createQuery(
                            "from URLRedirectRec u where u.url.id = :id", URLRedirectRec.class);
                    query.setParameter("id", ur.getId());
                    List<URLRedirectRec> resultList = query.getResultList();
                    if (resultList.isEmpty()) {
                        URLRedirectRec urr = new URLRedirectRec();
                        urr.setUrl(ur);
                        urr.setRedirectUrl(urlContent.getFinalRedirectURI().toString());
                        log.info("saving " + urr);
                        em.persist(urr);
                    }

                    Utils.commitTransaction(em);
                } catch(Throwable t) {
                    log.error("handleRedirectionIfAny", t);
                    Utils.rollbackTransaction(em);
                } finally {
                    Utils.closeEntityManager(em);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector( new RDPersistModule());
            URLRedirectPopulator urp = new URLRedirectPopulator();
            injector.injectMembers(urp);

            urp.handle();
        } finally {
            JPAInitializer.stopService();
        }
    }

}
