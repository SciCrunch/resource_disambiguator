package org.neuinfo.resource.disambiguator.nlp;

import bnlpkit.nlp.common.Acronym;
import bnlpkit.nlp.common.FrequencyTable;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.neuinfo.resource.disambiguator.model.AcronymExpansion;
import org.neuinfo.resource.disambiguator.model.PaperAcronyms;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Assertion;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.*;

/**
 * Created by bozyurt on 9/22/14.
 */
public class AcronymStats {
    @Inject
    @IndicatesPrimaryJpa
    Provider<EntityManager> emFactory = null;


    List<Frequency<String>> findClosestVariants(ExpansionVariants evRef, ExpansionVariants evOther, Utils.ICostFunction cf) {
        List<Frequency<String>> closestList = new ArrayList<Frequency<String>>(3);
        if (evOther.isEmptyCluster() && evOther.variants.isEmpty()) {
            return closestList;
        }
        for (Iterator<Frequency<String>> it = evOther.variants.iterator(); it.hasNext(); ) {
            Frequency<String> otherVar = it.next();
            float ed = Utils.levenshteinDistance(evRef.canonicalExpansion.toLowerCase().toCharArray(),
                    otherVar.key.toLowerCase().toCharArray(), cf);
            double fraction = ed / (double) evRef.canonicalExpansion.length();
            if (fraction < 0.1) {
                closestList.add(otherVar);
                it.remove();
            }
        }
        float ed = Utils.levenshteinDistance(evRef.canonicalExpansion.toLowerCase().toCharArray(),
                evOther.canonicalExpansion.toLowerCase().toCharArray(), cf);
        double fraction = ed / (double) evRef.canonicalExpansion.length();
        if (fraction < 0.1) {
            closestList.add(new Frequency<String>(evOther.canonicalExpansion, evOther.canonFreq));
            evOther.emptyCluster = true;
        }

        for (Frequency<String> refVar : evRef.variants) {
            for (Iterator<Frequency<String>> it = evOther.variants.iterator(); it.hasNext(); ) {
                Frequency<String> otherVar = it.next();
                ed = Utils.levenshteinDistance(refVar.key.toLowerCase().toCharArray(),
                        otherVar.key.toLowerCase().toCharArray(), cf);
                fraction = ed / (double) refVar.key.length();
                if (fraction < 0.1) {
                    closestList.add(otherVar);
                    it.remove();
                }
            }
            ed = Utils.levenshteinDistance(refVar.key.toLowerCase().toCharArray(),
                    evOther.canonicalExpansion.toLowerCase().toCharArray(), cf);
            fraction = ed / (double) refVar.key.length();
            if (fraction < 0.1) {
                closestList.add(new Frequency<String>(evOther.canonicalExpansion, evOther.canonFreq));
                evOther.emptyCluster = true;
            }
        }
        return closestList;
    }

