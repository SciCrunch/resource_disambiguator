package org.neuinfo.resource.disambiguator.util;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import bnlpkit.util.FileUtils;
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
import org.neuinfo.resource.disambiguator.util.URLValidator.RedirectHistoryCaptureStrategy;

/**
 * 
 * @author bozyurt
 * 
 */
public class DescriptionExtractor {
	private HtmlSaxHandler handler;
    private int sc;
	public final static int CON_TIMEOUT = 15; // seconds

	public DescriptionExtractor(URL url, OpMode opMode) throws Exception {
		int timeout = CON_TIMEOUT; // secs
		BasicHttpParams params = new BasicHttpParams();
		params.setIntParameter(ClientPNames.MAX_REDIRECTS, 5);

		DefaultHttpClient client = new DefaultHttpClient(params);

		client.getParams().setIntParameter("http.connection.timeout",
				timeout * 1000);
		client.getParams().setIntParameter("http.socket.timeout",
				timeout * 1000);
		URIBuilder builder = new URIBuilder(url.toString());
		URI uri = builder.build();

		// keep a history of redirects
		RedirectHistoryCaptureStrategy strategy = new RedirectHistoryCaptureStrategy(
				uri);
		client.setRedirectStrategy(strategy);
		handler = new HtmlSaxHandler(opMode, url.toString());
		HttpGet httpGet = new HttpGet(uri);
		try {
			HttpResponse resp = client.execute(httpGet);
			sc = resp.getStatusLine().getStatusCode();
			if (sc == HttpStatus.SC_OK) {
				HttpEntity entity = resp.getEntity();
				if (entity != null) {
					ContentType contentType = ContentType.getOrDefault(entity);
					System.out.println("contentType:" + contentType.getMimeType());
					if (contentType.getMimeType().equals("text/html")) {
                       // Utils.save2File(entity.getContent(), "/tmp/test.html");
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
		} finally {
			if (httpGet != null) {
				httpGet.releaseConnection();
			}
		}
	}

    public int getStatusCode() {
        return sc;
    }

    public boolean isOK() {
        return sc == HttpStatus.SC_OK;
    }

	public String getDescription() {
		return handler.getDescription();
	}

	public String getTitle() {
		return handler.getTitle();
	}

	public String getAboutLink() {
		return handler.getAboutLink();
	}

	public String getContent() {
		return handler.getContent();
	}
}
