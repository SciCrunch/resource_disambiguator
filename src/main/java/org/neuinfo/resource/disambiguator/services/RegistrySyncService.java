package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Assertion;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.sql.*;
import java.util.*;

/**
 * Created by bozyurt on 1/30/14.
 */
public class RegistrySyncService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(RegistrySyncService.class);

    public void fixSuperCategoryColumn() throws Exception {
        RW rw = getResourcesFromDISCO();
        System.out.println("# of resources from dest:" + rw.resources.size());
        List<Registry> destResources = getDestResources();
        Set<String> destResourceSet = new HashSet<String>();
        for (Registry dr : destResources) {
            destResourceSet.add(dr.getResourceName());
        }
        Assertion.assertTrue(destResourceSet.size() == destResources.size());
        int updatedCount = 0;
        for (Map<String, Object> rdm : rw.resources) {
            String rn = (String) rdm.get("resource_name");
            if (destResourceSet.contains(rn)) {
                updatedCount += updateResourceSuperCategory(rdm);
            }
        }
        System.out.println("updated resource:" + updatedCount);
    }

    public void testForDuplicates() throws Exception {
        RW rw = getResourcesFromDISCO();
        System.out.println("# of resources from dest:" + rw.resources.size());
        List<Registry> destResources = getAllDestResources();
        Set<String> destResourceSet = new HashSet<String>();

        for (Iterator<Registry> iter = destResources.iterator(); iter.hasNext(); ) {
            Registry dr = iter.next();
            if (destResourceSet.contains(dr.getNifId())) {
                log.info("Duplicate nifId:" + dr.getNifId() + " "
                        + dr.getResourceName() + " :: " + dr.getId());
            } else {
                destResourceSet.add(dr.getNifId());
            }
        }
        System.out.println("----------------------------");
    }

    public void handle() throws Exception {

        RW rw = getResourcesFromDISCO();
        System.out.println("# of resources from dest:" + rw.resources.size());
        List<Registry> destResources = getAllDestResources();
        Set<String> destResourceSet = new HashSet<String>();

        for (Iterator<Registry> iter = destResources.iterator(); iter.hasNext(); ) {
            Registry dr = iter.next();
            if (destResourceSet.contains(dr.getNifId())) {
                throw new Exception("Duplicate nifId:" + dr.getNifId() + " "
                        + dr.getResourceName() + " :: " + dr.getId());
            } else {
                destResourceSet.add(dr.getNifId());
            }
        }
        Assertion.assertTrue(destResourceSet.size() == destResources.size());
        int count = 0;
        int addedCount = 0;
        int updatedCount = 0;
        Set<String> uniqDiscoNifIdSet = new HashSet<String>();
        for (Map<String, Object> rdm : rw.resources) {
            String rn = (String) rdm.get("e_uid"); //  "resource_name");
            if (uniqDiscoNifIdSet.contains(rn)) {
                System.out.println("Duplicate Nif ID from Disco:" + rn);
            } else {
                uniqDiscoNifIdSet.add(rn);
            }
            if (!destResourceSet.contains(rn)) {
                count++;
                saveResource(rdm);
                addedCount++;
            } else {
                // update resource
                updatedCount += updateResource(rdm);
            }
        }
        System.out.println(count + " needs to be added to dest db");
        System.out.println("added resources:" + addedCount);
        System.out.println("updated resource:" + updatedCount);
    }

    List<Registry> getDestResources() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            return DisambiguatorFinder.getAllRegistryRecords(em);
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    List<Registry> getAllDestResources() {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<Registry> query = em.createQuery("from Registry",
                    Registry.class);

            return query.getResultList();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    RW getResourcesFromDISCO() throws Exception {
        Connection con = null;
        try {
            // required for maven assembly plugin to 'discover and include' JDBC driver (IBO)
            Class.forName("org.postgresql.Driver");
            Properties props = new Properties();
            props.put("user", "user");
            props.put("password", "");
            con = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/disco_crawler/searchpath=dv", props);

            Statement st = con.createStatement();
            st.execute("set search_path to 'dv'");
            st.close();
            Map<String, String> rMap = new HashMap<String, String>();
            st = con.createStatement();
            ResultSet rs = st.executeQuery("select * from nlx_144509_1");
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String colName = metaData.getColumnName(i);
                String colType = metaData.getColumnTypeName(i);
                rMap.put(colName, colType);
            }
            List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
            while (rs.next()) {
                Map<String, Object> rdm = new HashMap<String, Object>();
                for (String colName : rMap.keySet()) {
                    rdm.put(colName, rs.getObject(colName));
                }
                resources.add(rdm);
            }
            rs.close();
            st.close();
            return new RW(rMap, resources);
        } finally {
            if (con != null) {
                con.close();
            }
        }
    }

    public static String getValue(Object value) {
        return value == null ? null : value.toString();
    }

    int updateResourceSuperCategory(Map<String, Object> rdm) throws Exception {
        String superCategory = getValue(rdm.get("supercategory"));
        if (superCategory == null) {
            return 0;
        }
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            TypedQuery<Registry> query = em.createQuery("from Registry r where r.nifId = :nifId",
                    Registry.class);
            query.setParameter("nifId", getValue(rdm.get("e_uid")));
            List<Registry> resultList = query.getResultList();
            int updateCount = 0;
            if (!resultList.isEmpty()) {
                Registry registry = resultList.get(0);
                registry.setSuperCategory(superCategory);
                updateCount++;
                em.merge(registry);
            }
            Utils.commitTransaction(em);
            return updateCount;
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
            return 0;
        } finally {
            Utils.closeEntityManager(em);
        }

    }

    int updateResource(Map<String, Object> rdm) throws Exception {
        String relatedTo = getValue(rdm.get("relatedto"));
        //  if (relatedTo == null) {
        //      return 0;
        //  }
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            TypedQuery<Registry> query = em.createQuery("from Registry r where r.nifId = :nifId",
                    Registry.class);
            query.setParameter("nifId", getValue(rdm.get("e_uid")));

            List<Registry> resultList = query.getResultList();
            int updateCount = 0;
            if (!resultList.isEmpty()) {
                if (resultList.size() > 1) {
                    System.out.println(">> Duplicates:");
                    for (Registry reg : resultList) {
                        System.out.println(">>  " + reg.getId() + ", " + reg.getNifId() + ", " + reg.getResourceName());
                    }
                    throw new Exception("Duplicates found for registry " + getValue(rdm.get("e_uid")));
                }
                Registry registry = resultList.get(0);
                // if (registry.getRelatedTo() == null || !registry.getRelatedTo().equals(relatedTo)) {
                if (relatedTo != null) {
                    registry.setRelatedTo(relatedTo);
                }
                String description = getValue(rdm.get("description"));
                registry.setDescription(description);
                registry.setResourceName(getValue(rdm.get("resource_name")));
                registry.setUrl(getValue(rdm.get("url")));
                registry.setSynonym(getValue(rdm.get("synonym")));
                registry.setAbbrev(getValue(rdm.get("abbrev")));

                registry.setResourceType(getValue(rdm.get("resource_type")));
                // registry.setRelatedTo(getValue(rdm.get("relatedto")));

                registry.setOldUrl(getValue(rdm.get("oldurl")));
                registry.setAlternativeUrl(getValue(rdm.get("alturl")));
                updateCount++;
                em.merge(registry);

            }
            Utils.commitTransaction(em);
            return updateCount;
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
            return 0;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void saveResource(Map<String, Object> rdm) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            Registry reg = new Registry();
            reg.setNifId(getValue(rdm.get("e_uid")));
            reg.setResourceName(getValue(rdm.get("resource_name")));
            reg.setUrl(getValue(rdm.get("url")));
            reg.setSynonym(getValue(rdm.get("synonym")));
            reg.setAbbrev(getValue(rdm.get("abbrev")));
            reg.setDescription(getValue(rdm.get("description")));

            reg.setResourceType(getValue(rdm.get("resource_type")));
            reg.setRelatedTo(getValue(rdm.get("relatedto")));

            reg.setOldUrl(getValue(rdm.get("oldurl")));
            reg.setAlternativeUrl(getValue(rdm.get("alturl")));

            em.persist(reg);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static class RW {
        Map<String, String> rMap;
        List<Map<String, Object>> resources;

        public RW(Map<String, String> rMap, List<Map<String, Object>> resources) {
            this.rMap = rMap;
            this.resources = resources;
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            RegistrySyncService service = new RegistrySyncService();
            injector.injectMembers(service);

            service.handle();

           // service.testForDuplicates();

            //service.fixSuperCategoryColumn();


        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
