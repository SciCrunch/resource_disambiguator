package org.neuinfo.resource.disambiguator.services;

import bnlpkit.util.GenUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.neuinfo.resource.disambiguator.model.PaperReference;
import org.neuinfo.resource.disambiguator.model.Publisher;
import org.neuinfo.resource.disambiguator.model.PublisherQueryLog;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.util.Assertion;
import org.neuinfo.resource.disambiguator.util.Utils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.persistence.EntityManager;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.*;

/**
 * @author bozyurt
 */
public class NeuinfoReferenceSearchService extends BaseReferenceSearchService {
    Publisher publisher;
    List<String> apiKeys;
    // static final String serviceURL = "http://nif-services.neuinfo.org/servicesv1/v1/literature/search";
    static final String serviceURL = "http://beta.neuinfo.org/services/v1/literature/search";
    static Logger log = Logger.getLogger(NeuinfoReferenceSearchService.class);
    /**
     * delay between web service calls in millisecs (default no delay)
     */
    long delay = -1;

    public NeuinfoReferenceSearchService() {
        super();
    }

    public void init(List<Registry> registryList) {
        this.registryList = registryList;
        initPublisher();
    }

    public void initPublisher() {
        publisher = super.getPublisher("neuinfo.org");
        Assertion.assertNotNull(publisher);
        apiKeys = getApiKeys(publisher);
    }

    public void handle(Calendar afterDate) throws Exception {
        Set<Registry> seenRegSet;

/*
Calendar afterDate = Calendar.getInstance();
afterDate
.setTimeInMillis(System.currentTimeMillis() - 50 * 3600 * 1000);
*/
        seenRegSet = super.getAlreadyProcessedRegistries(afterDate, publisher);

        for (Registry registry : registryList) {
            log.info("handling registry " + registry.getNifId() + " ["
                    + registry.getResourceName() + "]");
            if (seenRegSet.contains(registry)) {
                log.info("already handled registry " + registry.getNifId() + " ["
                        + registry.getResourceName() + "] afterDate:" + afterDate);
                continue;
            }
            handleRegistry(registry);
        }
    }

    public void handleRegistry(Registry registry) throws Exception {
        List<QueryCandidate> candidates = prepSearchTermCandidates(registry);
        for (QueryCandidate qc : candidates) {
            String candidate = qc.getCandidate();
            URIBuilder builder = new URIBuilder(serviceURL);
            builder.setParameter("q", candidate);
            builder.setParameter("count", "1000");

            // for NIF web service change 04/26/2014
            builder.setParameter("searchFullText", "true");

            log.info("query:" + builder.build());
            if (getDelay() > 0) {
                Thread.sleep(getDelay());
            }
            String result = super.getResult(builder);

            if (result == null || result.indexOf("org.apache") != -1
                    || result.indexOf("SimpleQuery") != -1
                    || result.indexOf("WebApplicationImpl") != -1) {
                super.insertQueryLog(candidate, registry, publisher);
                log.warn("Server error for query " + builder.build());
                continue;
            }
            int size = Math.min(500, result.length());
            log.info(result.substring(0, size));

            // Utils.saveText(result, new File("/tmp/neuinfo.xml"));

            try {
                NeuinfoHandler handler = new NeuinfoHandler(candidate);
                SAXParserFactory factory = SAXParserFactory.newInstance();
                SAXParser saxParser = factory.newSAXParser();
                InputSource is = new InputSource(new StringReader(result));

                saxParser.parse(is, handler);

                int total = handler.getTotal();
                PublisherQueryLog pql = super.insertQueryLog(candidate, registry,
                        publisher);
                if (total > 0 && total < 1000) {
                    List<ArticleRec> articles = handler.getArticles();
                    saveArticles(articles, registry, pql, publisher,
                            qc.getType().equals(QueryCandidate.URL));
                }
            } catch (Exception x) {
                log.error(x);
            }
        }
    }

    @Override
    public void saveArticles(List<ArticleRec> articles, Registry registry,
                             PublisherQueryLog pql, Publisher publisher, boolean urlQuery) throws Exception {
        Transaction tx;
        EntityManager em = null;
        StatelessSession session = null;

        try {
            em = Utils.getEntityManager(emFactory);
            List<String> pmids = new ArrayList<String>(articles.size());
            for (ArticleRec ar : articles) {
                pmids.add(ar.getIdentifier());
            }
            Map<String, PaperReference> seenPPMap = new HashMap<String, PaperReference>();
            List<PaperReference> paperReferences = DisambiguatorFinder
                    .getMatchingPaperReferences(em, pmids, publisher.getId(),
                            registry.getId());
            Set<String> seenPMIDs = new HashSet<String>();
            for (PaperReference pr : paperReferences) {
                seenPMIDs.add(pr.getPubmedId());
                seenPPMap.put(pr.getPubmedId(), pr);
            }

            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();
            for (ArticleRec ar : articles) {
                if (seenPMIDs.contains(ar.getIdentifier())) {
                    // update PaperReference record if it does not have authors. description, mesh headings
                    PaperReference pr = seenPPMap.get(ar.getIdentifier());
                    if (pr != null && (pr.getAuthors() == null ||
                            (Utils.isEmpty(pr.getDescription()) && !Utils.isEmpty(ar.getDescription())))) {
                        if (urlQuery) {
                            pr.setFlags(PaperReference.URL_SEARCH);
                        }
                        if (ar.getDescription() != null) {
                            System.out.println("Description:" +
                                    ar.getDescription().substring(0, Math.min(100, ar.getDescription().length())));
                            pr.setDescription(ar.getDescription());
                        }
                        if (ar.getAuthors() != null) {
                            String authorsStr = GenUtils.join(ar.getAuthors(), ";");
                            pr.setAuthors(authorsStr);
                        }
                        if (ar.getMeshHeadings() != null) {
                            String mhStr = GenUtils.join(ar.getMeshHeadings(), ";");
                            pr.setMeshHeadings(mhStr);
                        }
                        session.update(pr);
                    }
                    continue;
                }
                PaperReference pr = new PaperReference();
                pr.setPublisher(publisher);
                pr.setPubmedId(ar.getIdentifier());
                pr.setRegistry(registry);
                pr.setPublisherDocId(ar.getIdentifier());
                pr.setPublicationDate(ar.getPublicationDate());
                pr.setPublicationName(ar.getPublicationName());
                pr.setTitle(ar.getTitle());
                pr.setPublisherQueryLog(pql);
                if (urlQuery) {
                    pr.setFlags(PaperReference.URL_SEARCH);
                }
                if (ar.getDescription() != null) {
                    pr.setDescription(ar.getDescription());
                }
                if (ar.getAuthors() != null) {
                    String authorsStr = GenUtils.join(ar.getAuthors(), ";");
                    pr.setAuthors(authorsStr);
                }
                if (ar.getMeshHeadings() != null) {
                    String mhStr = GenUtils.join(ar.getMeshHeadings(), ";");
                    pr.setMeshHeadings(mhStr);
                }
                session.insert(pr);
            }
            tx.commit();
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }

    }

