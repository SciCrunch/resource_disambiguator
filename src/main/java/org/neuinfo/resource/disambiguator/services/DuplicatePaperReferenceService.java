package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.PaperReference;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by bozyurt on 2/13/14.
 */
public class DuplicatePaperReferenceService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;

    public void handle() throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            Query q = session.createQuery("from PaperReference p where p.pubmedId is not null order by p.registry.id , p.pubmedId");
            q.setReadOnly(false).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);

            int count = 0;

            String prevPubMedId = null;
            Long prevRegistryId = null;
            List<PaperReference> toBeDeleted = new LinkedList<PaperReference>();
            List<PaperReference> toBeDeletedBatchList = new ArrayList<PaperReference>();
            while (results.next()) {
                PaperReference pr = (PaperReference) results.get(0);
                long registryId = pr.getRegistry().getId();
                if (prevPubMedId != null) {
                    if (prevRegistryId == registryId && pr.getPubmedId().equals(prevPubMedId)) {
                        toBeDeleted.add(pr);
                        toBeDeletedBatchList.add(pr);
                    }
                }

                prevPubMedId = pr.getPubmedId();
                prevRegistryId = pr.getRegistry().getId();
                count++;
                if ((count % 1000) == 0) {
                    System.out.println("# of duplicates so far:" + toBeDeleted.size());
                    System.out.println("# of records checked so far:" + count);
                    deleteDuplicates(toBeDeletedBatchList);
                    toBeDeletedBatchList.clear();
                }
            }

            System.out.println("# of duplicates:" + toBeDeleted.size());
            System.out.println("# of records checked:" + count);

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    void deleteDuplicates(List<PaperReference> toBeDeletedBatchList) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            for(PaperReference pr : toBeDeletedBatchList) {
                PaperReference npr = em.find(PaperReference.class, pr.getId());
                em.remove(npr);
            }

            Utils.commitTransaction(em);
        } catch(Exception x) {
            x.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            DuplicatePaperReferenceService service = new DuplicatePaperReferenceService();
            injector.injectMembers(service);

            service.handle();

        } finally {
            JPAInitializer.stopService();
        }
    }
}
