package org.neuinfo.resource.disambiguator.services;

import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.Publisher;
import org.neuinfo.resource.disambiguator.model.PublisherQueryLog;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.*;

/**
 * @author bozyurt
 */
public class NatureReferenceSearchService extends BaseReferenceSearchService {
    Publisher publisher;
    List<String> apiKeys;
    int count = 0;
    static final String serviceURL = "http://api.nature.com/content/opensearch/request";
    static Logger log = Logger.getLogger(NatureReferenceSearchService.class);

    public NatureReferenceSearchService() {
        super();
    }

    public void init(List<Registry> registryList) {
        this.registryList = registryList;
        initPublisher();
    }

    public void initPublisher() {
        publisher = super.getPublisher("Nature");
        assert publisher != null;
        apiKeys = getApiKeys(publisher);
    }

    public void handle(Calendar afterDate) throws Exception {
        Set<Registry> seenRegSet = null;
        // TEST 10 hours before till now
        // Date afterDate = new Date(System.currentTimeMillis() - 10 * 3600 *
        // 1000);
        //	Calendar afterDate = Calendar.getInstance();
        //	afterDate
        //			.setTimeInMillis(System.currentTimeMillis() - 50 * 3600 * 1000);
        seenRegSet = super.getAlreadyProcessedRegistries(afterDate, publisher);

        int maxAllowed = publisher.getNumConnectionsAllowed() * apiKeys.size() - 10;
        @SuppressWarnings("unused")
        int regCount = 0;
        for (Registry registry : registryList) {
            if (seenRegSet.contains(registry)) {
                continue;
            }
            handleRegistry(registry);
            regCount++;
            if (count >= maxAllowed) {
                log.warn("Maximum number of allowed requests are reached for Nature API.");
                break;
            }
        }
    }

    public boolean handleRegistry(Registry registry) throws Exception {
        List<QueryCandidate> candidates = prepSearchTermCandidates(registry);
        int numKeys = apiKeys.size();
        boolean found = false;
        for (QueryCandidate qc : candidates) {
            String candidate = qc.getCandidate();
            String apiKey = apiKeys.get((count % numKeys));
            URIBuilder builder = buildSearchURI(candidate, apiKey);

            log.info("query:" + builder.build());
            String result = super.getResult(builder);

            if (result == null || result.indexOf("Server Error") != -1
                    || result.indexOf("Internal server error") != -1) {
                super.insertQueryLog(candidate, registry, publisher);
                log.warn("Server error for query " + builder.build());
                continue;
            }
            int size = Math.min(500, result.length());
            log.info(result.substring(0, size));


            // Utils.saveText(result, new File("/tmp/nature.xml"));

            NatureHandler handler = new NatureHandler(candidate);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            InputSource is = new InputSource(new StringReader(result));
            try {
                saxParser.parse(is, handler);
            } catch (Exception x) {
                x.printStackTrace();
                log.warn("skipping candidate '" + candidate + "'");
                continue;
            }
            int total = handler.getTotal();
            PublisherQueryLog pql = super.insertQueryLog(candidate, registry,
                    publisher);
            // FIXME heuristic
            if (total > 0 && total <= 1000) {
                List<ArticleRec> articles = handler.getArticles();
                for (ArticleRec ar : articles) {
                    log.info(ar.getIdentifier());
                }

                saveArticles(articles, registry, pql, publisher,
                        qc.getType().equals(QueryCandidate.URL));
            }
            count++;
        }

        return found;
    }

    public static URIBuilder buildSearchURI(String candidate, String apiKey) throws URISyntaxException {
        URIBuilder builder = new URIBuilder(serviceURL);
        builder.setParameter("queryType", "cql");
        builder.setParameter("query", "\"" + candidate + "\"");
        builder.setParameter("maximumRecords", "1000");
        builder.setParameter("api_key", apiKey);
        return builder;
    }

    public static URIBuilder buildSearchURIOld(String candidate, String apiKey) throws URISyntaxException {
        URIBuilder builder = new URIBuilder(serviceURL);
        builder.setParameter("queryType", "cql");
        builder.setParameter("query", "cql.keywords==\"" + candidate + "\"");
        builder.setParameter("maximumRecords", "1000");
        builder.setParameter("api_key", apiKey);
        return builder;
    }

    public static class NatureHandler extends DefaultHandler {
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
        boolean invalid = false;
        String candidate;

        public NatureHandler(String candidate) {
            this.candidate = candidate;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            if (qName.equals("numberOfRecords")) {
                inTotal = true;
            } else if (qName.equals("pam:article")) {
                curArticle = new ArticleRec();
                invalid = false;
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
                String author = sb.toString().trim();
                if (author.indexOf(candidate) != -1) {
                    invalid = true;
                }
                curArticle.addAuthor(author);
            } else if (qName.equals("dc:description")) {
                inDesc = false;
                curArticle.description = Utils.stripHTML(sb.toString().trim());
            } else if (qName.equals("pam:article")) {
                if (!invalid && curArticle != null) {
                    articles.add(curArticle);
                }
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
            } else if (inTitle || inGenre || inPubDate || inPubName | inCreator || inDesc) {
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

    }
}
