package org.neuinfo.resource.disambiguator.service;

import java.util.List;

import javax.persistence.EntityManager;

import junit.framework.TestSuite;

import org.neuinfo.resource.disambiguator.BaseTestCase;
import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.services.DisambiguatorFinder;
import org.neuinfo.resource.disambiguator.services.NatureReferenceSearchService;
import org.neuinfo.resource.disambiguator.services.NeuinfoReferenceSearchService;
import org.neuinfo.resource.disambiguator.services.SpringerReferenceSearchService;
import org.neuinfo.resource.disambiguator.util.Utils;

public class ReferenceSearchServiceTests extends BaseTestCase {

	public ReferenceSearchServiceTests(String testName) {
		super(testName);
	}

	public void testSpringerReferenceSearchService() throws Exception {
		List<Registry> registryRecords = null;
		EntityManager em = null;
		try {
			em = Utils.getEntityManager(emFactory);
			registryRecords = DisambiguatorFinder.getAllRegistryRecords(em);

		} finally {
			Utils.closeEntityManager(em);
		}
		SpringerReferenceSearchService service = new SpringerReferenceSearchService();
		this.injector.injectMembers(service);
		service.init(registryRecords);
		service.handle(Utils.getStartOfMonth());
	}

	public void testNatureReferenceSearchService() throws Exception {
		List<Registry> registryRecords = null;
		EntityManager em = null;
		try {
			em = Utils.getEntityManager(emFactory);
			registryRecords = DisambiguatorFinder.getAllRegistryRecords(em);

		} finally {
			Utils.closeEntityManager(em);
		}
		NatureReferenceSearchService service = new NatureReferenceSearchService();
		this.injector.injectMembers(service);
		service.init(registryRecords);
		service.handle(Utils.getStartOfMonth());
	}

	public void testNeuinfoReferenceSearchService() throws Exception {
		List<Registry> registryRecords = null;
		EntityManager em = null;
		try {
			em = Utils.getEntityManager(emFactory);
			registryRecords = DisambiguatorFinder.getAllRegistryRecords(em);

		} finally {
			Utils.closeEntityManager(em);
		}
		NeuinfoReferenceSearchService service = new NeuinfoReferenceSearchService();
		this.injector.injectMembers(service);
		service.init(registryRecords);
		service.handle(Utils.getStartOfMonth());
	}

	public static TestSuite suite() {
		TestSuite suite = new TestSuite();
		suite.addTest(new ReferenceSearchServiceTests(
				"testSpringerReferenceSearchService"));
		// suite.addTest(new ReferenceSearchServiceTests(
		// "testNatureReferenceSearchService"));

	//	suite.addTest(new ReferenceSearchServiceTests(
		//		"testNeuinfoReferenceSearchService"));
		return suite;
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}
}
