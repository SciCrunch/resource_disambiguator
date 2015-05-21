package org.neuinfo.resource.disambiguator.services;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.util.FileUtils;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.StringReader;
import java.util.*;

/**
 * Created by bozyurt on 3/4/14.
 */
public class NatureXmlHandler extends DefaultHandler {

    private boolean inTotal = false;
    private boolean inIndentifier = false;
    private boolean inPubName = false;
    private boolean inTitle = false;
    private boolean inPubDate = false;
    private boolean inGenre = false;
    private boolean inDesc = false;
    private boolean inCreator = false;
    StringBuilder totalBuf = new StringBuilder(20);
    StringBuilder identifierBuf = new StringBuilder();
    StringBuilder sb = new StringBuilder(300);
    List<ArticleRec> articles = new ArrayList<ArticleRec>();
    ArticleRec curArticle = null;
    boolean verbose = false;

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }


    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        if (qName.equals("numberOfRecords")) {
            inTotal = true;
        } else if (qName.equals("pam:article")) {
            curArticle = new ArticleRec();
            articles.add(curArticle);
        } else if (qName.equals("dc:identifier")) {
            inIndentifier = true;
        } else if (qName.equals("dc:title")) {
            inTitle = true;
        } else if (qName.equals("prism:publicationName")) {
            inPubName = true;
        } else if (qName.equals("prism:publicationDate")) {
            inPubDate = true;
        } else if (qName.equals("prism:genre")) {
            inGenre = true;
        } else if (qName.equals("dc:creator")) {
            inCreator = true;
        } else if (qName.equals("dc:description")) {
            inDesc = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        System.out.println("qName:" + qName);
        if (qName.equals("numberOfRecords")) {
            inTotal = false;
        } else if (qName.equals("dc:identifier")) {
            inIndentifier = false;
            curArticle.identifier = identifierBuf.toString().trim();
            identifierBuf.setLength(0);
        } else if (qName.equals("dc:title")) {
            inTitle = false;
            curArticle.title = sb.toString().trim();
        } else if (qName.equals("prism:publicationName")) {
            inPubName = false;
            curArticle.publicationName = sb.toString().trim();
        } else if (qName.equals("prism:publicationDate")) {
            inPubDate = false;
            curArticle.publicationDate = sb.toString().trim();
        } else if (qName.equals("prism:genre")) {
            inGenre = false;
            curArticle.genre = sb.toString().trim();
        } else if (qName.equals("dc:creator")) {
            inCreator = false;
            curArticle.addAuthor(sb.toString().trim());
        } else if (qName.equals("dc:description")) {
            inDesc = false;
            curArticle.description = Utils.stripHTML(sb.toString().trim());
        }
        sb.setLength(0);
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (inTotal) {
            totalBuf.append(ch, start, length);
        } else if (inIndentifier) {
            identifierBuf.append(ch, start, length);
        } else if (inTitle || inGenre || inPubDate || inPubName || inCreator || inDesc) {
            sb.append(ch, start, length);
        }
    }

    public int getTotal() {
        String ts = totalBuf.toString().trim();
        if (ts.length() == 0) {
            return -1;
        }
        return Integer.parseInt(ts);
    }

    public List<ArticleRec> getArticles() {
        Set<String> uniqSet = new HashSet<String>();
        for (Iterator<ArticleRec> iter = articles.iterator(); iter
                .hasNext(); ) {
            ArticleRec ar = iter.next();
            if (!uniqSet.contains(ar.identifier)) {
                uniqSet.add(ar.identifier);
            } else {
                iter.remove();
            }
        }
        return articles;
    }

    public static void main(String[] args) throws Exception {
        File f = new File("/tmp/nature_result_wf.xml");
        NatureXmlHandler handler = new NatureXmlHandler();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        InputSource is = new InputSource(FileUtils.getBufferedReader(f.getAbsolutePath(), CharSetEncoding.UTF8));
        saxParser.parse(is, handler);

        System.out.println("total:" + handler.getTotal());
        List<ArticleRec> articles = handler.getArticles();
        for (ArticleRec ar : articles) {
            System.out.println(ar);
        }

    }

}