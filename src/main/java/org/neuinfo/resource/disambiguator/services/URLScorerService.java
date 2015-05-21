package org.neuinfo.resource.disambiguator.services;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.util.*;

/**
 * 05GetURLScore_categories.pl
 * <p/>
 * Scores filtered URLs vi NIF annotation service
 *
 * @author bozyurt
 */
public class URLScorerService {
    private List<String> categories;
    private File downloadRootDir;
    private File invalidRootDir;
    private boolean cachePages = false;
    static Logger log = Logger.getLogger(URLScorerService.class);

    private final static Pattern spanPattern = Pattern
            .compile("<span.+?data-nif=\"([^\"]+)");

    public URLScorerService(List<String> categories, String batchId)
            throws Exception {
        this.categories = categories;
        Properties props = Utils
                .loadProperties("resource_disambiguator.properties");
        downloadRootDir = new File(props.getProperty("download.rootdir"));
        if (!downloadRootDir.isDirectory()) {
            if (!downloadRootDir.mkdirs()) {
                throw new Exception("Cannot create directory:"
                        + downloadRootDir);
            }
        }
        downloadRootDir = new File(downloadRootDir, batchId);
        if (!downloadRootDir.isDirectory() && !downloadRootDir.mkdirs()) {
            throw new Exception("Cannot create directory:"
                    + downloadRootDir);
        }
        invalidRootDir = new File(props.getProperty("invalid.rootdir"));
        if (!invalidRootDir.isDirectory()) {
            if (!invalidRootDir.mkdirs()) {
                throw new Exception("Cannot create directory:" + invalidRootDir);
            }
        }
    }

    public List<URLRec> scoreURLs(List<URLRec> urls) throws Exception {
        int count = 0;
        for (URLRec urlRec : urls) {
            URLValidator validator = new URLValidator(urlRec.getUrl(),
                    OpMode.FULL_CONTENT);

            URLContent urlContent = validator.checkValidity(false);
            int score = 0;
            for (String category : categories) {
                score += getScore(urlContent.getContent(), category);
            }
            urlRec.setScore((double) score);
            ++count;

        }
        log.info("processed " + count + " number of urls.");
        return urls;
    }

    public URLRecWrapper scoreURL(URLRec ur) throws Exception {
        String urlStr = ur.getUrl().trim();
        URLValidator validator = new URLValidator(urlStr,
                OpMode.FULL_CONTENT);

        URLContent urlContent = validator.checkValidity(false);
        int score = 0;
        URI redirectURI = null;
        if (urlContent != null) {
            // check if there is url redirection

            if (RedirectUtils.isAValidRedirect(urlContent.getFinalRedirectURI(), urlStr)) {
                URL redirectURL = RedirectUtils.normalizeRedirectURL(
                        urlContent.getFinalRedirectURI().toString(), urlStr);
                if (RedirectUtils.isRedirectUrlValid(redirectURL.toString()))  {
                    urlContent = validator.checkValidity(false);
                    assert urlContent != null;
                    redirectURI = redirectURL.toURI();
                }
            }

            log.debug(urlContent);
            for (String category : categories) {
                score += getScore(urlContent.getContent(), category);
            }
            ur.setScore((double) score);

            if (cachePages) {
                String filename = Utils.getMD5ChecksumOfString(ur.getUrl())
                        + ".xml";
                File xmlFile = new File(downloadRootDir, filename);
                urlContent.toXml(xmlFile);
                log.info("cached to " + xmlFile);
            }
        } else {
            ur.setScore((double) -1);
        }
        return new URLRecWrapper(ur, redirectURI);
    }

    public int getScore(String content, String category) throws Exception {
        int score = 0;
        AnnotationServiceClient client = new AnnotationServiceClient();
        Map<String, String> paramsMap = new HashMap<String, String>(7);
        paramsMap.put("longestOnly", "true");
        paramsMap.put("includeCat", category);

        String annotContent = client.annotate(content, paramsMap);
        Matcher matcher = spanPattern.matcher(annotContent);
        while (matcher.find()) {
            String mc = matcher.group(1).toLowerCase();
            if (mc.indexOf(category) != -1) {
                score++;
            }
        }
        return score;
    }

    public boolean isCachePages() {
        return cachePages;
    }

    public void setCachePages(boolean cachePages) {
        this.cachePages = cachePages;
    }

    public static class URLRecWrapper {
        final URLRec urlRec;
        final URI finalRedirectURI;

        public URLRecWrapper(URLRec urlRec, URI finalRedirectURI) {
            this.urlRec = urlRec;
            this.finalRedirectURI = finalRedirectURI;
        }

        public URLRec getUrlRec() {
            return urlRec;
        }

        public URI getFinalRedirectURI() {
            return finalRedirectURI;
        }
    }
}
