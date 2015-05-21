package org.neuinfo.resource.disambiguator.services;

import bnlpkit.nlp.common.FrequencyTable;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.hibernate.Query;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.ResourceRec;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.*;
import java.util.*;

/**
 * <p/>
 * After NER for resources on PMC Open Access papers,
 * this service determines unique resource mentions in papers
 * checking for duplicates in the URL based resource detection
 * service generated records and matching against registry
 * records, associates mentions with corresponding registry item.
 * <p/>
 * Created by bozyurt on 1/24/14.
 */
public class ResourceNER2RegistryAssocService {
    Map<String, Registry> registryMap = new HashMap<String, Registry>();
    Map<String, Registry> origRegistryMap = new HashMap<String, Registry>();
    static Set<String> knownBadSet = new HashSet<String>();
    static Logger log = Logger.getLogger(ResourceNER2RegistryAssocService.class);

    @Inject
    @IndicatesPrimaryJpa
    Provider<EntityManager> emFactory = null;

    static {
        knownBadSet.add("program");
        knownBadSet.add("programs");
        knownBadSet.add("code");
        knownBadSet.add("libraries");
        knownBadSet.add("interface");
        knownBadSet.add("using");
        knownBadSet.add("Biomedical");
    }

    public ResourceNER2RegistryAssocService() {
    }

    public void markDuplicates() throws Exception {
        prepRegistryMap();
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            ScrollableResults rrList = getAllResourceRefsWithRegistry(session);
            int count = 0;
            int dupCount = 0;
            while (rrList.next()) {
                ResourceRec rr = (ResourceRec) rrList.get(0);
                if (rr.getRegistry() != null) {
                    if (isAlreadyDetectedByUrl(rr, rr.getRegistry())) {
                        deassociate(rr);
                        dupCount++;
                    }
                }
                count++;
                if ((count % 100) == 0) {
                    System.out.println("Handled so far:" + count);
                }
            }
            System.out.printf("dupCount:%d tot count:%d%n", dupCount, count);
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public void handle() throws Exception {
        prepRegistryMap();
        EntityManager em = null;
        StatelessSession session = null;
        FrequencyTable<String> ft = new FrequencyTable<String>();
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            ScrollableResults rrList = getAllResourceRefCandidates(session);
            int matchCount = 0;
            int count = 0;
            while (rrList.next()) {
                ResourceRec rr = (ResourceRec) rrList.get(0);
                String entity = rr.getEntity();
                if (entity.length() <= 3 || justNumbersAndDots(entity) || knownBadSet.contains(entity)) {
                    continue;
                }
                Registry theReg = null;
                if ((theReg = matches(rr.getEntity().trim())) != null) {
                    // System.out.println(rr.getEntity() + " matches " + reg.getResourceName());
                    matchCount++;
                    if (rr.getRegistry() == null) {
                        if (!isAlreadyDetectedByUrl(rr, theReg)) {
                            associate(rr, theReg);
                        } else {
                            deassociate(rr);
                        }
                    }
                } else {
                    // System.out.println("No match for:" + rr.getEntity());
                    ft.addValue(rr.getEntity());
                }
                count++;
                if ((count % 1000) == 0) {
                    System.out.println("Handled so far:" + count);
                }
            }

            System.out.printf("matchCount:%d tot count:%d%n", matchCount, count);

            ft.dumpSortedByFreq();

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    void deassociate(ResourceRec rr) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            rr.setFlags(ResourceRec.DUPLICATE);
            rr.setRegistry(null);
            em.merge(rr);

            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void associate(ResourceRec rr, Registry reg) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            rr.setRegistry(reg);
            em.merge(rr);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    boolean isAlreadyDetectedByUrl(ResourceRec rr, Registry reg) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            javax.persistence.Query query = em.createQuery(
                    "select u.id from URLRec u where u.paper.id = :paperId and u.registry.id = :regId");
            query.setParameter("paperId", rr.getPaper().getId()).setParameter("regId", reg.getId());

            List<?> results = query.getResultList();
            return !results.isEmpty();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected Registry matches(String entity) {
        if (registryMap.containsKey(entity)) {
            return registryMap.get(entity);
        }
        if (registryMap.containsKey(entity.toLowerCase())) {
            return registryMap.get(entity.toLowerCase());
        }
        int count = 0;
        Registry theReg = null;
        String[] entityToks = entity.split("\\s+");
        for (Registry reg : origRegistryMap.values()) {
            if (reg.getResourceName().indexOf(entity) != -1) {
                if (entityToks.length == 1 && termMatches(entity, reg.getResourceName())) {
                    theReg = reg;
                    count++;
                } else if (entityToks.length > 1) {
                    theReg = reg;
                    count++;
                }
            }
        }
        if (count == 1) {
            System.out.println(entity + " matches " + theReg.getResourceName());
            registryMap.put(entity, theReg);
            return theReg;
        }

        return null;
    }


    public static boolean termMatches(String term, String resourceName) {
        int idx = resourceName.indexOf(term);
        int endIdx = idx + term.length() - 1;
        boolean startAtBoundary = idx == 0 || Character.isWhitespace(resourceName.charAt(idx - 1));
        if (!startAtBoundary) {
            return false;
        }
        char ch = (char) -1;
        if ((endIdx + 1) < resourceName.length()) {
            ch = resourceName.charAt(endIdx + 1);
        }
        boolean endAtBoundary = (endIdx + 1) == resourceName.length() ||
                Character.isWhitespace(ch) || Character.isDigit(ch);
        return startAtBoundary && endAtBoundary;
    }

    public static boolean justNumbersAndDots(String term) {
        int len = term.length();
        for (int i = 0; i < len; i++) {
            char ch = term.charAt(i);
            if (!Character.isDigit(ch) && ch != '.') {
                return false;
            }
        }
        return true;
    }

    ScrollableResults getAllResourceRefsWithRegistry(StatelessSession session) {
        Query query = session.createQuery("from ResourceRec r where r.registry.id is not null and r.flags <> 1");
        query.setReadOnly(true).setFetchSize(1000);
        return query.scroll(ScrollMode.FORWARD_ONLY);
    }

    ScrollableResults getAllResourceRefCandidates(StatelessSession session) {
        Query query = session.createQuery("from ResourceRec r where r.registry.id is null and r.flags <> 1");
        query.setReadOnly(true).setFetchSize(1000);
        return query.scroll(ScrollMode.FORWARD_ONLY);
    }

    public void prepRegistryMap() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> registryRecords = DisambiguatorFinder.getAllRegistryRecords(em);
            for (Registry reg : registryRecords) {
                String name = reg.getResourceName();
                registryMap.put(name, reg);
                origRegistryMap.put(name, reg);
                if (name.equals("ImageJ")) {
                    registryMap.put("Image J", reg);
                    origRegistryMap.put("Image J", reg);
                    registryMap.put("Image-J", reg);
                    origRegistryMap.put("Image-J", reg);
                }

                if (reg.getAbbrev() != null && reg.getAbbrev().length() > 0) {
                    registryMap.put(reg.getAbbrev(), reg);
                }
                String lcName = name.toLowerCase();
                if (!lcName.equals(name)) {
                    registryMap.put(lcName, reg);
                }
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());

        try {
            ResourceNER2RegistryAssocService service = new ResourceNER2RegistryAssocService();
            injector.injectMembers(service);

            service.handle();
            //service.markDuplicates();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
