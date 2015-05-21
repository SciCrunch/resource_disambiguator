package org.neuinfo.resource.disambiguator.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;

import junit.framework.TestSuite;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.criterion.Restrictions;
import org.neuinfo.resource.disambiguator.BaseTestCase;
import org.neuinfo.resource.disambiguator.model.ResourceCandidate;
import org.neuinfo.resource.disambiguator.model.URLRec;
import org.neuinfo.resource.disambiguator.services.URLScorerService;
import org.neuinfo.resource.disambiguator.services.URLScorerServiceManager;

public class URLScorerServiceTests extends BaseTestCase {

	public URLScorerServiceTests(String testName) {
		super(testName);
	}

	
	public void testURLScorerServiceManager() throws Exception {
		String batchId = "201310";
		URLScorerServiceManager manager = new URLScorerServiceManager(batchId);
		
		this.injector.injectMembers(manager);
		
		manager.handle(67787);
	}
	
	public void testScoring() throws Exception {
		EntityManager em = emFactory.get();
		String batchId = "201310";
		StatelessSession session = ((Session) em.getDelegate())
				.getSessionFactory().openStatelessSession();
		Criteria criteria = session.createCriteria(ResourceCandidate.class)
				.setFetchMode("url", FetchMode.JOIN)
				.add(Restrictions.eq("batchId", batchId));

		criteria.setReadOnly(true).setFetchSize(1000).setCacheable(false);
		ScrollableResults results = criteria.scroll(ScrollMode.FORWARD_ONLY);

		int count = 0;
		List<String> categories = new ArrayList<String>(3);
		categories.add("biological_process");
		categories.add("anatomical_structure");
		categories.add("resource");

		URLScorerService service = new URLScorerService(categories, batchId);
		while (results.next()) {
			ResourceCandidate rc = (ResourceCandidate) results.get(0);

			if (rc.getUrl() != null) {
				String url = rc.getUrl().getUrl();
				System.out.println("scoring " + url);
			//	if (url.equals("http://www.kyb.mpg.de")) {
					URLScorerService.URLRecWrapper urw = service.scoreURL(rc.getUrl());
                    URLRec ur = urw.getUrlRec();
					System.out.println("score: " + ur.getScore() + "  "
							+ ur.getUrl());
			//	}

			}
			count++;
			if (count > 10) {
				break;
			}
		}

		session.close();

	}

	public void testSpanMatcher() throws Exception {
		String annotatedTxt = " A key <span class=\"nifAnnotation\" data-nif=\"regionalization,GO_0003002,biological_process\">region</span> "
				+ "in the human parietal cortex for processing <span class=\"nifAnnotation\" data-nif=\"proprioception,GO_0019230,biological_process\">proprioceptive</span> "
				+ "hand feedback during reaching movements NeuroImage 84 615–625. Upcoming Talk MIKO: TBA 13. November   "
				+ "|   15:00 - 16:30 Category:   MoKo/MiKo Prof. Thilo Stehle University of Tuebingen [more]     "
				+ "Brain Research and Animal Experiments Information about brain research with animals     "
				+ "Understanding Thought Processes The Max Planck Institute for Biological Cybernetics is studying "
				+ "<span class=\"nifAnnotation\" data-nif=\"signaling,GO_0023052,biological_process|signalling,GO_0023052,biological_process\">signal</span> "
				+ "and information processing in the brain. "
				+ "We know that our brain is constantly processing a vast amount of sensory and intrinsic "
				+ "information with which our <span class=\"nifAnnotation\" data-nif=\"behavior,GO_0007610,biological_process\">behavior</span> is "
				+ "coordinated accordingly. Interestingly, how the brain actually achieves these tasks is less well understood, "
				+ "for example, how it perceives, recognizes, and "
				+ "<span class=\"nifAnnotation\" data-nif=\"learning,GO_0007612,biological_process\">learns</span> new objects. "
				+ "The scientists at the Max Planck Institute for Biological Cy";

		Pattern spanPattern = Pattern
				.compile("<span.+?data-nif=\"([^\"]+)");
		Matcher matcher = spanPattern.matcher(annotatedTxt);
		int count = 0;
		while (matcher.find()) {
			String mc = matcher.group(1).toLowerCase();
		    System.out.println(mc);
		    ++count;
		}
		assertTrue(count > 0);
	}

	public static TestSuite suite() {
		TestSuite suite = new TestSuite();
		// suite.addTest(new URLScorerServiceTests("testScoring"));
		
		//suite.addTest(new URLScorerServiceTests("testSpanMatcher"));
		suite.addTest( new URLScorerServiceTests("testURLScorerServiceManager"));
		return suite;
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}
}