    public void calcStats2() {
        List<AcronymStat> acronymStats = getGroupedPaperAcronyms();
        List<ProcessedAcronym> paList = new ArrayList<ProcessedAcronym>(acronymStats.size());
        int i = 1;
        Utils.TransliterationCostFunction cf = new Utils.TransliterationCostFunction();
        for (AcronymStat as : acronymStats) {
            List<Frequency<String>> list = as.getExpansionsSortedByFreq();

            ProcessedAcronym pa = new ProcessedAcronym(as.acronym, as.freq);
            paList.add(pa);
            while (!list.isEmpty()) {
                Frequency<String> cev = list.remove(0);
                ExpansionVariants ev = new ExpansionVariants(cev.key, cev.freq);
                pa.add(ev);
                for (Iterator<Frequency<String>> it = list.iterator(); it.hasNext(); ) {
                    Frequency<String> cev2 = it.next();
                    if (cev.key.equalsIgnoreCase(cev2.key)) {
                        ev.addVariant(cev2);
                        it.remove();
                    } else {
                        float ed = Utils.levenshteinDistance(cev.key.toLowerCase().toCharArray(),
                                cev2.key.toLowerCase().toCharArray(), cf);
                        double fraction = ed / (double) cev.key.length();
                        if (fraction < 0.1) {
                            ev.addVariant(cev2);
                            it.remove();
                        }
                    }
                }
            }
            // merge clusters
            List<ExpansionVariants> emptyList = new ArrayList<ExpansionVariants>(pa.expansions.size());
            for (int j = 0; j < pa.expansions.size(); j++) {
                ExpansionVariants evRef = pa.expansions.get(j);

                for (int k = j + 1; k < pa.expansions.size(); k++) {
                    ExpansionVariants evOther = pa.expansions.get(k);
                    if (evRef == evOther) {
                        continue;
                    }
                    List<Frequency<String>> closestVariants = findClosestVariants(evRef, evOther, cf);
                    if (!closestVariants.isEmpty()) {
                        for (Frequency<String> v : closestVariants) {
                            evRef.addVariant(v);
                        }
                        if (evOther.variants.isEmpty() && evOther.isEmptyCluster()) {
                            emptyList.add(evOther);
                        }
                    }
                }
            }
            for (ExpansionVariants ev : emptyList) {
                pa.expansions.remove(ev);
            }
            if (!emptyList.isEmpty()) {
                System.out.println(pa);
            }

            if ((i % 200) == 0) {
                System.out.println("Acronyms processed so far:" + i);
            }
            i++;
        }

        /*
        Collections.sort(paList, new Comparator<ProcessedAcronym>() {
            @Override
            public int compare(ProcessedAcronym o1, ProcessedAcronym o2) {
                return o1.freq - o2.freq;
            }
        });
        */
        Collections.sort(paList, new Comparator<ProcessedAcronym>() {
            @Override
            public int compare(ProcessedAcronym o1, ProcessedAcronym o2) {
                int cmp = Character.compare(Character.toLowerCase(o1.acronym.charAt(0)),
                        Character.toLowerCase(o2.acronym.charAt(0)));
                if (cmp == 0) {
                    return o2.freq - o1.freq;
                }
                return cmp;
            }
        });
        for (ProcessedAcronym pa : paList) {
            System.out.println(pa);
            saveProcessedAcronym(pa);
        }
        System.out.println("--------------------------");
        System.out.println("Num unique acronyms:" + paList.size());
    }

    void saveProcessedAcronym(ProcessedAcronym pa) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            org.neuinfo.resource.disambiguator.model.Acronym acr = new org.neuinfo.resource.disambiguator.model.Acronym();

            acr.setAcronym(pa.acronym);
            acr.setFrequency(pa.freq);
            em.persist(acr);

