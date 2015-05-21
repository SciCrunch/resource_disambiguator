package org.neuinfo.resource.disambiguator.services;

import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.neuinfo.resource.disambiguator.model.CheckPoint;
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
public class SpringerReferenceSearchService extends BaseReferenceSearchService {
    Publisher publisher;
    List<String> apiKeys;
    int count = 0;
    static final String serviceURL = "http://api.springer.com/metadata/pam";
    static Logger log = Logger.getLogger(SpringerReferenceSearchService.class);

    public SpringerReferenceSearchService() {
        super();
    }

    public void init(List<Registry> registryList) {
        this.registryList = registryList;
        initPublisher();
    }

    public void initPublisher() {
        publisher = super.getPublisher("Springer");
        assert publisher != null;
        apiKeys = getApiKeys(publisher);
    }

    public void handle(Calendar afterDate) throws Exception {
        Set<Registry> seenRegSet;
        // TEST 10 hours before till now
        // Date afterDate = new Date(System.currentTimeMillis() - 10 * 3600 *
        // 1000);
      /*
        Calendar afterDate = Calendar.getInstance();
		afterDate
				.setTimeInMillis(System.currentTimeMillis() - 50 * 3600 * 1000);
		*/


        String batchId = Utils.prepBatchId();
        super.addCheckPoint(batchId, CheckPoint.STATUS_START);

        // seenRegSet = new HashSet<Registry>();
        seenRegSet = super.getAlreadyProcessedRegistries(afterDate, publisher);


        int maxAllowed = publisher.getNumConnectionsAllowed() * apiKeys.size() - 10;
        for (Registry registry : registryList) {
            if (seenRegSet.contains(registry)) {
                continue;
            }
            handleRegistry(registry);
            if (count >= maxAllowed) {
                log.warn("Maximum number of allowed requests are reached for Springer API.");
                break;
            }
        }
        log.info("finished.");
        super.addCheckPoint(batchId, CheckPoint.STATUS_END);
    }

    public void handleRegistry(Registry registry) throws Exception {
        List<QueryCandidate> candidates = prepSearchTermCandidates(registry);
        int numKeys = apiKeys.size();
        for (QueryCandidate qc : candidates) {
            String candidate = qc.getCandidate();
            String apiKey = apiKeys.get((count % numKeys));

            URIBuilder builder = buildNextPageURI(candidate, apiKey, -1);

            SpringerHandler handler = getNextBatch(builder, candidate, registry);
            if (handler == null) {
                continue;
            }
            int total = handler.getTotal();

            PublisherQueryLog pql = super.insertQueryLog(candidate, registry,
                    publisher);
            if (total > 0 && total <= 1000) {
                List<ArticleRec> articles = handler.getArticles();
                saveArticles(articles, registry, pql, publisher, qc.getType().equals(QueryCandidate.URL));
                int start = 101;
                while (total > start) {
                    count++;
                    apiKey = apiKeys.get((count % numKeys));
                    builder = buildNextPageURI(candidate, apiKey, start);
                    handler = getNextBatch(builder, candidate, registry);
                    if (handler != null) {
                        articles = handler.getArticles();
                        saveArticles(articles, registry, pql, publisher, qc.getType().equals(QueryCandidate.URL));
                    } else {
                        break;
                    }
                    start += 100;
                }
            }
            count++;
        }
    }

    public static URIBuilder buildNextPageURI(String candidate,
                                              String apiKey, int start) throws URISyntaxException {
        URIBuilder builder = new URIBuilder(serviceURL);
        builder.setParameter("q", candidate);
        builder.setParameter("p", "100");
        builder.setParameter("api_key", apiKey);
        if (start > 0) {
            builder.setParameter("s", String.valueOf(start));
        }
        return builder;
    }

    String getNextApiKey() {
        return apiKeys.get((count % apiKeys.size()));
    }

    SpringerHandler getNextBatch(URIBuilder builder, String candidate, Registry registry) {
        try {
            System.out.println("query:" + builder.build());
            String result = getResult(builder);
            if (result == null || result.indexOf("Server Error") != -1
                    || result.indexOf("Internal server error") != -1) {
                super.insertQueryLog(candidate, registry, publisher);
                log.warn("Server error for query " + builder.build());
                return null;
            } else if (result.indexOf("Developer Inactive") != -1) {
                this.count++;
                String apiKey = getNextApiKey();
                builder.setParameter("api_key", apiKey);
                result = getResult(builder);
                if (result == null || result.indexOf("Server Error") != -1
                        || result.indexOf("Internal server error") != -1) {
                    // give up
                    super.insertQueryLog(candidate, registry, publisher);
                    log.warn("Server error for query " + builder.build());
                    return null;
                }
            }

            int size = Math.min(500, result.length());
            log.info(result.substring(0, size));

            SpringerHandler handler = new SpringerHandler(candidate);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            InputSource is = new InputSource(new StringReader(result));
            saxParser.parse(is, handler);

            return handler;
        } catch (Exception x) {
            log.warn("getNextBatch", x);
            return null;
        }
    }

    public static class SpringerHandler extends DefaultHandler {
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
        StringBuilder descBuf = new StringBuilder(256);
        StringBuilder sb = new StringBuilder(300);
        List<ArticleRec> articles = new ArrayList<ArticleRec>();
        ArticleRec curArticle = null;
        boolean invalid = false;
        String candidate;

        public SpringerHandler(String candidate) {
            this.candidate = candidate;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            if (qName.equals("total")) {
                inTotal = true;
                //} else if (qName.equals("pam:article")) {
            } else if (qName.equals("pam:message")) {
                curArticle = new ArticleRec();
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
                //  } else if (qName.equals("dc:description")) {
                // inDesc = true;
            } else if (qName.equals("xhtml:body")) {
                inDesc = true;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (qName.equals("total")) {
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
                // query term matches in author names are probably bad
                if (author.indexOf(candidate) != -1) {
                    invalid = true;
                }
                curArticle.addAuthor(author);
            } else if (qName.equals("xhtml:body")) {
                inDesc = false;
                curArticle.description = Utils.filterNonUTF8(Utils.stripHTML(descBuf.toString().trim()));
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
            } else if (inTitle || inGenre || inPubDate || inPubName | inCreator) {
                sb.append(ch, start, length);
            } else if (inDesc) {
                descBuf.append(ch, start, length).append(' ');
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
