package org.neuinfo.resource.disambiguator.persist;

import javax.persistence.EntityManager;

import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import com.google.inject.Exposed;
import com.google.inject.PrivateModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.jpa.JpaPersistModule;

public class RDPersistModule extends PrivateModule {

	@Override
	protected void configure() {
		install(new JpaPersistModule("mainJpaUnit"));
		bind(JPAInitializer.class).asEagerSingleton();

	}

	@Exposed
	@Provides
	@IndicatesPrimaryJpa
	PersistService getPersistService(Provider<PersistService> provider) {
		return provider.get();
	}

	@Exposed
	@Provides
	@IndicatesPrimaryJpa
	EntityManager getEntityManager(Provider<EntityManager> provider,
			JPAInitializer init) {
		return provider.get();
	}

}
