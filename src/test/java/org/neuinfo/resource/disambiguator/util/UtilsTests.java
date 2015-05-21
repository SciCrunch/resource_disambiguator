package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.util.GenUtils;
import junit.framework.TestSuite;
import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl;
import org.hibernate.*;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Whitelist;
import org.neuinfo.resource.disambiguator.BaseTestCase;
import org.neuinfo.resource.disambiguator.model.PaperReference;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.model.ResourceCandidate;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.xml.sax.XMLReader;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bozyurt
 */
public class UtilsTests extends BaseTestCase {

    public UtilsTests(String testName) {
        super(testName);
    }

    public void testConvertToFileURL() throws Exception {
        File f = new File("/var/indexes/PMC_OAI_201412/Int_J_Diabetol_Vasc_Disease_Res/Int_J_Diabetol_Vasc_Disease_Res_2014_Feb_17_2(1)_http_scidoc_org_articles%20pdf_ijdvr_IJDVR-02-102_pdf.nxml");

        System.out.println(Utils.convertToFileURL(f.getAbsolutePath()));
        if (f.getAbsolutePath().indexOf("%20") != -1) {
            System.out.println("Bad file name found! skipping " + f.getAbsolutePath());
        }
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        SAXParser parser = factory.newSAXParser();

        XMLReader xmlReader = parser.getXMLReader();
        xmlReader.parse(Utils.convertToFileURL(f.getAbsolutePath()));
    }

    public void testCheckURLFormat() throws Exception {
        EntityManager em = emFactory.get();
        String batchId = "201310";
        StatelessSession session = ((Session) em.getDelegate())
                .getSessionFactory().openStatelessSession();
        System.out.println("Bad URLs");

        Criteria criteria = session.createCriteria(URLRec.class)
                .setFetchMode("paper", FetchMode.JOIN)
                .setFetchMode("registry", FetchMode.JOIN)
                .add(Restrictions.eq("batchId", batchId));
        criteria.setReadOnly(false).setFetchSize(1000).setCacheable(false);
        ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);

        int count = 0;
        while (results.next()) {
            URLRec ur = (URLRec) results.get(0);
            String url = Utils.normalizeUrl(ur.getUrl());
            if (!Utils.isValidURLFormat(url)) {
                System.out.println("Bad URL:" + ur.getUrl());
                ++count;
                //session.delete(ur);
            }
        }
        session.close();

