package org.neuinfo.resource.disambiguator.services;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.Paper;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Assertion;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.InputSource;

import javax.persistence.EntityManager;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.net.URI;
import java.util.List;

/**
 * Created by bozyurt on 2/18/14.
 */
public class PaperYearByPMIDService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    static final String serviceURL = "http://nif-services.neuinfo.org/servicesv1/v1/literature/pmid";

    public void handle() throws Exception {
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();

            Query q = session.createQuery("select p.id, p.pubmedId from Paper p where p.publicationDate is null and p.pubmedId is not null");
            q.setReadOnly(true).setFetchSize(1000);
            ScrollableResults results = q.scroll(ScrollMode.FORWARD_ONLY);
            int count = 0;
            while (results.next()) {
                Integer paperId = (Integer) results.get(0);
                String pmid = (String) results.get(1);
                updatePaperYear(pmid, paperId);
                ++count;
                if ((count % 200) == 0) {
                    System.out.println("Handled so far:" + count);
                }
            }
            System.out.println("finished.");
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public void updatePaperYear(String pmid, int paperId) throws Exception {
        URIBuilder builder = new URIBuilder(serviceURL);
        builder.setParameter("pmid", pmid);
        String result = getResults(builder);

        if (result != null) {
            if (result.indexOf("Error report") != -1) {
                System.out.println(result);
                System.err.println("error for pmid:" + pmid);
                return;
            }
            NeuinfoReferenceSearchService.NeuinfoHandler handler =
                    new NeuinfoReferenceSearchService.NeuinfoHandler("zzzzzzzzzzzzzzzzz");
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            InputSource is = new InputSource(new StringReader(result));

            saxParser.parse(is, handler);

            List<ArticleRec> articles = handler.getArticles();
            for (ArticleRec ar : articles) {
                System.out.println(ar);
            }
            System.out.println("--------------------");
            if (!articles.isEmpty()) {
                ArticleRec ar = articles.get(0);
                if (ar.getPublicationDate() != null) {
                    updateYear(ar, paperId);
                }
            }
        }
    }

    void updateYear(ArticleRec rec, int paperId) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            Paper paper = em.find(Paper.class, paperId);
            Assertion.assertNotNull(paper);
            paper.setPublicationDate(rec.getPublicationDate());
            em.merge(paper);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            x.printStackTrace();
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public String getResults(URIBuilder builder) {
        HttpClient client = new DefaultHttpClient();
        HttpGet httpGet = null;
        try {
            URI uri = builder.build();
            httpGet = new HttpGet(uri);

            HttpResponse resp = client.execute(httpGet);
            HttpEntity entity = resp.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
        return null;
    }


    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            PaperYearByPMIDService service = new PaperYearByPMIDService();
            injector.injectMembers(service);
            service.handle();

        } finally {
            JPAInitializer.stopService();
        }
    }
}
