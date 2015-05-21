package org.neuinfo.resource.disambiguator.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 *
 * Created by bozyurt on 1/7/14.
 */
public class RedirectUtils {

    public static boolean isAValidRedirect(URI redirectURI, String origUrl) {
        if (redirectURI == null) {
            return false;
        }
        try {
            URI origURI = new URI(origUrl);
            return !origURI.equals(redirectURI);
        } catch (URISyntaxException e) {
            /* ignored */
        }
        return false;
    }
    public static URL normalizeRedirectURL(String origUrl, String redirectUrl) throws MalformedURLException {
        if (redirectUrl.startsWith(".") || redirectUrl.startsWith("/")) {
            try {
                URL url = new URL(origUrl);
                if (redirectUrl.startsWith(".")) {
                    redirectUrl = redirectUrl.substring(1);
                }

                return new URL(url.getProtocol(), url.getHost(), url.getPort(), redirectUrl);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        } else if (!Utils.isValidURLFormat(redirectUrl)) {
            try {
                URL url = new URL(origUrl);
                if (!url.getPath().isEmpty()) {
                    String path = url.getPath();
                    int idx = path.lastIndexOf("/");
                    if (idx >= 0 && (idx < (path.length() - 1))) {
                        path = path.substring(0, idx+1);
                        return new URL(url.getProtocol() , url.getHost(), url.getPort(), path + redirectUrl);
                    }
                }
                if (!origUrl.endsWith("/")) {
                    origUrl = origUrl + "/";
                }
                URL ru = new URL(origUrl + redirectUrl);
                return ru;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        return new URL(redirectUrl);
    }

    public static boolean isRedirectUrlValid(String redirectUrl) {
        URLValidator validator = new URLValidator(redirectUrl, OpMode.FULL_CONTENT);
        try {
            URLContent urlContent = validator.checkValidity(true);
            return urlContent != null;
        } catch (Exception e) {
           /* ignore */
        }
        return false;
    }
}
