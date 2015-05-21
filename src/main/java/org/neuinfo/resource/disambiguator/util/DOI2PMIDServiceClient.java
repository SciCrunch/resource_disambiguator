package org.neuinfo.resource.disambiguator.util;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DOI2PMIDServiceClient {
    private static String serviceURL = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    static Pattern pattern = Pattern.compile("<Id>(\\d+)</Id>");

    private DOI2PMIDServiceClient() {
    }

    public static String getPMID(String doi) throws Exception {
        HttpClient client = new DefaultHttpClient();
        URIBuilder builder = new URIBuilder(serviceURL);
        builder.setParameter("db", "PubMed");
        builder.setParameter("retmode", "xml");
        builder.setParameter("term", doi);
        // builder.setParameter("field", "LID");
        builder.setParameter("field", "title");
        URI uri = builder.build();
        HttpGet httpGet = new HttpGet(uri);
        try {
            HttpResponse resp = client.execute(httpGet);
            HttpEntity entity = resp.getEntity();
            if (entity != null) {
                String s = EntityUtils.toString(entity);
                System.out.println(s);
                Matcher matcher = pattern.matcher(s);
                if (matcher.find()) {
                    if (hasOnlyOneId(s)) {
                        return matcher.group(1);
                    }
                }
            }
        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
        return null;
    }

    public static String getPMID(String publicationName, String pubTitle)
            throws Exception {
        HttpClient client = new DefaultHttpClient();
        URIBuilder builder = new URIBuilder(serviceURL);
        builder.setParameter("db", "PubMed");
        builder.setParameter("retmode", "xml");
        StringBuilder sb = new StringBuilder();
        sb.append('"').append(pubTitle).append('"');

        builder.setParameter("term", sb.toString());
        // builder.setParameter("field", "LID");
        builder.setParameter("field", "title");
        URI uri = builder.build();
        HttpGet httpGet = new HttpGet(uri);
        try {
            HttpResponse resp = client.execute(httpGet);
            HttpEntity entity = resp.getEntity();
            if (entity != null) {
                String s = EntityUtils.toString(entity);
                // System.out.println(s);
                Matcher matcher = pattern.matcher(s);
                if (matcher.find()) {
                    List<String> pmidList = getPMIDs(s);
                    if (pmidList != null) {
                        for (String pmid : pmidList) {
                            PMArticleInfo pmai = getCitationInfo(pmid);
                            if (pmai != null
                                    && pmai.matches(publicationName, pubTitle)) {
                                System.out.println("FOUND: " + pmid);
                                return pmid;
                            }
                        }

                    }
                }
            }
        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
        return null;
    }

    public static PMArticleInfo getCitationInfo(String pmid) throws Exception {

        HttpClient client = new DefaultHttpClient();
        URIBuilder builder = new URIBuilder(
                "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi");
        builder.setParameter("db", "PubMed");
        builder.setParameter("retmode", "xml");
        builder.setParameter("id", pmid);
        URI uri = builder.build();
        HttpGet httpGet = new HttpGet(uri);
        try {
            HttpResponse resp = client.execute(httpGet);
            HttpEntity entity = resp.getEntity();
            if (entity != null) {
                String s = EntityUtils.toString(entity);
                //  System.out.println(s);

                SAXBuilder saxBuilder = new SAXBuilder();
                Document doc = saxBuilder.build(new StringReader(s));
                Element rootNode = doc.getRootElement();
                if (rootNode == null) {
                    return null;
                }
                Element pae = rootNode.getChild("PubmedArticle");
                if (pae == null) {
                    return null;
                }
                Element mce = pae.getChild("MedlineCitation");
                if (mce == null) {
                    return null;
                }
                Element ac = mce.getChild("Article");
                if (ac == null) {
                    return null;
                }
                Element jc = ac.getChild("Journal");
                PMArticleInfo pmai = new PMArticleInfo();
                if (jc != null) {
                    String journal = jc.getChildText("Title");
                    System.out.println("Journal:" + journal);
                    pmai.journal = journal;
                }
                Element ai = ac.getChild("ArticleTitle");
                if (ai != null) {
                    String title = ac.getChildTextTrim("ArticleTitle");
                    System.out.println("Title:" + title);
                    pmai.title = title;
                }
                Element al = ac.getChild("AuthorList");
                if (al != null) {
                    List<Element> authorEls = al.getChildren("Author");
                    for (Element authorEl : authorEls) {
                        String lastName = authorEl.getChildText("LastName");
                        String foreName = authorEl.getChildText("ForeName");
                        StringBuilder sb = new StringBuilder();
                        if (foreName != null) {
                            sb.append(foreName);
                        }
                        if (lastName != null) {
                            if (foreName != null) {
                                sb.append(' ');
                            }
                            sb.append(lastName);
                        }
                        String author = sb.toString();
                        System.out.println("Author:" + author);
                        pmai.addAuthor(author);
                    }
                }
                return pmai;
            }

        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
        return null;
    }

    public static class PMArticleInfo {
        String journal;
        String title;
        List<String> authorList = new ArrayList<String>(2);

        public PMArticleInfo() {
        }

        public PMArticleInfo(String journal, String title) {
            this.journal = journal;
            this.title = title;
        }

        public void addAuthor(String author) {
            authorList.add(author);
        }

        public String getJournal() {
            return journal;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getAuthorList() {
            return authorList;
        }

        public boolean matches(String aJournal, String aTitle) {
            boolean ok = similarEnough(aTitle, title);
            if (ok && aJournal != null && journal != null) {
                return similarEnough(aJournal, journal);
            }
            return ok;
        }

        public static boolean similarEnough(String ref, String other) {
            ref = ref.toLowerCase();
            other = other.toLowerCase();
            if (ref.equals(other)) {
                return true;
            }
            if (ref.startsWith(other)) {
                return true;
            } else if (other.startsWith(ref)) {
                return true;
            }
            int ed = Utils.levenshteinDistance(ref.toCharArray(), other.toCharArray());
            double frac = ed / (double) ref.length();
            if (frac <= 0.1) {
                return true;
            }
            return false;
        }
    }

    static boolean hasOnlyOneId(String xml) throws Exception {
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(new StringReader(xml));
        Element rootNode = doc.getRootElement();
        if (rootNode.getChild("Count") != null) {
            int count = Integer.parseInt(rootNode.getChildTextTrim("Count"));
            if (count == 1) {
                return true;
            }
        }
        return false;
    }

    static List<String> getPMIDs(String xml) throws Exception {
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(new StringReader(xml));
        Element rootNode = doc.getRootElement();
        int count = 0;
        if (rootNode.getChild("Count") != null) {
            count = Integer.parseInt(rootNode.getChildTextTrim("Count"));
        }
        if (count == 0 || count > 100) {
            return null;
        }
        List<String> pmidList = new ArrayList<String>(count);
        List<Element> children = rootNode.getChild("IdList").getChildren("Id");
        for (Element c : children) {
            pmidList.add(c.getTextTrim());
        }
        return pmidList;
    }

    public static void main(String[] args) throws Exception {
        String doi = "doi:10.2165/00126839-200809040-0000";
        doi = "10.2165/00128415-201113410-0000";
        // System.out.println(getPMID(doi));
        String pmid = "24198878";
        pmid = "24110666";
        getCitationInfo(pmid);

        System.out
                .println(getPMID("", "A P300-based brain—computer interface"));
    }
}
