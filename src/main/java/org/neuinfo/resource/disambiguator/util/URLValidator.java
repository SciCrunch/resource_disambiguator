package org.neuinfo.resource.disambiguator.util;

import org.apache.http.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.log4j.Logger;
import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl;

import java.io.InputStream;
import java.net.URI;
import java.util.Deque;
import java.util.LinkedList;

/**
 * Validates (checks accessibility) of a provided URL and extracts title and
 * description info if its valid
 *
 * @author bozyurt
 */
public class URLValidator {
    private String url;
    private OpMode opMode = OpMode.DESCR;
    static Logger log = Logger.getLogger(URLValidator.class);

    public URLValidator(String url, OpMode opMode) {
        this.url = url;
        this.opMode = opMode;
    }

    /**
     * validates (checks accessibility) of a provided URL and extracts title and
     * description info if its valid. It follows URL redirects up to five level
     * before giving up.
     *
     * @param validateOnly if true does not try to get/parse the web page contents, just
     *                     checks if the url is accessible
     * @return {@link URLContent} if url is valid otherwise returns null
     * @throws Exception
     */
    public URLContent checkValidity(boolean validateOnly) throws Exception {
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

        // keep a history of redirects
        RedirectHistoryCaptureStrategy strategy = new RedirectHistoryCaptureStrategy(
                uri);
        client.setRedirectStrategy(strategy);
        if (validateOnly) {
            return checkURL(client, uri, strategy);
        }

        HttpGet httpGet = new HttpGet(uri);
        try {
            httpGet.setHeader("User-Agent", "NIF Resource Disambiguator");
            HttpResponse resp = client.execute(httpGet);
            int sc = resp.getStatusLine().getStatusCode();
            if (sc == HttpStatus.SC_OK) {
                HttpEntity entity = resp.getEntity();
                if (entity != null) {
                    URI finalRedirectURI = null;
                    if (strategy.redirectHistory.size() > 1) {
                        finalRedirectURI = strategy.redirectHistory.getLast();
                    }
                    ContentType contentType = ContentType.getOrDefault(entity);
                    log.info(contentType + ", " + contentType);
                    if (!contentType.getMimeType().equals("text/html")) {
                        return null;
                    }
                    InputStream in = null;
                    try {
                        in = entity.getContent();

                        HtmlSaxHandler handler = new HtmlSaxHandler(opMode);
                        SAXParserImpl.newInstance(null).parse(in, handler);
                        String content = null;
                        if (opMode == OpMode.FULL_CONTENT) {
                            content = handler.getContent();
                        }

                        if (finalRedirectURI == null && handler.getRedirectUrl() != null) {
                            finalRedirectURI = new URI(handler.getRedirectUrl());
                        }

                        return new URLContent(url, finalRedirectURI,
                                handler.getTitle(), handler.getDescription(),
                                content);
                    } finally {
                        Utils.close(in);
                    }
                }
            }
        } catch (Throwable t) {
            log.warn(t.getMessage());
            return null;
        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
        return null;
    }

    private URLContent checkURL(DefaultHttpClient client, URI uri,
                                RedirectHistoryCaptureStrategy strategy) throws Exception {
        HttpHead head = new HttpHead(uri);
        head.setHeader("Accept", "*/*");
        head.setHeader("User-Agent", "NIF Resource Disambiguator");
        /*
        Header[] headers = head.getAllHeaders();
        for (Header h : headers) {
            System.out.println("\t" + h.getName() + ": " + h.getValue());
        }
        */
        try {

            HttpResponse resp = client.execute(head);
            int sc = resp.getStatusLine().getStatusCode();
            if (sc == HttpStatus.SC_OK) {
                HttpEntity entity = resp.getEntity();
                if (entity != null) {
                    URI finalRedirectURI = null;
                    if (strategy.redirectHistory.size() > 1) {
                        finalRedirectURI = strategy.redirectHistory.getLast();
                    }
                    return new URLContent(url, finalRedirectURI, null, null,
                            null);
                } else {
                    return new URLContent(url, null, null, null, null);
                }
            } else {
                return getURL(client, uri, strategy);

            }
        } finally {
            if (head != null) {
                head.releaseConnection();
            }
        }
    }

    private URLContent getURL(DefaultHttpClient client, URI uri,
                              RedirectHistoryCaptureStrategy strategy) throws Exception {
        HttpGet get = new HttpGet(uri);
        get.setHeader("Accept", "*/*");
        get.setHeader("User-Agent", "NIF Resource Disambiguator");
        try {

            HttpResponse resp = client.execute(get);
            int sc = resp.getStatusLine().getStatusCode();
            if (sc == HttpStatus.SC_OK) {
                HttpEntity entity = resp.getEntity();
                if (entity != null) {
                    URI finalRedirectURI = null;
                    if (strategy.redirectHistory.size() > 1) {
                        finalRedirectURI = strategy.redirectHistory.getLast();
                    }
                    return new URLContent(url, finalRedirectURI, null, null,
                            null);
                } else {
                    return new URLContent(url, null, null, null, null);
                }
            }
        } finally {
            if (get != null) {
                get.releaseConnection();
            }
        }
        return null;
    }

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
