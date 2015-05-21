package org.neuinfo.resource.disambiguator.util;

import org.neuinfo.resource.disambiguator.model.CheckPoint;
import org.neuinfo.resource.disambiguator.model.PaperReference;
import org.neuinfo.resource.disambiguator.model.PublisherQueryLog;
import org.neuinfo.resource.disambiguator.model.ValidationStatus;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * Created by bozyurt on 3/12/14.
 */
public class CheckPointUtils {


    public static void saveCheckPoint(EntityManager em, CheckPoint cp) throws Exception {
        try {
            Utils.beginTransaction(em);
            em.persist(cp);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        }
    }

    public static List<CheckPoint> findCheckpoints(EntityManager em, String batchId) throws Exception {
        TypedQuery<CheckPoint> query = em.createQuery(
                "from CheckPoint c where c.batchId = :batchId order by c.modTime", CheckPoint.class);

        return query.setParameter("batchId", batchId).getResultList();
    }

    /**
     * FIXME: Only for certain tables
     *
     * @param em
     * @param cp
     * @throws Exception
     */
    public static void rollback2CheckPoint(EntityManager em, CheckPoint cp) throws Exception {
        try {
            Utils.beginTransaction(em);
            String tableName = cp.getTableName();
            if (tableName.equals("rd_publisher_query_log")) {
                TypedQuery<PublisherQueryLog> query = em.createQuery("from PublisherQueryLog j where j.id >= :id",
                        PublisherQueryLog.class);
                final List<PublisherQueryLog> resultList = query.setParameter("id",
                        cp.getPkValue()).getResultList();
                for (PublisherQueryLog pql : resultList) {
                    em.remove(pql);
                }
            } else if (tableName.equals("rd_paper_reference")) {
                TypedQuery<PaperReference> query = em.createQuery(
                        "from PaperReference p where p.id >= :id", PaperReference.class);
                final List<PaperReference> resultList = query.setParameter("id", cp.getPkValue()).getResultList();
                for (PaperReference pr : resultList) {
                    em.remove(pr);
                }
            } else if (tableName.equals("rd_validation_status")) {
                TypedQuery<ValidationStatus> query = em.createQuery("from ValidationStatus v where v.id >= :id",
                        ValidationStatus.class);
                final List<ValidationStatus> resultList = query.setParameter("id", cp.getPkValue()).getResultList();
                for (ValidationStatus vs : resultList) {
                    em.remove(vs);
                }

            } else {
                throw new Exception(cp.getTableName() + " is not supported yet");
            }


            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        }
    }




}
