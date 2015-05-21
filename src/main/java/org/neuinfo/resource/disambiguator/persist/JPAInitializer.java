package org.neuinfo.resource.disambiguator.persist;

import java.util.concurrent.atomic.AtomicBoolean;

import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.PersistService;

@Singleton
public class JPAInitializer {
	AtomicBoolean started = new AtomicBoolean(false);
	private static PersistService service;

	@Inject
	public JPAInitializer(@IndicatesPrimaryJpa PersistService service) {
		if (started.compareAndSet(false, true)) {
			JPAInitializer.service = service;
			service.start();
		}
	}

	public synchronized static void stopService() {
		if (service != null) {
			service.stop();
			service = null;
		}
	}

}