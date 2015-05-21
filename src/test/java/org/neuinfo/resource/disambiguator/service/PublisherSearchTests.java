package org.neuinfo.resource.disambiguator.service;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.util.FileUtils;
import bnlpkit.util.GenUtils;
import junit.framework.TestSuite;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.neuinfo.resource.disambiguator.BaseTestCase;
import org.neuinfo.resource.disambiguator.model.Publisher;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.services.*;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.InputSource;

import javax.persistence.EntityManager;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Created by bozyurt on 3/4/14.
 */
public class PublisherSearchTests extends BaseTestCase {


    public PublisherSearchTests(String name) {
        super(name);
    }


    public void testSpringerSearch() throws Exception {
        String serviceURL = "http://api.springer.com/metadata/pam";
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Registry registry = DisambiguatorFinder.getRegistryByName(em, "ModelDB");
            Publisher publisher = DisambiguatorFinder.getPublisher(em, "Springer");
            List<String> apiKeys = BaseReferenceSearchService.getApiKeys(publisher);
            List<QueryCandidate> candidates = BaseReferenceSearchService.prepSearchTermCandidates(registry);
            int numKeys = apiKeys.size();
            int count = 1;
            String toMatch = "parallel network";
            toMatch = "human cardiac fibroblasts";
            for (QueryCandidate qc : candidates) {
                String candidate = qc.getCandidate();
                String apiKey = apiKeys.get((count % numKeys));
                URIBuilder builder = new URIBuilder(serviceURL);
                builder.setParameter("q", candidate);
                builder.setParameter("p", "1000");
                builder.setParameter("api_key", apiKey);

                System.out.println("query:" + builder.build());
                String result = getResult(builder);
                SpringerReferenceSearchService.SpringerHandler handler = new SpringerReferenceSearchService.SpringerHandler(candidate);
                SAXParserFactory factory = SAXParserFactory.newInstance();
                SAXParser saxParser = factory.newSAXParser();
                InputSource is = new InputSource(new StringReader(result));
                saxParser.parse(is, handler);

                int total = handler.getTotal();
                if (total > 0) {
                    List<ArticleRec> articles = handler.getArticles();
                    for (ArticleRec ar : articles) {
                        if (ar.getTitle().toLowerCase().indexOf(toMatch) != -1) {
                            System.out.println(ar);
                        }
                    }
                    if (total > 100) {
                        int start = 100;
                        while (start < total) {
                            URIBuilder uriBuilder = buildNextPageURI(serviceURL, candidate, apiKey, start);
                            handler = getNextBatch(uriBuilder, candidate);
                            articles = handler.getArticles();
                            for (ArticleRec ar : articles) {
                                if (ar.getTitle().toLowerCase().indexOf(toMatch) != -1) {
                                    System.out.println(ar);
                                }
                            }

                            start += 100;
                        }

                    }

                    System.out.println("---------------------------------");
                }
                count++;
            }
        } finally {
            Utils.closeEntityManager(em);
        }


    }

    public static URIBuilder buildNextPageURI(String serviceURL, String candidate,
                                              String apiKey, int start) throws URISyntaxException {
        URIBuilder builder = new URIBuilder(serviceURL);
        builder.setParameter("q", candidate);
        builder.setParameter("p", "100");
        builder.setParameter("api_key", apiKey);
        if (start > 0) {
            builder.setParameter("s", String.valueOf(start));
        }
        return builder;
    }

    SpringerReferenceSearchService.SpringerHandler getNextBatch(URIBuilder builder, String candidate) throws Exception {
        System.out.println("query:" + builder.build());
        String result = getResult(builder);
        SpringerReferenceSearchService.SpringerHandler handler = new SpringerReferenceSearchService.SpringerHandler(candidate);
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        InputSource is = new InputSource(new StringReader(result));
        saxParser.parse(is, handler);

        return handler;
    }

    public void testNatureSearch() throws Exception {
        EntityManager em = null;
        final String serviceURL = "http://api.nature.com/content/opensearch/request";
        try {
            em = Utils.getEntityManager(emFactory);
            Registry registry = DisambiguatorFinder.getRegistryByName(em, "WormBase");
            Publisher publisher = DisambiguatorFinder.getPublisher(em, "Nature");
            List<String> apiKeys = BaseReferenceSearchService.getApiKeys(publisher);
            List<QueryCandidate> candidates = BaseReferenceSearchService.prepSearchTermCandidates(registry);
            int numKeys = apiKeys.size();
            boolean found = false;
            int count = 1;
            for (QueryCandidate qc : candidates) {
                String candidate = qc.getCandidate();
                String apiKey = apiKeys.get((count % numKeys));
                URIBuilder builder = new URIBuilder(serviceURL);
                builder.setParameter("queryType", "cql");
                builder.setParameter("query", "cql.keywords==\"" + candidate + "\"");
                builder.setParameter("maximumRecords", "200");
                builder.setParameter("api_key", apiKey);

                System.out.println("query:" + builder.build());

                String result = getResult(builder);
                if (result == null || result.indexOf("Server Error") != -1
                        || result.indexOf("Internal server error") != -1) {
                    continue;
                }
                NatureReferenceSearchService.NatureHandler handler =
                        new NatureReferenceSearchService.NatureHandler(candidate);
                SAXParserFactory factory = SAXParserFactory.newInstance();
                SAXParser saxParser = factory.newSAXParser();

                FileUtils.saveText(result, "/tmp/nature_result.xml", CharSetEncoding.UTF8);
                System.out.println(GenUtils.formatText(result, 100));
                InputSource is = new InputSource(new StringReader(result));
                saxParser.parse(is, handler);


                int total = handler.getTotal();
                if (total > 0) {
                    List<ArticleRec> articles = handler.getArticles();
                    for (ArticleRec ar : articles) {
                        System.out.println(ar);
                    }
                    break;
                }
                count++;
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void testNeuinfoSearch2() throws Exception {
        EntityManager em = null;
        final String serviceURL = "http://nif-services.neuinfo.org/servicesv1/v1/literature/search";
        try {
            em = Utils.getEntityManager(emFactory);
            // String q = "phm.utoronto.ca/~jeffh/neuromouse.htm";
            String q = "www.virtualflybrain.org";
            QueryCandidate qc = new QueryCandidate(q, QueryCandidate.URL);
            String candidate = qc.getCandidate();
            URIBuilder builder = new URIBuilder(serviceURL);

            builder.setParameter("q", candidate);
            builder.setParameter("count", "1000");

            System.out.println("query:" + builder.build());
            System.out.println("candidate:" + candidate);

            String result = getResult(builder);
            if (result == null || result.indexOf("Server Error") != -1
                    || result.indexOf("Internal server error") != -1) {
                System.out.println("An error has occurred");
                System.out.println(GenUtils.formatText(result, 100));
            } else {
                FileUtils.saveText(result, "/tmp/nif_result.xml", CharSetEncoding.UTF8);

                NeuinfoReferenceSearchService.NeuinfoHandler handler = new NeuinfoReferenceSearchService.NeuinfoHandler(candidate);
                SAXParserFactory factory = SAXParserFactory.newInstance();
                SAXParser saxParser = factory.newSAXParser();
                InputSource is = new InputSource(new StringReader(result));
                saxParser.parse(is, handler);

                int total = handler.getTotal();
                if (total > 0) {
                    List<ArticleRec> articles = handler.getArticles();
                    for (ArticleRec ar : articles) {
                        System.out.println(ar);
                    }
                }
            }

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void testNeuinfoSearch() throws Exception {
        EntityManager em = null;
        final String serviceURL = "http://nif-services.neuinfo.org/servicesv1/v1/literature/search";
        try {
            em = Utils.getEntityManager(emFactory);
            Registry registry = DisambiguatorFinder.getRegistryByName(em, "Gemma");
            Publisher publisher = DisambiguatorFinder.getPublisher(em, "neuinfo.org");
            List<QueryCandidate> candidates = BaseReferenceSearchService.prepSearchTermCandidates(registry);
            int count = 1;
            for (QueryCandidate qc : candidates) {
                String candidate = qc.getCandidate();
                URIBuilder builder = new URIBuilder(serviceURL);

                builder.setParameter("q", candidate);
                builder.setParameter("count", "1000");

                System.out.println("query:" + builder.build());
                System.out.println("candidate:" + candidate);

                String result = getResult(builder);
                if (result == null || result.indexOf("Server Error") != -1
                        || result.indexOf("Internal server error") != -1) {
                    continue;
                }

                FileUtils.saveText(result, "/tmp/nif_result.xml", CharSetEncoding.UTF8);

                NeuinfoReferenceSearchService.NeuinfoHandler handler = new NeuinfoReferenceSearchService.NeuinfoHandler(candidate);
                SAXParserFactory factory = SAXParserFactory.newInstance();
                SAXParser saxParser = factory.newSAXParser();
                InputSource is = new InputSource(new StringReader(result));
                saxParser.parse(is, handler);

                int total = handler.getTotal();
                if (total > 0) {
                    List<ArticleRec> articles = handler.getArticles();
                    for (ArticleRec ar : articles) {
                        System.out.println(ar);
                    }
                    break;
                }
                count++;
            }
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void testNeuinfoSearchFullText() throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Registry registry = DisambiguatorFinder.getRegistryByName(em, "ModelDB");
            NeuinfoReferenceSearchService service = new NeuinfoReferenceSearchService();

            super.injector.injectMembers(service);

            service.initPublisher();

            service.handleRegistry(registry);

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void testNatureSearchFullText() throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Registry registry = DisambiguatorFinder.getRegistryByName(em, "ModelDB");
            NatureReferenceSearchService service = new NatureReferenceSearchService();

            super.injector.injectMembers(service);

            service.initPublisher();

            service.handleRegistry(registry);

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void testSpringerSearchFullText() throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Registry registry = DisambiguatorFinder.getRegistryByName(em, "ModelDB");
            SpringerReferenceSearchService service = new SpringerReferenceSearchService();


            super.injector.injectMembers(service);

            service.initPublisher();

            service.handleRegistry(registry);

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public String getResult(URIBuilder builder) {
        DefaultHttpClient client = new DefaultHttpClient();
        client.addRequestInterceptor(new HttpRequestInterceptor() {
            @Override
            public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
                final Header[] allHeaders = request.getAllHeaders();
                System.out.println("----- Headers --------");
                for (Header header : allHeaders) {
                    System.out.println(header.toString());
                }
                System.out.println("----------------------");
            }
        });

        HttpGet httpGet = null;
        try {
            URI uri = builder.build();
            httpGet = new HttpGet(uri);

            httpGet.addHeader("Accept","application/xml");
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


    public static TestSuite suite() {
        TestSuite suite = new TestSuite();

        //suite.addTest(new PublisherSearchTests("testNatureSearch"));
        // suite.addTest(new PublisherSearchTests("testNeuinfoSearch"));

        //suite.addTest(new PublisherSearchTests("testNeuinfoSearchFullText"));
        //  suite.addTest(new PublisherSearchTests("testNatureSearchFullText"));
        //  suite.addTest(new PublisherSearchTests("testSpringerSearchFullText"));

        //  suite.addTest(new PublisherSearchTests("testSpringerSearch"));
        suite.addTest(new PublisherSearchTests("testNeuinfoSearch2"));
        return suite;
    }


    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());

    }
}
