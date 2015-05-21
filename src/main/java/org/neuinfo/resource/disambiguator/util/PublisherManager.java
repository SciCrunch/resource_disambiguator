package org.neuinfo.resource.disambiguator.util;

import javax.persistence.EntityManager;

import org.neuinfo.resource.disambiguator.model.Publisher;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

/**
 * 
 * @author bozyurt
 * 
 */
public class PublisherManager {
	@Inject
	@IndicatesPrimaryJpa
	protected Provider<EntityManager> emFactory;

	public void populate() throws Exception {
		EntityManager em = null;

		try {
			em = Utils.getEntityManager(emFactory);
			Utils.beginTransaction(em);

			Publisher springer = new Publisher();
			springer.setPublisherName("Springer");
			springer.setNumConnectionsAllowed(5000);
			springer.setApiKey("j6v9qrqepmphuett2zsn4u56|hv3dmtydt4dhptpwxq2zpxvx|wemhb8u4x35h2skyhnjygwz8|gp28gzhgwtb5j2jgydhqegan");

			Publisher nature = new Publisher();
			nature.setPublisherName("Nature");
			nature.setApiKey("39apsu8ss6wpcevchb73qtdb|adetmyrrnqkb44dkzqdpmkxt|n8469fahtf4hd975mj8939nu");

			Publisher nif = new Publisher();
			nif.setPublisherName("neuinfo.org");

			em.persist(springer);
			em.persist(nature);
			em.persist(nif);
			Utils.commitTransaction(em);
		} finally {
			Utils.closeEntityManager(em);
		}
	}
	
	
	public static void main(String[] args) throws Exception {
		Injector injector = Guice.createInjector(new RDPersistModule());
		try {
			PublisherManager pm = new PublisherManager();
			injector.injectMembers(pm);
			
			pm.populate();
		} finally {
			JPAInitializer.stopService();
		}
	}

}
