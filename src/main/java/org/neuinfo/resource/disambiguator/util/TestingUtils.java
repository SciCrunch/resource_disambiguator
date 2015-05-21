package org.neuinfo.resource.disambiguator.util;

import bnlpkit.util.GenUtils;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl;
import org.hibernate.*;
import org.neuinfo.resource.disambiguator.model.RegistrySiteContent;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;

/**
 * Created by bozyurt on 1/2/14.
 */
public class TestingUtils {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;

    public void downloadUrlsForTest(int maxCount) {
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory().openStatelessSession();

            Criteria criteria = session.createCriteria(RegistrySiteContent.class)
                    .setFetchMode("registry", FetchMode.JOIN);
            criteria.setReadOnly(true).setFetchSize(500).setCacheable(false);
            ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);

            File cacheDir = new File("/tmp/rd_cache");
            cacheDir.mkdir();
            int i = 0;
            while (results.next()) {
                RegistrySiteContent rsc = (RegistrySiteContent) results.get(0);
                String content = rsc.getContent();
                if (content.indexOf("margin-top") != -1 || content.indexOf("CDATA") != -1) {
                    String url = rsc.getRegistry().getUrl();
                    String filename = Utils.toFileName(url) + ".html";
                    File cacheFile = new File(cacheDir, filename);
                    loadPage(url, cacheFile);
                    i++;
                }
                if (i >= maxCount) {
                    break;
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public static void dumpContent(File cacheFile) throws Exception {
        HtmlSaxHandler handler = new HtmlSaxHandler(OpMode.FULL_CONTENT);

        SAXParserImpl.newInstance(null).parse(cacheFile, handler);

        String content = handler.getContent();

        System.out.println(GenUtils.formatText(content, 100));
    }

    public static void loadPage(String url, File cacheFile) throws Exception {
        int timeout = 15; // secs
        BasicHttpParams params = new BasicHttpParams();
        params.setIntParameter(ClientPNames.MAX_REDIRECTS, 5);

        DefaultHttpClient client = new DefaultHttpClient(params);

        client.getParams().setIntParameter("http.connection.timeout",
                timeout * 1000);
        client.getParams().setIntParameter("http.socket.timeout",
                timeout * 1000);

        URIBuilder builder = new URIBuilder(url);
        URI uri = builder.build();
        HttpGet httpGet = new HttpGet(uri);
        try {
            HttpResponse resp = client.execute(httpGet);
            int sc = resp.getStatusLine().getStatusCode();
            if (sc == HttpStatus.SC_OK) {
                HttpEntity entity = resp.getEntity();
                ContentType contentType = ContentType.getOrDefault(entity);
                if (!contentType.getMimeType().equals("text/html")) {
                    return;
                }
                BufferedInputStream in = null;
                BufferedOutputStream bout = null;
                byte[] buffer = new byte[4096];
                try {
                    in = new BufferedInputStream(entity.getContent(), 4096);
                    bout = new BufferedOutputStream(new FileOutputStream(cacheFile), 4096);
                    int readBytes;
                    while ((readBytes = in.read(buffer, 0, 4096)) != -1) {
                        bout.write(buffer, 0, readBytes);
                    }

                } finally {
                    Utils.close(in);
                    Utils.close(bout);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            httpGet.releaseConnection();
        }
    }

    public static void pingWebSite(String url) {
        URLValidator uv = new URLValidator(url, null);

        URLContent uc;
        try {
            uc = uv.checkValidity(true);
            if (uc != null) {
                System.out.println(uc);
            }
        } catch (Exception x) {
            x.printStackTrace();

        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            TestingUtils tu = new TestingUtils();
            injector.injectMembers(tu);

            // tu.downloadUrlsForTest(10);
            // File cacheFile = new File("/tmp/rd_cache/http_sccn_ucsd_edu_wiki_BCILAB.html");
            // File cacheFile = new File("/tmp/rd_cache/http_mouse_brain_map_org_.html");
            File cacheFile = new File("/tmp/rd_cache/http_www_medicine_uiowa_edu_pharmacology_.html");
           // TestingUtils.dumpContent(cacheFile);

            String url = "http://www.vph-noe.eu/";
            url = "http://blast.ncbi.nlm.nih.gov/Blast.cgi?PROGRAM=blastx&BLAST_PROGRAMS=blastx&PAGE_TYPE=BlastSearch&SHOW_DEFAULTS=on&LINK_LOC=blasthome";
            url = "http://toxnet.nlm.nih.gov/cgi-bin/sis/htmlgen?TRI";
            url = "http://onlinelibrary.wiley.com/journal/10.1002/%28ISSN%291096-9861/homepage/jcn_antibody_database.htm";
            url = "http://www.cvm.tamu.edu/resgrad/ssr/index.shtml";
            TestingUtils.pingWebSite(url);

        } finally {
            JPAInitializer.stopService();
        }
    }
}
