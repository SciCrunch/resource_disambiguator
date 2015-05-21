package org.neuinfo.resource.disambiguator.services;

import bnlpkit.util.GenUtils;
import com.google.inject.Provider;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.neuinfo.resource.disambiguator.model.*;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.Utils;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.*;

/**
 * @author bozyurt
 */
public abstract class BaseReferenceSearchService {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory = null;

    protected List<Registry> registryList;
    protected PMIDService pmidService;
    static Logger log = Logger.getLogger(BaseReferenceSearchService.class);

    public BaseReferenceSearchService() {
        pmidService = new PMIDService();
    }

    abstract public void init(List<Registry> registryList);

    abstract public void handle(Calendar afterDate) throws Exception;


    public void shutdown() {
        if (pmidService != null) {
            pmidService.shutdown();
        }
    }

    public static List<QueryCandidate> prepSearchTermCandidates(Registry registry) {
        List<QueryCandidate> candidates = new ArrayList<QueryCandidate>(10);
        String name = registry.getResourceName();
        candidates.add(new QueryCandidate(registry.getResourceName(), QueryCandidate.OTHER));
        String url = registry.getUrl();

        url = Utils.extractUrl(url);
        if (url != null) {
            url = url.replaceFirst("^http://", "");
            url = url.replaceFirst("/$", "");
            candidates.add(new QueryCandidate(url, QueryCandidate.URL));
        }
        if (name.indexOf(':') != -1) {
            String[] toks = name.split(":");
            candidates.add(new QueryCandidate(toks[0].trim(), QueryCandidate.OTHER));
        } else if (name.indexOf('-') != -1) {
            String[] toks = name.split("-");
            candidates.add(new QueryCandidate(toks[0].trim(), QueryCandidate.OTHER));
        }

        String synonym = registry.getSynonym();
        // split in case of multiple synonyms
        if (synonym != null && synonym.trim().length() > 0) {
            synonym = synonym.trim();
            List<String> synonyms = new ArrayList<String>(3);
            if (synonym.indexOf(',') != -1) {
                String[] toks = synonym.split("\\s*,\\s*");
                for (String tok : toks) {
                    synonyms.add(tok);
                }
            } else {
                synonyms.add(synonym);
            }
            for (String syn : synonyms) {
                if (syn.indexOf(':') != -1) {
                    String[] toks = syn.split(":");
                    candidates.add(new QueryCandidate(toks[0].trim(), QueryCandidate.OTHER));
                } else {
                    candidates.add(new QueryCandidate(syn.trim(), QueryCandidate.OTHER));
                }
            }
        }

        Set<String> seenSet = new HashSet<String>();
        for (Iterator<QueryCandidate> it = candidates.iterator(); it.hasNext(); ) {
            QueryCandidate qc = it.next();
            String candidate = qc.getCandidate();
            if (!seenSet.contains(candidate) && candidate.length() > 0) {
                seenSet.add(candidate);
            } else {
                it.remove();
            }
        }
        return candidates;
    }

