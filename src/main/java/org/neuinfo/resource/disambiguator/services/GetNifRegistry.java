package org.neuinfo.resource.disambiguator.services;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.neuinfo.resource.disambiguator.model.Registry;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

/**
 * TODO Put here a description of what this class does.
 * 
 * @author vadim. Created Oct 22, 2013.
 */
public class GetNifRegistry {
	@Inject
	@IndicatesPrimaryJpa
	Provider<EntityManager> emFactory;

	public GetNifRegistry() {
		// TODO overload constructor to load registry when object created. Use
		// singleton pattern
	}

	protected void setUpJPA() throws Exception {
		Injector injector = Guice.createInjector(new RDPersistModule());
		injector.injectMembers(this);
	}

	protected void tearDownJPA() throws Exception {
		JPAInitializer.stopService();

	}

	@Transactional
	public List<Registry> testGetAllRegistries() {
		EntityManager em = emFactory.get();

		TypedQuery<Registry> query = em.createQuery("from Registry",
				Registry.class);

		List<Registry> resultList = query.getResultList();

		// assertFalse(resultList.isEmpty());

		return resultList;
	}

	public static void main(String[] args) {
		GetNifRegistry nifRegistry = new GetNifRegistry();
		try {
			nifRegistry.setUpJPA();
		} catch (Exception exception) {
			// TODO Auto-generated catch-block stub.
			exception.printStackTrace();
		}

		nifRegistry.testGetAllRegistries();

		try {
			nifRegistry.tearDownJPA();
		} catch (Exception exception) {
			// TODO Auto-generated catch-block stub.
			exception.printStackTrace();
		}
	}
}