    public static class NeuinfoHandler extends DefaultHandler {
        private boolean inPubName = false;
        private boolean inTitle = false;
        private boolean inDay = false;
        private boolean inMonth = false;
        private boolean inYear = false;
        private boolean inAuthor = false;
        private boolean inAffl = false;
        private boolean inAbstract = false;
        private boolean inMesh = false;

        StringBuilder sb = new StringBuilder(300);
        List<ArticleRec> articles = new ArrayList<ArticleRec>();
        ArticleRec curArticle = null;
        String candidate;
        DateInfo curDate = new DateInfo();
        int total = -1;
        boolean invalid = false;

        public NeuinfoHandler(String candidate) {
            this.candidate = candidate;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            if (qName.equals("result")) {
                String rc = attributes.getValue("resultCount");
                if (rc != null && rc.length() > 0) {
                    total = Integer.parseInt(rc);
                }
            } else if (qName.equals("publication")) {
                curArticle = new ArticleRec();
                // articles.add(curArticle);
                curArticle.identifier = attributes.getValue("pmid");
                invalid = false;
            } else if (qName.equals("author")) {
                inAuthor = true;
            } else if (qName.equals("journal")) {
                inPubName = true;
            } else if (qName.equals("title")) {
                inTitle = true;
            } else if (qName.equals("day")) {
                inDay = true;
            } else if (qName.equals("month")) {
                inMonth = true;
            } else if (qName.equals("year")) {
                inYear = true;
            } else if (qName.equals("affiliation")) {
                inAffl = true;
            } else if (qName.equals("abstract")) {
                inAbstract = true;
            } else if (qName.equals("meshHeading")) {
                inMesh = true;
            }

            sb.setLength(0);
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (qName.equals("author")) {
                inAuthor = false;
                if (!invalid) {
                    String s = sb.toString();
                    if (s.indexOf(candidate) != -1) {
                        invalid = true;
                    }
                }
                if (!invalid) {
                    curArticle.addAuthor(sb.toString().trim());
                }

            } else if (qName.equals("journal")) {
                inPubName = false;
                curArticle.publicationName = sb.toString().trim();
            } else if (qName.equals("title")) {
                if (curArticle != null) {
                    curArticle.title = sb.toString().trim();
                }
            } else if (qName.equals("year")) {
                inYear = false;
                curDate.year = sb.toString().trim();
            } else if (qName.equals("month")) {
                inMonth = false;
                curDate.month = sb.toString().trim();
            } else if (qName.equals("day")) {
                inDay = false;
                curDate.day = sb.toString().trim();
            } else if (qName.equals("publication")) {
                if (!invalid) {
                    curArticle.publicationDate = curDate.toDate();
                    articles.add(curArticle);
                }
            } else if (qName.equals("affiliation")) {
                inAffl = false;
                if (!invalid) {
                    String s = sb.toString();
                    if (s.indexOf(candidate) != -1) {
                        invalid = true;
                    }
                }
            } else if (qName.equals("abstract")) {
                inAbstract = false;
                if (!invalid) {
                    curArticle.description = sb.toString().trim();
                }
            } else if (qName.equals("meshHeading")) {
                inMesh = false;
                if (!invalid) {
                    curArticle.addMeshHeading(sb.toString().trim());
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (inAuthor || inPubName || inTitle || inDay || inMonth || inYear
                    || inAffl || inAbstract || inAuthor || inMesh) {
                sb.append(ch, start, length);
            }
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

        public int getTotal() {
            return total;
        }

        public static class DateInfo {
            String day;
            String month;
            String year;

            public String toDate() {
                StringBuilder sb = new StringBuilder();
                sb.append(year).append('-');
                if (month.length() == 1) {
                    sb.append('0').append(month);
                } else {
                    sb.append(month);
                }
                sb.append('-');
                if (day.length() == 1) {
                    sb.append('0').append(day);
                } else {
                    sb.append(day);
                }
                return sb.toString();
            }
        }

    } // ;

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }
}
