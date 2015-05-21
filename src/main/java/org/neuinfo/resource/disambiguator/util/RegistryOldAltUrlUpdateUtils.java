package org.neuinfo.resource.disambiguator.util;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;

import javax.persistence.EntityManager;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 2/19/14.
 */
public class RegistryOldAltUrlUpdateUtils {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static Pattern p = Pattern.compile(
            "Old\\s+URL:\\s+((([A-Za-z]{3,9}:(?://)?)(?:[-;:&=\\+\\$,\\w]+@)?[A-Za-z0-9.-]+|(?:www.|[-;:&=\\+\\$,\\w]+@)[A-Za-z0-9.-]+)((?:/[\\+~%/.\\w_-]*)?\\??(?:[-\\+=&;%@.\\w_]*)#?(?:[\\w]*))?)");
    static Pattern altP = Pattern.compile(
            "Alt\\.?\\s+(?:NITRC)?\\s+URL:\\s+((([A-Za-z]{3,9}:(?://)?)(?:[-;:&=\\+\\$,\\w]+@)?[A-Za-z0-9.-]+|(?:www.|[-;:&=\\+\\$,\\w]+@)[A-Za-z0-9.-]+)((?:/[\\+~%/.\\w_-]*)?\\??(?:[-\\+=&;%@.\\w_]*)#?(?:[\\w]*))?)");

    public void handle(String xmlFile) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            List<Registry> registryRecords = DisambiguatorFinder.getAllRegistryRecords(em);
            Map<String, Registry> regMap = new HashMap<String, Registry>();
            for (Registry reg : registryRecords) {
                regMap.put(reg.getNifId(), reg);
            }
            registryRecords = null;
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(new File(xmlFile));
            Element rootEl = doc.getRootElement();

            final List<?> articles = rootEl.getChildren("article");
            for (Object o : articles) {
                Element articleEl = (Element) o;
                String nifId = articleEl.getChildText("id");
                String freeText = articleEl.getChildText("freetext");
                if (freeText != null && freeText.length() > 0) {
                    final Matcher m = p.matcher(freeText);
                    String oldUrl = null;
                    String altUrl = null;
                    if (m.find()) {
                        oldUrl = m.group(1);
                        if (Utils.isValidURLFormat(oldUrl)) {
                            System.out.println(nifId + " oldUrl:" + oldUrl);
                        } else {
                            oldUrl = null;
                        }
                    }
                    Matcher m2 = altP.matcher(freeText);
                    if (m2.find()) {
                        altUrl = m2.group(1);
                        if (Utils.isValidURLFormat(altUrl)) {
                            System.out.println(nifId + " altUrl:" + altUrl);
                        } else {
                            altUrl = null;
                        }
                    }
                    if (altUrl != null || oldUrl != null) {
                        Registry reg = regMap.get(nifId);
                        if (reg != null) {
                            updateResource(reg, altUrl, oldUrl);
                        }
                    }
                }
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    void updateResource(Registry reg, String altUrl, String oldUrl) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);
            Registry r = em.find(Registry.class, reg.getId());
            r.setOldUrl(oldUrl);
            r.setAlternativeUrl(altUrl);
            em.merge(r);

            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            RegistryOldAltUrlUpdateUtils util = new RegistryOldAltUrlUpdateUtils();
            injector.injectMembers(util);

            String homeDir = System.getProperty("user.home");

            String xmlFile = homeDir + "/dev/java/resource_disambiguator/Output.xml";
            util.handle(xmlFile);
        } catch (Exception x) {
            x.printStackTrace();
        } finally {
            JPAInitializer.stopService();
        }
    }
}