            int clusterId = 1;
            for (ExpansionVariants ev : pa.expansions) {
                AcronymExpansion ae = new AcronymExpansion();
                ae.setAcronym(acr);
                ae.setClusterId(clusterId);
                ae.setExpansion(ev.canonicalExpansion);
                ae.setFrequency(ev.canonFreq);
                em.persist(ae);

                for (Frequency<String> v : ev.variants) {
                    ae = new AcronymExpansion();
                    ae.setAcronym(acr);
                    ae.setClusterId(clusterId);
                    ae.setExpansion(v.key);
                    ae.setFrequency(v.freq);
                    em.persist(ae);
                }
                clusterId++;
            }

            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void calcStats() {
        List<PaperAcronyms> paperAcronyms = getPaperAcronyms();
        Set<String> pmidSet = new HashSet<String>();
        Map<String, Acronym> acrMap = new HashMap<String, Acronym>();
        for (PaperAcronyms pa : paperAcronyms) {
            Acronym ac = acrMap.get(pa.getAcronym());
            if (ac == null) {
                ac = new Acronym(pa.getAcronym());
                acrMap.put(pa.getAcronym(), ac);
            }
            ac.addExpansion(new Acronym.AcrExpansion(pa.getExpansion(), "", "", pa.getPubmedId()));
            pmidSet.add(pa.getPubmedId());
        }
        int len = paperAcronyms.size();
        paperAcronyms = null;
        double[] numSenses = new double[acrMap.size()];
        List<AcronymStat> asList = new ArrayList<AcronymStat>(acrMap.size());
        List<ProcessedAcronym> paList = new ArrayList<ProcessedAcronym>(acrMap.size());
        int i = 0;
        Utils.TransliterationCostFunction cf = new Utils.TransliterationCostFunction();
        for (Acronym ac : acrMap.values()) {
            FrequencyTable<String> ft = new FrequencyTable<String>();
            for (Acronym.AcrExpansion ae : ac.getExpansionList()) {
                ft.addValue(ae.getExpansion().toLowerCase());
            }
            List<Comparable<String>> keys = ft.getSortedKeys();
            numSenses[i] = keys.size();
            AcronymStat as = new AcronymStat(ac.getAcronym());
            asList.add(as);
            for (Comparable<String> k : keys) {
                String exp = (String) k;
                as.addExpansion(new Frequency<String>(exp, ft.getFrequency(exp)));
            }
            as.calcFreq();
            List<Frequency<String>> list = as.getExpansionsSortedByFreq();

            ProcessedAcronym pa = new ProcessedAcronym(as.acronym, as.freq);
            paList.add(pa);
            while (!list.isEmpty()) {
                Frequency<String> cev = list.remove(0);
                ExpansionVariants ev = new ExpansionVariants(cev.key, cev.freq);
                pa.add(ev);
                for (Iterator<Frequency<String>> it = list.iterator(); it.hasNext(); ) {
                    Frequency<String> cev2 = it.next();
                    if (cev.key.equalsIgnoreCase(cev2.key)) {
                        ev.addVariant(cev2);
                        it.remove();
                    } else {
                        float ed = Utils.levenshteinDistance(cev.key.toCharArray(), cev2.key.toCharArray(), cf);
                        double fraction = ed / (double) cev.key.length();
                        if (fraction < 0.1) {
                            ev.addVariant(cev2);
                            it.remove();
                        }
                    }
                }
            }
            i++;
        }

        double avgNumOfSenses = Utils.mean(numSenses);
        double sdNumOfSenses = Math.sqrt(Utils.variance(numSenses, avgNumOfSenses));

        Collections.sort(asList, new Comparator<AcronymStat>() {
            @Override
            public int compare(AcronymStat o1, AcronymStat o2) {
                return o1.freq - o2.freq;
            }
        });

        //    for (AcronymStat as : asList) {
        //        System.out.println(as);
        //    }

        Collections.sort(paList, new Comparator<ProcessedAcronym>() {
            @Override
            public int compare(ProcessedAcronym o1, ProcessedAcronym o2) {
                return o1.freq - o2.freq;
            }
        });

        for (ProcessedAcronym pa : paList) {
            System.out.println(pa);
        }
        System.out.println("--------------------------");
        System.out.printf("Avg # of acronyms defined per paper %.1f%n", (len / (float) pmidSet.size()));
        System.out.printf("Avg num of senses for acronyms :%.1f (%.3f)%n", avgNumOfSenses, sdNumOfSenses);
        System.out.println("Num unique acronyms:" + asList.size());

    }

    public static class AcronymStat {
        String acronym;
        int freq;
        List<Frequency<String>> expansions = new LinkedList<Frequency<String>>();

        public AcronymStat(String acronym) {
            this.acronym = acronym;
        }

        public void addExpansion(Frequency<String> freq) {
            expansions.add(freq);
        }

        public List<Frequency<String>> getExpansionsSortedByFreq() {
            List<Frequency<String>> list = new ArrayList<Frequency<String>>(expansions);
            Collections.sort(list, new Comparator<Frequency<String>>() {
                @Override
                public int compare(Frequency<String> o1, Frequency<String> o2) {
                    return o2.freq - o1.freq;
                }
            });
            return list;
        }

