package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.DownSiteStatus;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 8/14/14.
 */
public class DownSiteStatusService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(DownSiteStatusService.class);


    public void handle() throws Exception {
        List<Registry> registryList = getRegistryList();
        Map<Long, RegistryInfo> regMap = new HashMap<Long, RegistryInfo>();
        for (Registry reg : registryList) {
            RegistryInfo ri = new RegistryInfo(reg.getId(), reg.getNifId(), reg.getResourceName(), reg.getUrl());
            regMap.put(reg.getId(), ri);
        }
        registryList = null;
        EntityManager em = null;
        StatelessSession session = null;
        Map<Long, ValidationStatusStat> vssMap = new HashMap<Long, ValidationStatusStat>();
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();

            Query query = session.createQuery("select v.registry.id, v.up, v.message, v.lastCheckedTime " +
                    "from ValidationStatus v order by v.registry.id, v.lastCheckedTime desc");
            ScrollableResults results = query.setReadOnly(true).setFetchSize(1000).scroll(ScrollMode.FORWARD_ONLY);
            while (results.next()) {
                Long registryId = results.getLong(0);
                boolean isUp = results.getBoolean(1);
                String message = results.getString(2);
                Calendar lastCheckedTime = results.getCalendar(3);
                ValidationStatusStat vss = vssMap.get(registryId);
                if (vss == null) {
                    RegistryInfo ri = regMap.get(registryId);
                    if (ri == null) {
                        // skip bad registryIds
                        continue;
                    }
                    ValStatRec vsr = new ValStatRec(ri, lastCheckedTime, message);
                    vss = new ValidationStatusStat(vsr);
                    vssMap.put(registryId, vss);
                }
                vss.addUp(isUp);
            }
            results.close();
            Pattern p = Pattern.compile(" connection\\s +.+\\s + refused ");
            List<ValidationStatusStat> vssList = new LinkedList<ValidationStatusStat>();
            for (ValidationStatusStat vss : vssMap.values()) {
                if (vss.isInvalid()) {
                    String msg = vss.vs.message;
                    boolean ok = true;
                    // if temporary problems or authentication issues assume the site is valid
                    if (!Utils.isEmpty(msg)) {
                        msg = msg.toLowerCase();
                        if (msg.indexOf("peer not authenticated") != -1 ||
                                msg.indexOf("temporary failure in name resolution") != -1) {
                            ok = false;
                        }
                        if (ok) {
                            Matcher matcher = p.matcher(msg);
                            if (matcher.find()) {
                                ok = false;
                            }
                        }
                    }
                    if (ok) {
                        vssList.add(vss);
                    }
                }
            }
            vssMap = null;
            for (ValidationStatusStat vss : vssList) {
                addUpdateDownSiteStatus(vss);
            }
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public void addUpdateDownSiteStatus(ValidationStatusStat vss) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            TypedQuery<DownSiteStatus> query = em.createQuery("from DownSiteStatus d where nifId = :nifId",
                    DownSiteStatus.class);
            List<DownSiteStatus> existingList = query.setParameter("nifId", vss.vs.ri.nifId).getResultList();
            if (existingList.isEmpty()) {
                DownSiteStatus dss = new DownSiteStatus();
                dss.setNifId(vss.vs.ri.nifId);
                dss.setResourceName(vss.vs.ri.resourceName);
                dss.setUrl(vss.vs.ri.url);
                dss.setLastCheckedTime(vss.vs.lastCheckTime);
                dss.setMessage(vss.vs.message);
                dss.setNumOfConsecutiveChecks(vss.getConsecutiveCount());
                dss.setBatchId(Utils.prepBatchId());

                em.persist(dss);

            } else {
                DownSiteStatus dss = existingList.get(0);
                dss.setLastCheckedTime(vss.vs.lastCheckTime);
                dss.setMessage(vss.vs.message);
                dss.setNumOfConsecutiveChecks(vss.getConsecutiveCount());
                em.merge(dss);
            }

            Utils.commitTransaction(em);
        } catch (Exception x) {
            x.printStackTrace();
            Utils.rollbackTransaction(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }


    public static class ValidationStatusStat {
        ValStatRec vs;
        private int consecutiveCount = 0;
        List<Boolean> upList = new LinkedList<Boolean>();

        public ValidationStatusStat(ValStatRec vs) {
            this.vs = vs;
        }

        public void addUp(Boolean wasUp) {
            upList.add(wasUp);
        }

        boolean isInvalid() {
            int count = 0;
            if (!upList.isEmpty() && upList.get(0)) {
                return false;
            }
            for (int i = 0; i < upList.size(); i++) {
                if (!upList.get(i)) {
                    if (i == 0 || !upList.get(i - 1)) {
                        count++;
                    } else if (i != 0) {
                        break;
                    }
                }
            }
            consecutiveCount = count;
            return count >= 3;
        }

        public int getConsecutiveCount() {
            return consecutiveCount;
        }
    }

    public static class RegistryInfo {
        Long id;
        String nifId;
        String resourceName;
        String url;

        public RegistryInfo(Long id, String nifId, String resourceName, String url) {
            this.id = id;
            this.nifId = nifId;
            this.resourceName = resourceName;
            this.url = url;
        }
    }

    public static class ValStatRec {
        RegistryInfo ri;
        Calendar lastCheckTime;
        String message;

        public ValStatRec(RegistryInfo ri, Calendar lastCheckTime, String message) {
            this.ri = ri;
            this.lastCheckTime = lastCheckTime;
            this.message = message;
        }
    }

    protected List<Registry> getRegistryList() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            return DisambiguatorFinder.getAllRegistryRecords(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("DownSiteStatusService", options);
        System.exit(1);
    }

    public static void cli(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Options options = new Options();
        options.addOption(help);

        CommandLineParser cli = new DefaultParser();
        CommandLine line = null;
        try {
            line = cli.parse(options, args);
        } catch (Exception x) {
            System.err.println(x.getMessage());
            usage(options);
        }

        if ((line != null) && line.hasOption("h")) {
            usage(options);
        }
        Injector injector = Guice.createInjector(new RDPersistModule());
        DownSiteStatusService service = new DownSiteStatusService();
        try {
            injector.injectMembers(service);

            service.handle();
        } finally {
            JPAInitializer.stopService();
        }
    }

    public static void main(String[] args) throws Exception {
        cli(args);
    }
}
