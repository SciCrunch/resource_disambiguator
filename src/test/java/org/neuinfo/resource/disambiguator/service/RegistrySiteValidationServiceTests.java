package org.neuinfo.resource.disambiguator.service;

import junit.framework.TestSuite;

import org.neuinfo.resource.disambiguator.BaseTestCase;
import org.neuinfo.resource.disambiguator.services.RegistrySiteValidationService;

/**
 * 
 * @author bozyurt
 *
 */
public class RegistrySiteValidationServiceTests extends BaseTestCase {

	public RegistrySiteValidationServiceTests(String testName) {
		super(testName);
	}

	public void testRegistrySiteValidation() throws Exception {
		RegistrySiteValidationService service = new RegistrySiteValidationService();

		this.injector.injectMembers(service);

		service.handle();
	}

	public static TestSuite suite() {
		TestSuite suite = new TestSuite();
		suite.addTest(new RegistrySiteValidationServiceTests(
				"testRegistrySiteValidation"));
		return suite;
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}
}
