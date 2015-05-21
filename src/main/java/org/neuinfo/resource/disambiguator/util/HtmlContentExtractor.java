package org.neuinfo.resource.disambiguator.util;

import org.apache.http.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Whitelist;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 3/18/14.
 */
public class HtmlContentExtractor {
    private String content;
    private String title;
    private URL url;
    private int sc;
    public final static int CON_TIMEOUT = 15; // seconds

    public HtmlContentExtractor(URL url) {
        this.url = url;
    }

    public void extractContent() {
        extractContentViaTagSoupWithFrames();
        if (content == null) {
            // try with Jsoup also
            extractContentViaJSoup();
        }
    }


    public void extractContentViaJSoup() {
        Document doc;
        try {
            doc = Jsoup.connect(url.toString()).get();

            doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
            doc.select("br").append("\\n");
            doc.select("p").prepend("\\n\\n");
            this.title = doc.title();
            String s = doc.html().replaceAll("\\\\n", "\n");
            this.content = Jsoup.clean(s, "", Whitelist.none(),
                    new Document.OutputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml));

            content = content.replaceAll("\\n\\s*\\n+", "\n");
            content = content.replaceAll("&gt;", ">");
            content = content.replaceAll("&lt;", "<");
            content = content.replaceAll("&apos;", "'");
            content = content.replaceAll("&quot;", "\"");
            content = content.replaceAll("&amp;", "&");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void extractContentViaTagSoupWithFrames() {
        HtmlHandler2 handler = extractContentViaTagSoup(this.url);
        if (handler != null) {
            if (handler.getFrames().isEmpty()) {
                this.title = handler.getTitle();
                this.content = handler.getContent();
            } else {
                StringBuilder sb = new StringBuilder(4096);
                for (HtmlContentExtractor.FrameInfo fi : handler.getFrames()) {
                    String frameUrl = Utils.prepFrameAbsUrl(fi.getSrcUrl(), this.url);
                    frameUrl = frameUrl.replaceAll(" ", "%20");
                    try {
                        handler = extractContentViaTagSoup(new URL(frameUrl));
                        if (handler != null) {
                            if (handler.getTitle() != null) {
                                this.title = handler.getTitle();
                            }
                            String contentStr = handler.getContent();
                            if (contentStr != null) {
                                contentStr = contentStr.trim();
                                if (sb.length() > 0) {
                                    sb.append(' ');
                                }
                                sb.append(contentStr);
                            }
                        }
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
                this.content = sb.toString().trim();
            }
        }
    }

    public HtmlHandler2 extractContentViaTagSoup(URL anURL) {
        HtmlHandler2 handler = null;
        HttpGet httpGet = null;
        int timeout = CON_TIMEOUT; // secs
        BasicHttpParams params = new BasicHttpParams();
        params.setIntParameter(ClientPNames.MAX_REDIRECTS, 5);

        DefaultHttpClient client = new DefaultHttpClient(params);

        client.getParams().setIntParameter("http.connection.timeout",
                timeout * 1000);
        client.getParams().setIntParameter("http.socket.timeout",
                timeout * 1000);

        try {
            URIBuilder builder = new URIBuilder(anURL.toString());
            URI uri = builder.build();

            // keep a history of redirects
            RedirectHistoryCaptureStrategy strategy = new RedirectHistoryCaptureStrategy(
                    uri);
            client.setRedirectStrategy(strategy);
            httpGet = new HttpGet(uri);
            HttpResponse resp = client.execute(httpGet);
            sc = resp.getStatusLine().getStatusCode();
            if (sc == HttpStatus.SC_OK) {
                HttpEntity entity = resp.getEntity();
                if (entity != null) {
                    ContentType contentType = ContentType.getOrDefault(entity);
                    // System.out.println("contentType:" + contentType.getMimeType());
                    if (contentType.getMimeType().equals("text/html")) {
                        // Utils.save2File(entity.getContent(), "/tmp/test.html");
                        handler = new HtmlHandler2(url.toString());
                        InputStream in = null;
                        try {
                            in = entity.getContent();
                            SAXParserImpl.newInstance(null).parse(in, handler);
                        } finally {
                            Utils.close(in);
                        }
                    } else {
                        System.out.println("DescriptionExtractor:: Unsupported MIME type:" + contentType.getMimeType());
                    }
                }
            }
        } catch (Exception x) {
            x.printStackTrace();
        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
        return handler;
    }

    public String getContent() {
        return content;
    }

    public String getTitle() {
        return title;
    }

    public URL getUrl() {
        return url;
    }


    public static class FrameInfo {
        final String srcUrl;
        final String name;

        public FrameInfo(String srcUrl, String name) {
            this.srcUrl = srcUrl;
            this.name = name;
        }

        public String getSrcUrl() {
            return srcUrl;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("FrameInfo{");
            sb.append("srcUrl='").append(srcUrl).append('\'');
            sb.append(", name='").append(name).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    public static class HtmlHandler extends DefaultHandler {
        private boolean inFrameSet = false;
        private boolean inP = false;
        private boolean inTitle = false;
        private boolean inScript = false;
        private boolean inAnchor = false;
        private boolean inStyle = false;
        private StringBuilder sb = new StringBuilder(4096);
        private StringBuilder descriptionBuf = new StringBuilder(4096);
        private StringBuilder titleBuf = new StringBuilder(256);
        private StringBuilder totBuf = new StringBuilder(4096);
        private String curHref;
        private StringBuilder anchorBuf = new StringBuilder(200);
        private List<FrameInfo> frames = new ArrayList<FrameInfo>(4);

        private String aboutLink;
        private String pageURL;
        private String redirectUrl;
        private final Pattern redirectRegex = Pattern.compile("URL=(.+)", Pattern.CASE_INSENSITIVE);

        public HtmlHandler(String pageURL) {
            this.pageURL = pageURL;
        }


        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            if (localName.equalsIgnoreCase("p")) {
                inP = true;
            } else if (localName.equalsIgnoreCase("title")) {
                inTitle = true;
            } else if (localName.equalsIgnoreCase("script")) {
                inScript = true;
            } else if (localName.equalsIgnoreCase("a")) {
                inAnchor = true;
                curHref = attributes.getValue("href");
            } else if (localName.equalsIgnoreCase("style")) {
                inStyle = true;
            } else if (localName.equalsIgnoreCase("frameset")) {
                inFrameSet = true;
            } else if (localName.equalsIgnoreCase("frame")) {
                String frameSrc = attributes.getValue("src");
                String frameName = attributes.getValue("name");
                if (frameSrc != null) {
                    FrameInfo fi = new FrameInfo(frameSrc, frameName);
                    this.frames.add(fi);
                }
            } else if (localName.equalsIgnoreCase("meta")) {
                String value = attributes.getValue("http-equiv");
                if (value != null && value.equalsIgnoreCase("refresh")) {
                    String contentValue = attributes.getValue("content");
                    if (contentValue != null) {
                        Matcher matcher = redirectRegex.matcher(contentValue);
                        if (matcher.find()) {
                            this.redirectUrl = matcher.group(1);
                        }
                    }
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (localName.equalsIgnoreCase("title")) {
                inTitle = false;
            } else if (localName.equalsIgnoreCase("script")) {
                inScript = false;
            } else if (localName.equalsIgnoreCase("a")) {
                if (aboutLink == null && curHref != null && pageURL != null
                        && !curHref.equalsIgnoreCase(pageURL)) {
                    String anchorText = anchorBuf.toString();
                    if (anchorText.toLowerCase().indexOf("about") != -1
                            || curHref.toLowerCase().indexOf("about") != -1) {
                        if (!isBadURL(curHref)) {
                            if (!curHref.startsWith("http")) {
                                String s;
                                if (curHref.startsWith("/")) {
                                    s = curHref.replaceFirst("^/+", "");
                                } else {
                                    s = curHref;
                                }
                                this.aboutLink = pageURL + "/" + s;
                            } else {
                                this.aboutLink = curHref;
                            }
                        }
                    }
                }
                inAnchor = false;
                anchorBuf.setLength(0);
            } else if (localName.equalsIgnoreCase("style")) {
                inStyle = false;
            }
        }

        public static boolean isBadURL(String url) {
            url = url.toLowerCase();
            return ((url.indexOf(".pdf") != -1) || (url.indexOf(".jpg") != -1)
                    || (url.indexOf(".gif") != -1) || (url.indexOf(".bmp") != -1)
                    || (url.indexOf(".ppt") != -1) || (url.indexOf(".doc") != -1));
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            // System.out.println(new String(ch));
            if (!inScript && !inStyle) {
                totBuf.append(ch, start, length).append(' ');
            }
            if (inP) {
                sb.append(ch, start, length).append(' ');
            } else if (inTitle) {
                titleBuf.append(ch, start, length).append(' ');
            } else if (inAnchor) {
                anchorBuf.append(ch, start, length);
            }
        }

        public String getDescription() {
            String d = descriptionBuf.toString().trim();
            if (d.length() > 0) {
                d = d.replaceAll("\\s+", " ");
            }
            return d;
        }

        public String getTitle() {
            return titleBuf.toString().trim();
        }

        public String getContent() {
            String s = totBuf.toString().trim();
            s = Utils.normalizeWS(s);
            s = s.replaceAll("(?:\\s*\\n)+", "\n");
            return s;
        }

        public String getAboutLink() {
            return aboutLink;
        }


        public String getRedirectUrl() {
            return redirectUrl;
        }

        public List<FrameInfo> getFrames() {
            return frames;
        }
    } //; HtmlHandler

    public static class RedirectHistoryCaptureStrategy extends
            DefaultRedirectStrategy {
        final Deque<URI> redirectHistory = new LinkedList<URI>();

        public RedirectHistoryCaptureStrategy(URI uri) {
            redirectHistory.push(uri);
        }

        @Override
        public HttpUriRequest getRedirect(HttpRequest req, HttpResponse resp,
                                          HttpContext ctx) throws ProtocolException {
            HttpUriRequest redirect = super.getRedirect(req, resp, ctx);
            redirectHistory.push(redirect.getURI());
            return redirect;
        }
    }

}