        System.out.println("bad URL count:" + count);
    }

    public void testFilterResourceCandidates() throws Exception {
        EntityManager em = emFactory.get();
        String batchId = "201310";
        StatelessSession session = ((Session) em.getDelegate())
                .getSessionFactory().openStatelessSession();
        Criteria criteria = session.createCriteria(ResourceCandidate.class)
                .setFetchMode("url", FetchMode.JOIN)
                .add(Restrictions.eq("batchId", batchId));

        criteria.setReadOnly(true).setFetchSize(1000).setCacheable(false);
        ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);

        int count = 0;
        while (results.next()) {
            ResourceCandidate rc = (ResourceCandidate) results.get(0);
            if (rc.getUrl() == null) {
                session.delete(rc);
                continue;
            }
            String url = rc.getUrl().getUrl();
            if (Utils.numOfMatches(url, '/') > 4) {
                System.out.println(url);
                ++count;
            }
        }

        System.out.println("bad URL count:" + count);
        session.close();
    }

    public void testNormalizeRedirectURI() throws Exception {
        EntityManager em = emFactory.get();
        TypedQuery<RegistrySiteContent> query = em.createQuery(
                "from RegistrySiteContent r  where r.redirectUrl is not null",
                RegistrySiteContent.class);
        for (RegistrySiteContent rsc : query.getResultList()) {
            System.out.println(rsc.getRedirectUrl() + "  orig url:" + rsc.getRegistry().getUrl());
            URL redirectUrl = RedirectUtils.normalizeRedirectURL(rsc.getRegistry().getUrl(), rsc.getRedirectUrl());
            //boolean valid = RedirectUtils.isRedirectUrlValid(redirectUrl.toString());
            boolean valid = true;
            System.out.println("redirect url:" + redirectUrl + " valid:" + valid);
            if (!valid) {
                System.out.println(">>>>>>>>>>>>");
            }
            System.out.println("---------------------------");
        }
    }

    public void testAnnotationClient() throws Exception {
        EntityManager em = emFactory.get();
        StatelessSession session = ((Session) em.getDelegate())
                .getSessionFactory().openStatelessSession();

        Criteria criteria = session.createCriteria(URLRec.class)
                .add(Restrictions.isNotNull("description"))
                .add(Restrictions.ge("score", (double) 10))
                .addOrder(Order.desc("score"));

        criteria.setReadOnly(true).setFetchSize(1000).setCacheable(false);
        ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);
        int count = 0;
        Map<String, String> paramsMap = new HashMap<String, String>(7);
        paramsMap.put("longestOnly", "true");
        paramsMap.put("includeCat",
                "biological_process,anatomical_structure,resource");
        // paramsMap.put("includeCat", "biological_process");

        while (results.next()) {
            URLRec ur = (URLRec) results.get(0);
            AnnotationServiceClient client = new AnnotationServiceClient();
            String annotated = client.annotatePost(ur.getDescription(),
                    paramsMap);
            System.out.println(annotated);
            System.out.println();
            Map<String, Integer> types = client.getCategories(annotated);
            for (String key : types.keySet()) {
                System.out.println("\t" + key + " -> " + types.get(key));
            }
            count++;
            if (count > 20) {
                break;
            }
        }
        session.close();
    }

    public void testFTPClientBufferSize() throws Exception {
        String indexRootDir = "/var/indexes";
        FtpClient client = new FtpClient("ftp.ncbi.nlm.nih.gov");
        String remoteFile = "pub/pmc/articles.I-N.tar.gz";
        int idx = remoteFile.lastIndexOf('/');
        String basename = remoteFile.substring(idx + 1);
        File localFile = new File(indexRootDir, basename);
        client.transferFile(localFile.getAbsolutePath(), remoteFile,
                true);
    }

    public void testDOI2PMIDServiceClient() throws Exception {
        String pmid = "22323214";
        DOI2PMIDServiceClient.PMArticleInfo pmai = DOI2PMIDServiceClient.getCitationInfo(pmid);
        assertNotNull(pmai);

    }

    public void testDOI2PMIDLookupUtil() throws Exception {
        DOI2PMIDLookupUtil pmidLookupUtil = DOI2PMIDLookupUtil.getInstance();

        EntityManager em = emFactory.get();
        StatelessSession session = ((Session) em.getDelegate())
                .getSessionFactory().openStatelessSession();

        Criteria criteria = session.createCriteria(PaperReference.class)
                .setReadOnly(false).setFetchSize(1000).setCacheable(false);
        ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);
        int count = 0;
        int pmidCount = 0;
        while (results.next()) {
            PaperReference pr = (PaperReference) results.get(0);
            String doi = pr.getPublisherDocId();
            String pmid = pmidLookupUtil.getPMID(doi);
            if (pmid != null) {
                System.out.println("DOI:" + doi + " PMID:" + pmid);
                pmidCount++;
            }

            count++;
            if (count > 1000) {
                break;
            }
        }
        session.close();
        System.out.println("count:" + count + " pmidCount:" + pmidCount);
        pmidLookupUtil.shutdown();
    }

    public void testDescriptionExtractor() throws Exception {
        String urlStr = "http://www.bio.unc.edu/faculty/vision/lab/mappop/";
        urlStr = "http://diprogb.fli-leibniz.de/";
        URL url = new URL(urlStr);
        DescriptionExtractor de = new DescriptionExtractor(url,
                OpMode.FULL_CONTENT);

        String content = de.getContent();
        assertNotNull(content);
        // content = content.replaceAll("(?:\\s*\\n)+", "\n");
        System.out.println(content);
    }

    public void testHtmlSaxHandler() throws Exception {
        InputStream in = null;
        try {
            in = new FileInputStream("/tmp/test.html");
            HtmlSaxHandler handler = new HtmlSaxHandler(OpMode.FULL_CONTENT, "");
            SAXParserImpl.newInstance(null).parse(in, handler);

            System.out.println(handler.getContent());
        } finally {
            Utils.close(in);
        }
    }

    public void testJSoup() throws Exception {

        Document doc = Jsoup.parse(new File("/tmp/test.html"), CharSetEncoding.UTF8.toString());
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
        doc.select("br").append("\\n");
        doc.select("p").prepend("\\n\\n");
        String title = doc.title();
        String s = doc.html().replaceAll("\\\\n", "\n");
        String content = Jsoup.clean(s, "", Whitelist.none(),
                new Document.OutputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml));
        content = content.replaceAll("\\n\\s*\\n+", "\n");
        // String content = doc.body().text();
        // content = StringEscapeUtils.escapeHtml(content);
        content = content.replaceAll("&gt;", ">");
        content = content.replaceAll("&lt;", "<");

        System.out.println(content);
        System.out.println("title:" + title);
    }

    public void testRegistryContentDownload() throws Exception {
        EntityManager em = emFactory.get();
        TypedQuery<RegistrySiteContent> query = em.createQuery(
                "from RegistrySiteContent r where length(r.content) = 0 and r.redirectUrl is null",
                RegistrySiteContent.class);
        List<RegistrySiteContent> resultList = query.getResultList();
        int count = 0;
        for (RegistrySiteContent rsc : resultList) {
            String urlStr = rsc.getRegistry().getUrl();
            urlStr = Utils.extractUrl(urlStr);
            try {
                Document doc = Jsoup.connect(urlStr).get();

                String content = doc.body().text();
                assertNotNull(content);

                System.out.println(GenUtils.formatText(content, 100));
            } catch (Exception x) {
                System.out.println(x.getMessage());
                count++;
            }
        }
        System.out.println("bad registry count:" + count);
    }


    public void testGetRedirect() throws Exception {
        EntityManager em = emFactory.get();
        TypedQuery<RegistrySiteContent> query = em.createQuery(
                "from RegistrySiteContent r  where r.redirectUrl is not null",
                RegistrySiteContent.class);
        for (RegistrySiteContent rsc : query.getResultList()) {
            String urlStr = Utils.extractUrl(rsc.getRegistry().getUrl());
            if (rsc.getRedirectUrl().equals(urlStr)) {
                URLValidator validator = new URLValidator(urlStr,
                        OpMode.FULL_CONTENT);

                URLContent urlContent = validator.checkValidity(false);
                if (urlContent != null) {
                    if (RedirectUtils.isAValidRedirect(urlContent.getFinalRedirectURI(), urlStr)) {
                        URL redirectURL = RedirectUtils.normalizeRedirectURL(
                                urlContent.getFinalRedirectURI().toString(), urlStr);
                        if (RedirectUtils.isRedirectUrlValid(redirectURL.toString())) {
                            urlContent = validator.checkValidity(false);
                            assertNotNull(urlContent);
                            rsc.setRedirectUrl(redirectURL.toString());
                        }
                    }
                }
            }
            if (!rsc.getRedirectUrl().equals(urlStr)) {
                System.out.println(rsc.getRedirectUrl() + "  orig url:" + rsc.getRegistry().getUrl());
                URL redirectUrl = RedirectUtils.normalizeRedirectURL(rsc.getRegistry().getUrl(), rsc.getRedirectUrl());
                //boolean valid = RedirectUtils.isRedirectUrlValid(redirectUrl.toString());
                boolean valid = true;
                System.out.println("redirect url:" + redirectUrl + " valid:" + valid);
                if (!valid) {
                    System.out.println(">>>>>>>>>>>>");
                }
                System.out.println("---------------------------");
            }
        }
    }


    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
        // suite.addTest(new UtilsTests("testCheckURLFormat"));
        // suite.addTest(new UtilsTests("testFilterResourceCandidates"));
        // suite.addTest(new UtilsTests("testAnnotationClient"));
        //suite.addTest(new UtilsTests("testFTPClientBufferSize"));
//        suite.addTest(new UtilsTests("testNormalizeRedirectURI"));
        //  suite.addTest(new UtilsTests("testDOI2PMIDLookupUtil"));

        //   suite.addTest(new UtilsTests("testDescriptionExtractor"));
        //suite.addTest(new UtilsTests("testRegistryContentDownload"));
        //  suite.addTest(new UtilsTests("testHtmlSaxHandler"));
        //    suite.addTest(new UtilsTests("testJSoup"));
        // suite.addTest(new UtilsTests("testGetRedirect"));
        suite.addTest(new UtilsTests("testDOI2PMIDServiceClient"));

        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

}