    protected void addCheckPoint(String batchId, String status) throws Exception {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Utils.beginTransaction(em);

            Query query = em.createQuery("select max(id) from PaperReference");
            Long maxId = (Long) query.getSingleResult();

            CheckPoint cp = new CheckPoint();
            cp.setOpType("publisher_search");
            cp.setTableName("rd_paper_reference");
            cp.setStatus(status);
            cp.setBatchId(batchId);
            cp.setPkValue(maxId);

            em.persist(cp);

            query = em.createQuery("select max(id) from PublisherQueryLog");
            maxId = (Long) query.getSingleResult();

            cp = new CheckPoint();
            cp.setOpType("publisher_search");
            cp.setTableName("rd_publisher_query_log");
            cp.setStatus(status);
            cp.setBatchId(batchId);
            cp.setPkValue(maxId);

            em.persist(cp);
            Utils.commitTransaction(em);
        } catch (Exception x) {
            Utils.rollbackTransaction(em);
            throw x;
        } finally {
            Utils.closeEntityManager(em);
        }

    }

    protected Set<Registry> getAlreadyProcessedRegistries(Calendar afterDate,
                                                          Publisher publisher) {
        Set<Registry> seenRegSet = new HashSet<Registry>();
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            for (Registry registry : registryList) {
                List<PublisherQueryLog> queryLogs = DisambiguatorFinder
                        .getQueryLogs(em, registry.getId(), publisher.getId(),
                                afterDate);
                if (!queryLogs.isEmpty()) {
                    seenRegSet.add(registry);
                } else {
                    break;
                }
            }

            // last registry handled is most probably not complete so remove it from the seenRegSet
            PublisherQueryLog latestQueryLog = DisambiguatorFinder.getLatestQueryLog(em, publisher.getId(), afterDate);
            if (latestQueryLog != null) {
                Registry latestReg = latestQueryLog.getRegistry();
                for (Iterator<Registry> it = seenRegSet.iterator(); it.hasNext(); ) {
                    Registry reg = it.next();
                    if (latestReg.getId() == reg.getId()) {
                        it.remove();
                        break;
                    }
                }
            }
            return seenRegSet;
        } finally {
            Utils.closeEntityManager(em);
        }
    }

    protected PublisherQueryLog insertQueryLog(String candidate,
                                               Registry registry, Publisher publisher) {
        Transaction tx;
        EntityManager em = null;
        StatelessSession session = null;
        try {
            em = Utils.getEntityManager(emFactory);
            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();

            PublisherQueryLog ql = new PublisherQueryLog();
            ql.setQueryString(candidate);
            ql.setRegistry(registry);
            ql.setPublisher(publisher);
            ql.setExecTime(Calendar.getInstance());

            Serializable id = session.insert(ql);

            PublisherQueryLog pql = (PublisherQueryLog) session.get(
                    PublisherQueryLog.class, id);
            tx.commit();
            return pql;
        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }
    }

    public String getResult(URIBuilder builder) {
        HttpClient client = new DefaultHttpClient();

        HttpGet httpGet = null;
        try {
            URI uri = builder.build();
            httpGet = new HttpGet(uri);
            httpGet.addHeader("Accept","application/xml");

            HttpResponse resp = client.execute(httpGet);
            HttpEntity entity = resp.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            log.warn(t.getMessage());
        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
        return null;
    }

    public Publisher getPublisher(String publisherName) {
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);

            return DisambiguatorFinder.getPublisher(em, publisherName);
        } finally {
            Utils.closeEntityManager(em);
        }

    }

    public static List<String> getApiKeys(Publisher publisher) {
        if (publisher.getApiKey() == null) {
            return new ArrayList<String>(0);
        }
        String[] toks = publisher.getApiKey().split("\\|");
        return Arrays.asList(toks);
    }

    public void saveArticles(List<ArticleRec> articles, Registry registry,
                             PublisherQueryLog pql, Publisher publisher, boolean urlQuery) throws Exception {
        Transaction tx;
        EntityManager em = null;
        StatelessSession session = null;

        try {
            em = Utils.getEntityManager(emFactory);

            List<String> pmids = new ArrayList<String>(articles.size());

            List<PMIDService.PMIDInfo> piList = new ArrayList<PMIDService.PMIDInfo>(articles.size());
            for (ArticleRec ar : articles) {
                // first check if the paper is already processed, if so use the existing PMID
                PaperReference paperReferenceMatching = DisambiguatorFinder.findPaperReferenceMatching(em,
                        ar.getIdentifier(), publisher);
                if (paperReferenceMatching != null) {
                    String pmid = paperReferenceMatching.getPubmedId();
                    pmids.add(pmid);
                } else {
                    piList.add(new PMIDService.PMIDInfo(ar.getPublicationName(), ar.getTitle()));
                }
            }
            // get all remaining pmids in bulk with parallel processing
            Map<String, PMIDService.PMIDInfo> piMap = pmidService.getPMIDs(piList);
            for (ArticleRec ar : articles) {
                String key = new PMIDService.PMIDInfo(ar.getPublicationName(), ar.getTitle()).getKey();
                if (piMap.containsKey(key)) {
                    PMIDService.PMIDInfo pi = piMap.get(key);
                    ar.setPmid(pi.getPmid());
                    pmids.add(ar.getPmid());
                }
            }

        /*
            for (ArticleRec ar : articles) {
                String pmid;

                // first check if the paper is already processed, if so use the existing PMID
                PaperReference paperReferenceMatching = DisambiguatorFinder.findPaperReferenceMatching(em,
                        ar.getIdentifier(), publisher);
                if (paperReferenceMatching != null) {
                    pmid = paperReferenceMatching.getPubmedId();
                } else {
                    try {
                        // NB: checking PUBMED with publication and article name is more robust than checking for DOI (IBO)
                        pmid = DOI2PMIDServiceClient.getPMID(ar.getPublicationName(), ar.getTitle());
                        // pmid = DOI2PMIDServiceClient.getPMID(ar.getIdentifier());
                    } catch (Exception e) {
                        e.printStackTrace();
                        pmid = null;
                    }
                }
                if (pmid != null) {
                    ar.setPmid(pmid);
                    pmids.add(ar.getPmid());
                }
            }
            */

            Set<String> seenPMIDs = new HashSet<String>();
            Map<String, PaperReference> seenPPMap = new HashMap<String, PaperReference>();
            if (!pmids.isEmpty()) {
                List<PaperReference> paperReferences = DisambiguatorFinder
                        .getMatchingPaperReferences(em, pmids,
                                publisher.getId(), registry.getId());

                for (PaperReference pr : paperReferences) {
                    seenPMIDs.add(pr.getPubmedId());
                    seenPPMap.put(pr.getPubmedId(), pr);
                }

                List<String> matchingPMIDs = DisambiguatorFinder.getMatchingPMIDsFromURLMatch(em, pmids,
                        registry.getId());
                for (String mp : matchingPMIDs) {
                    seenPMIDs.add(mp);
                }
                matchingPMIDs = DisambiguatorFinder.getMatchingPMIDsFromNER(em, pmids,
                        registry.getId());
                for (String mp : matchingPMIDs) {
                    seenPMIDs.add(mp);
                }
            }

            session = ((Session) em.getDelegate()).getSessionFactory()
                    .openStatelessSession();
            tx = session.beginTransaction();
            for (ArticleRec ar : articles) {
                if (ar.getPmid() != null && seenPMIDs.contains(ar.getPmid())) {
                    log.info("Already seen before " + ar.toString());
                } else if (ar.getPmid() == null) {
                    log.info("No pmid for " + ar.toString());
                }
                if (ar.getPmid() != null && !seenPMIDs.contains(ar.getPmid())) {
                    PaperReference pr = new PaperReference();
                    pr.setPublisher(publisher);
                    pr.setPubmedId(ar.getPmid());
                    pr.setRegistry(registry);
                    pr.setPublisherDocId(ar.getIdentifier());
                    pr.setGenre(ar.getGenre());
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
                    log.info("adding article ref " + ar.toString());
                    session.insert(pr);
                } else {
                    // update PaperReference record if it does not have authors. description, mesh headings
                    if (ar.getPmid() != null) {
                        PaperReference pr = seenPPMap.get(ar.getPmid());
                        if (pr != null && (pr.getAuthors() == null ||
                                (Utils.isEmpty(pr.getDescription()) && !Utils.isEmpty(ar.getDescription())))) {
                            if (urlQuery) {
                                pr.setFlags(PaperReference.URL_SEARCH);
                            }
                            if (ar.getDescription() != null) {
                                System.out.println("Description:" + ar.getDescription());
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
                            log.info("updating article ref " + ar.toString());
                            session.update(pr);
                        }

                    }
                }
            } // for
            tx.commit();

        } finally {
            if (session != null) {
                session.close();
            }
            Utils.closeEntityManager(em);
        }

    }

}