        public void calcFreq() {
            freq = 0;
            for (Frequency<String> f : expansions) {
                freq += f.freq;
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("AcronymStat{");
            sb.append("acronym='").append(acronym).append('\'');
            sb.append(", freq=").append(freq);
            sb.append("\n, expansions=\n");
            for (Frequency<String> e : expansions) {
                sb.append("\t(").append(e.freq).append(")  ").append(e.key).append("\n");
            }
            sb.append('}');
            return sb.toString();
        }
    }


    public static class Frequency<T> {
        T key;
        int freq;

        public Frequency(T key, int freq) {
            this.key = key;
            this.freq = freq;
        }
    }


    public static class ProcessedAcronym {
        String acronym;
        int freq;
        List<ExpansionVariants> expansions = new ArrayList<ExpansionVariants>(2);

        public ProcessedAcronym(String acronym, int freq) {
            this.acronym = acronym;
            this.freq = freq;
        }

        public void add(ExpansionVariants ev) {
            expansions.add(ev);
        }


        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ProcessedAcronym{");
            sb.append("acronym='").append(acronym).append('\'');
            sb.append(", freq=").append(freq);
            sb.append("\n, expansions=\n");
            for (ExpansionVariants ev : expansions) {
                sb.append("\t(").append(ev.canonFreq).append(")  ").append(ev.canonicalExpansion).append("\n");
                for (Frequency<String> v : ev.variants) {
                    sb.append("\t\t(").append(v.freq).append(")  ").append(v.key).append("\n");
                }
            }
            sb.append('}');
            return sb.toString();
        }
    }

    public static class ExpansionVariants {
        String canonicalExpansion;
        int canonFreq;
        List<Frequency<String>> variants = new LinkedList<Frequency<String>>();
        boolean emptyCluster = false;

        public ExpansionVariants(String canonicalExpansion, int canonFreq) {
            this.canonicalExpansion = canonicalExpansion;
            this.canonFreq = canonFreq;
        }


        void addVariant(Frequency<String> variant) {
            variants.add(variant);
        }

        public boolean isEmptyCluster() {
            return emptyCluster;
        }
    }

    List<PaperAcronyms> getPaperAcronyms() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<PaperAcronyms> query = em.createQuery("from PaperAcronyms", PaperAcronyms.class);
            return query.getResultList();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void cleanupExpansions(boolean skipBadEnding) throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        List<PaperAcronyms> badPAList = new LinkedList<PaperAcronyms>();
        List<PaperAcronyms> pa2RemoveList = new ArrayList<PaperAcronyms>();
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            org.hibernate.Query query = session.createQuery("from PaperAcronyms p order by p.acronym");
            ScrollableResults results = query.setReadOnly(false).setFetchSize(1000).scroll(ScrollMode.FORWARD_ONLY);
            int count = 1;

            while (results.next()) {
                PaperAcronyms pa = (PaperAcronyms) results.get(0);
                String exp = pa.getExpansion();
                if (isBadAcronym(pa)) {
                    pa2RemoveList.add(pa);

                } else {
                    if (exp.startsWith("in ") || exp.startsWith("of ") || exp.startsWith("for ")
                            || exp.startsWith("to ") || exp.startsWith("and ") || exp.startsWith("from ")) {
                        int idx = exp.indexOf(' ');
                        Assertion.assertTrue(idx != -1);
                        String correctedExp = exp.substring(idx + 1).trim();
                        pa.setExpansion(correctedExp);
                        badPAList.add(pa);
                        if (!skipBadEnding && hasBadEnding(correctedExp)) {
                            correctedExp = removeBadEnding(correctedExp);
                            pa.setExpansion(correctedExp);
                        }
                    } else if (!skipBadEnding && hasBadEnding(exp)) {
                        String correctedExp = removeBadEnding(exp);
                        pa.setExpansion(correctedExp);
                        badPAList.add(pa);
                    }
                }

                if ((count % 1000) == 0) {
                    System.out.println("Processed so far:" + count);
                }
                count++;
            }

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
        for (PaperAcronyms pa : badPAList) {
            System.out.println(pa.getAcronym() + " -- " + pa.getExpansion());
            updatePaperAcronym(pa);
        }

