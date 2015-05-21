package org.neuinfo.resource.disambiguator.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;

import junit.framework.TestSuite;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.neuinfo.resource.disambiguator.BaseTestCase;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;
import org.neuinfo.resource.disambiguator.util.Utils;

public class NewResourceCandidateFinderServiceTests extends BaseTestCase {
	private Map<String, Registry> registryMap = new HashMap<String, Registry>(
			9973);

	public NewResourceCandidateFinderServiceTests(String testName) {
		super(testName);
	}

	public void testRegistryUrlMatching() throws Exception {
		EntityManager em = emFactory.get();
		String batchId = "201310";
		StatelessSession session = null;
		try {
			prepRegistryMap();
			em = Utils.getEntityManager(emFactory);
			session = ((Session) em.getDelegate()).getSessionFactory()
					.openStatelessSession();

			ScrollableResults urs = getAllUrlRecordsForBatch(session, batchId);
			Set<Integer> resourceCandidateUrlIdsSet = getExistingResourceCandidateUrlIds(
					session, batchId);

			Set<String> seenPMCSet = new HashSet<String>();
			while (urs.next()) {
				URLRec ur = (URLRec) urs.get(0);
				String url = ur.getUrl();
				url = Utils.normalizeUrl(url);
				url = url.replaceAll("\\\\", "");
				checkIfUrlInRegistry(session, url, ur, seenPMCSet,
						resourceCandidateUrlIdsSet, batchId);
			}
		} finally {
			if (session != null) {
				session.close();
			}
			Utils.closeEntityManager(em);
		}

	}

	void checkIfUrlInRegistry(StatelessSession session, String url, URLRec ur,
			Set<String> seenPMCSet, Set<Integer> resourceCandidateUrlIdsSet,
			String batchId) {
		// Integer urlID = ur.getId();
		if (!registryMap.containsKey(url)) {
			if (url.indexOf("dicty") != -1) {
				System.out.println(">>>> " + url);
			}
			if (url.indexOf("www.") != -1) {
				int idx = url.indexOf("www.");
				String url1 = url.substring(idx + 4);
				url1 = "http://" + url1;
				if (registryMap.containsKey(url1)) {
					System.out.println("(1) In registry: " + url1 + " ("
							+ ur.getUrl() + ")");
				}
			}
			int dotCount = Utils.numOfMatches(url, '.');
			if (dotCount == 1) {
				String url1 = url.replaceFirst("http:\\/\\/", "http://www.");
				if (registryMap.containsKey(url1)) {
					System.out.println("(2) In registry: " + url1 + " ("
							+ ur.getUrl() + ")");
				}
			}
		}
		seenPMCSet.add(url);

	}

	ScrollableResults getAllUrlRecordsForBatch(StatelessSession session,
			String batchId) {
		Query query = session.createQuery(
				"from URLRec where batchId = :batchId").setString("batchId",
				batchId);
		query.setReadOnly(false);
		query.setFetchSize(1000);
		return query.scroll(ScrollMode.FORWARD_ONLY);
	}

	public Set<Integer> getExistingResourceCandidateUrlIds(
			StatelessSession session, String batchId) {
		Query query = session
				.createQuery(
						"select r.url.id from ResourceCandidate r where batchId = :batchId")
				.setString("batchId", batchId);
		List<?> list = query.list();
		Set<Integer> seenURLSet = new HashSet<Integer>(list.size());
		for (Object o : list) {
			seenURLSet.add(new Integer(o.toString()));
		}
		return seenURLSet;
	}

	public void prepRegistryMap() {
		EntityManager em = null;
		try {
			em = Utils.getEntityManager(emFactory);

			List<Registry> registryRecords = DisambiguatorFinder
					.getAllRegistryRecords(em);
			for (Registry reg : registryRecords) {
				String url = reg.getUrl();
				url = Utils.normalizeUrl(url);
				this.registryMap.put(url, reg);
			}
		} finally {
			Utils.closeEntityManager(em);
		}
	}

	public static TestSuite suite() {
		TestSuite suite = new TestSuite();
		suite.addTest(new NewResourceCandidateFinderServiceTests(
				"testRegistryUrlMatching"));
		return suite;
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}
}
