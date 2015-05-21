package org.neuinfo.resource.disambiguator;

import javax.persistence.EntityManager;

import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

import junit.framework.TestCase;

public abstract class BaseTestCase extends TestCase {

	@Inject
	@IndicatesPrimaryJpa
	protected Provider<EntityManager> emFactory;
	
	protected Injector injector;

	public BaseTestCase() {
		super();
	}

	public BaseTestCase(String name) {
		super(name);
	}

	@Override
	protected void setUp() throws Exception {
		injector = Guice.createInjector(new RDPersistModule());
		injector.injectMembers(this);
	}

	@Override
	protected void tearDown() throws Exception {
		JPAInitializer.stopService();
		super.tearDown();
	}

}