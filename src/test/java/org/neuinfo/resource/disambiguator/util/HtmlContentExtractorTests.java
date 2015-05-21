package org.neuinfo.resource.disambiguator.util;

import junit.framework.TestSuite;
import org.neuinfo.resource.disambiguator.BaseTestCase;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bozyurt on 3/25/14.
 */
public class HtmlContentExtractorTests extends BaseTestCase {
    List<String> registryUrls = new ArrayList<String>();

    public HtmlContentExtractorTests(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Query query = em.createQuery("select r.url from RegistrySiteContent s inner join s.registry r where s.flags = 1 and length(s.content) < 30");
            List<?> resultList = query.getResultList();
            for (Object o : resultList) {
                registryUrls.add((String) o);
            }

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public void testFindFrameContent() throws Exception {
        URL url = new URL("http://dbtss.hgc.jp/");
        url = new URL("http://volume-one.org");
        url = new URL("http://www.hdsa-ne.org/");
        HtmlContentExtractor hce = new HtmlContentExtractor(url);

        HtmlHandler2 handler = hce.extractContentViaTagSoup(url);
        // Map<HtmlContentExtractor.FrameInfo, HtmlContentExtractor.HtmlHandler> fi2HandlerMap = new HashMap<HtmlContentExtractor.FrameInfo, HtmlContentExtractor.HtmlHandler>(7);
        if (handler != null && !handler.getFrames().isEmpty()) {
            StringBuilder sb = new StringBuilder(4096);
            String title = null;
            for (HtmlContentExtractor.FrameInfo fi : handler.getFrames()) {
                String frameUrl = Utils.prepFrameAbsUrl(fi.getSrcUrl(), url);
                frameUrl = frameUrl.replaceAll(" ", "%20");
                URL fu = new URL(frameUrl);
                hce = new HtmlContentExtractor(fu);
                handler = hce.extractContentViaTagSoup(fu);
                if (handler != null) {
                    if (handler.getTitle() != null) {
                        title = handler.getTitle();
                    }
                    String content = handler.getContent();
                    if (content != null) {
                        content = content.trim();
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(content);
                    }
                }
            }
            System.out.println("Content:" + sb.toString());
            System.out.println("title:" + title);
        }
    }


    public void testHtmlContentExtractorWithFrames() throws Exception {
        URL url = new URL("http://www.mapuproteome.com/");
        url = new URL("http://nba.uth.tmc.edu/snnap/");
        url = new URL("http://www.empix.com");

        Set<String> frameNameSet = new HashSet<String>();
        int frameCount = 0;
        int total = this.registryUrls.size();
        for (String urlStr : this.registryUrls) {
            url = new URL(urlStr);
            HtmlContentExtractor hce = new HtmlContentExtractor(url);

            HtmlHandler2 handler = hce.extractContentViaTagSoup(url);

            if (handler != null && !handler.getFrames().isEmpty()) {
                System.out.println(url);
                for (HtmlContentExtractor.FrameInfo fi : handler.getFrames()) {
                    System.out.println(fi);
                    if (fi.getName() != null) {
                        frameNameSet.add(fi.getName());
                    }
                }
                System.out.println("======================================");
                hce.extractContent();
                System.out.println(hce.getContent());
                frameCount++;
            } else {
                //System.out.println(handler.getContent());
            }
        }

        System.out.println("Num of frameset registry resources:" + frameCount + " out of " + total);
        System.out.println("frameNameSet:" + frameNameSet);
    }


    public void testHtmlHandler2WithContentSections() throws Exception {
        URL url = new URL("http://datf.cbi.pku.edu.cn/");
        HtmlContentExtractor hce = new HtmlContentExtractor(url);

        HtmlHandler2 handler = hce.extractContentViaTagSoup(url);

        String content = handler.getContent();
        List<HtmlHandler2.ContentSection> sectionList = handler.getSectionList();
        System.out.println("Sections\n----------------");
        for(HtmlHandler2.ContentSection cs : sectionList) {
            System.out.println(cs);
        }


    }

    public static TestSuite suite() {
        TestSuite suite = new TestSuite();
      //   suite.addTest(new HtmlContentExtractorTests("testHtmlContentExtractorWithFrames"));
        suite.addTest(new HtmlContentExtractorTests("testHtmlHandler2WithContentSections"));
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
