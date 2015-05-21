package org.neuinfo.resource.disambiguator.model;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.neuinfo.resource.disambiguator.BaseTestCase;


import com.google.inject.persist.Transactional;

import junit.framework.TestSuite;

public class ModelTests extends BaseTestCase {
	public ModelTests(String testName) {
		super(testName);
	}

	@Transactional
	public void testGetAllRegistries() {
		EntityManager em = emFactory.get();

		TypedQuery<Registry> query = em.createQuery("from Registry",
				Registry.class);
		
		List<Registry> resultList = query.getResultList();
		
		assertFalse(resultList.isEmpty());
	}
	
	
	public static TestSuite suite() {
		TestSuite suite = new TestSuite();
		suite.addTest( new ModelTests("testGetAllRegistries"));
		return suite;
	}
	
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}
}