        for (PaperAcronyms pa : pa2RemoveList) {
            System.out.println("removing: " + pa.getAcronym() + " -- " + pa.getExpansion());
            removePaperAcronym(pa);
        }
        System.out.println("badPAList size:" + badPAList.size());

        System.out.println("pa2RemoveList size:" + pa2RemoveList.size());
    }

    public boolean isBadAcronym(PaperAcronyms pa) {
        String acr = pa.getAcronym();
        String exp = pa.getExpansion();
        if (exp.length() <= acr.length()) {
            return true;
        }
        if (acr.length() == 1) {
            if (Character.isDigit(acr.charAt(0))) {
                return true;
            }
            if (Character.isLowerCase(acr.charAt(0))) {
                return true;
            }
        }

        return false;
    }

    public static String removeBadEnding(String exp) {
        int idx = exp.lastIndexOf(' ');
        if (idx != -1) {
            return exp.substring(0, idx);
        } else {
            return exp;
        }
    }

    public static boolean hasBadEnding(String exp) {
        int idx = exp.lastIndexOf(' ');
        if (idx != -1) {
            String suffix = exp.substring(idx + 1);
            if (suffix.length() == 1 && !Character.isAlphabetic(suffix.codePointAt(0))) {
                return true;
            }
        }
        return false;
    }


    public void updatePaperAcronym(PaperAcronyms pa) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            PaperAcronyms paperAcronyms = em.find(PaperAcronyms.class, pa.getId());
            paperAcronyms.setExpansion(pa.getExpansion());

            em.merge(paperAcronyms);
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void removePaperAcronym(PaperAcronyms pa) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            PaperAcronyms paperAcronyms = em.find(PaperAcronyms.class, pa.getId());
            em.remove(paperAcronyms);
            Utils.commitTransaction(em);
        } catch (Throwable t) {
            t.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    List<AcronymStat> getGroupedPaperAcronyms() {
        EntityManager em = null;
        StatelessSession session = null;
        Map<String, AcronymStat> asMap = new LinkedHashMap<String, AcronymStat>();
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();
            org.hibernate.Query query = session.createQuery("from PaperAcronyms p order by p.acronym");
            ScrollableResults results = query.setReadOnly(false).setFetchSize(1000).scroll(ScrollMode.FORWARD_ONLY);
            String prevAcr = null;
            FrequencyTable<String> ft = null;
            int count = 1;
            while (results.next()) {
                PaperAcronyms pa = (PaperAcronyms) results.get(0);
                String acronym = pa.getAcronym();
                AcronymStat as = asMap.get(acronym);
                if (as == null) {
                    if (prevAcr != null) {
                        AcronymStat pAs = asMap.get(prevAcr);
                        List<Comparable<String>> keys = ft.getSortedKeys();
                        for (Comparable<String> k : keys) {
                            String exp = (String) k;
                            pAs.addExpansion(new Frequency<String>(exp, ft.getFrequency(exp)));
                        }
                        pAs.calcFreq();
                    }
                    as = new AcronymStat(acronym);
                    asMap.put(acronym, as);
                    ft = new FrequencyTable<String>();
                    prevAcr = acronym;
                }
                ft.addValue(pa.getExpansion());
                if ((count % 1000) == 0) {
                    System.out.println("Processed so far:" + count);
                }

                count++;
            }
            if (prevAcr != null && ft != null) {
                AcronymStat pAs = asMap.get(prevAcr);
                List<Comparable<String>> keys = ft.getSortedKeys();
                for (Comparable<String> k : keys) {
                    String exp = (String) k;
                    pAs.addExpansion(new Frequency<String>(exp, ft.getFrequency(exp)));
                }
                pAs.calcFreq();
            }
            return new ArrayList<AcronymStat>(asMap.values());
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }


    public static void main(String[] args) throws Exception {
        Injector injector = Guice.createInjector(new RDPersistModule());
        try {
            AcronymStats as = new AcronymStats();
            injector.injectMembers(as);


            as.calcStats2();

            // as.cleanupExpansions(false);
        } finally {
            JPAInitializer.stopService();
        }
    }
}
