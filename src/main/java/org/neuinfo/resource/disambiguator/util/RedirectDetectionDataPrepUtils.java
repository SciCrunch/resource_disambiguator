package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Created by bozyurt on 3/26/14.
 */
public class RedirectDetectionDataPrepUtils {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Logger log = Logger.getLogger(RegistrySiteContentPopulator.class);

    public void prepTrainingDataFile(File outXmlFile) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            TypedQuery<RegistrySiteContent> query = em.createQuery(
                    "from RegistrySiteContent r where r.flags = 1 and r.content like :str ", RegistrySiteContent.class);

            List<RegistrySiteContent> resultList = query.setParameter("str", "% moved%").getResultList();

            Element root = new Element("data-set");
            Document doc = new Document(root);

            for (RegistrySiteContent rsc : resultList) {
                Element el = new Element("data");
                el.setAttribute("type", "rsc");
                el.setAttribute("label", "");
                el.setAttribute("registry_nif_id", rsc.getRegistry().getNifId());
                el.setAttribute("url", rsc.getRegistry().getUrl());
                el.setText(rsc.getContent());
                root.addContent(el);
            }

            BufferedOutputStream bout = null;
            try {
                bout = new BufferedOutputStream(new FileOutputStream(outXmlFile));
                XMLOutputter serializer = new XMLOutputter(Format.getPrettyFormat());
                serializer.output(doc, bout);
                bout.flush();
            } finally {
                 Utils.close(bout);
            }

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void main(String[] args) throws Exception {
          Injector injector = Guice.createInjector(new RDPersistModule());
        RedirectDetectionDataPrepUtils util = new RedirectDetectionDataPrepUtils();
        try {
            injector.injectMembers(util);

            util.prepTrainingDataFile(new File("/tmp/redirect_tr.xml"));
        } finally {
            JPAInitializer.stopService();
        }
    }

}
