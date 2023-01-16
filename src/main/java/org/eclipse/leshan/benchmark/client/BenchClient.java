package org.eclipse.leshan.benchmark.client;

import static org.eclipse.leshan.client.object.Security.*;
import static org.eclipse.leshan.core.LwM2mId.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.leshan.client.californium.*;
import org.eclipse.leshan.client.object.*;
import org.eclipse.leshan.client.observer.LwM2mClientObserverAdapter;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.ResponseCode;
import org.eclipse.leshan.core.model.*;
import org.eclipse.leshan.core.request.*;
import org.slf4j.*;

import com.codahale.metrics.*;

public class BenchClient {

	private Counter bootstrapSuccess;
	private Counter bootstrapFailure;
	private Counter bootstrapTimeout;
	private Counter registrationSuccess;
	private Counter registrationFailure;
	private Counter registrationTimeout;
	private Counter updateSuccess;
	private Counter updateFailure;
	private Counter updateTimeout;
	private Counter deregistrationSuccess;
	private Counter deregistrationFailure;
	private Counter deregistrationTimeout;

	private final boolean secure;

	static {
		List<ObjectModel> objectModels = ObjectLoader.loadDefault();
		try {
			objectModels.addAll(ObjectLoader.loadDdfResources(new String[] { "/LWM2M_Software_Management-v1_0.xml" }));
		} catch (InvalidDDFFileException | IOException | InvalidModelException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		model = new StaticModel(objectModels);
	}

	private static final Logger LOG = LoggerFactory.getLogger(BenchClient.class);

	private final static LwM2mModel model;

	private LeshanClient client;

	public BenchClient(LeshanClientBuilder builder, String serverURI, boolean bootstrap, String pskId, byte[] pskKey,
			long lifetimeInSec, MetricRegistry metricRegistry) {

		// register metrics
		bootstrapSuccess = registerIfNotExist(metricRegistry, "leshan.bench.client.bootstrap.success", new Counter());
		bootstrapFailure = registerIfNotExist(metricRegistry, "leshan.bench.client.bootstrap.failure", new Counter());
		bootstrapTimeout = registerIfNotExist(metricRegistry, "leshan.bench.client.bootstrap.timeout", new Counter());
		registrationSuccess = registerIfNotExist(metricRegistry, "leshan.bench.client.registration.success",
				new Counter());
		registrationFailure = registerIfNotExist(metricRegistry, "leshan.bench.client.registration.failure",
				new Counter());
		registrationTimeout = registerIfNotExist(metricRegistry, "leshan.bench.client.registration.timeout",
				new Counter());
		updateSuccess = registerIfNotExist(metricRegistry, "leshan.bench.client.update.success", new Counter());
		updateFailure = registerIfNotExist(metricRegistry, "leshan.bench.client.update.failure", new Counter());
		updateTimeout = registerIfNotExist(metricRegistry, "leshan.bench.client.update.timeout", new Counter());
		deregistrationSuccess = registerIfNotExist(metricRegistry, "leshan.bench.client.deregistration.success",
				new Counter());
		deregistrationFailure = registerIfNotExist(metricRegistry, "leshan.bench.client.deregistration.failure",
				new Counter());
		deregistrationTimeout = registerIfNotExist(metricRegistry, "leshan.bench.client.deregistration.timeout",
				new Counter());

		// Create objects
		ObjectsInitializer initializer = new ObjectsInitializer(model);

		// String endpoint;
		secure = serverURI.startsWith("coaps");
		if (secure) {
			if (bootstrap) {
				initializer.setInstancesForObject(SECURITY, pskBootstrap(serverURI, pskId.getBytes(), pskKey));
				initializer.setClassForObject(SERVER, Server.class);
			} else {
				initializer.setInstancesForObject(SECURITY, psk(serverURI, 123, pskId.getBytes(), pskKey));
				initializer.setInstancesForObject(SERVER, new Server(123, lifetimeInSec));
			}
		} else {
			if (bootstrap) {
				initializer.setInstancesForObject(SECURITY, noSecBootstrap(serverURI));
				initializer.setClassForObject(SERVER, Server.class);
			} else {
				System.err.println("no sec");
				initializer.setInstancesForObject(SECURITY, noSec(serverURI, 123));
				initializer.setInstancesForObject(SERVER, new Server(123, lifetimeInSec));
			}

		}
		initializer.setInstancesForObject(DEVICE, new Device("leshan","benchmark","1234567890"));
		initializer.setDummyInstancesForObject(5, 9);

		builder.setObjects(initializer.createAll());

		// builder.setEncoder(new DefaultLwM2mNodeEncoder(true));
		// builder.setDecoder(new DefaultLwM2mNodeDecoder(true));
		Configuration cfConfig = LeshanClientBuilder.createDefaultCoapConfiguration();
		cfConfig.set(CoapConfig.PREFERRED_BLOCK_SIZE, 1024);
		builder.setCoapConfig(cfConfig);

		client = builder.build();

		client.addObserver(new LwM2mClientObserverAdapter() {

			@Override
			public void onUpdateTimeout(ServerIdentity server, UpdateRequest request) {
				updateTimeout.inc();
			}

			@Override
			public void onUpdateSuccess(ServerIdentity server, UpdateRequest request) {
				updateSuccess.inc();
			}

			@Override
			public void onUpdateFailure(ServerIdentity server, UpdateRequest request, ResponseCode responseCode,
					String errorMessage, Exception e) {
				updateFailure.inc();
				if (e != null) {
					if (LOG.isTraceEnabled()) {
						LOG.trace("Update Failed : {}", extractMessage(e), e);
					} else if (LOG.isDebugEnabled()) {
						LOG.debug("Update Failed : {}", extractMessage(e));
					}
				} else {
					LOG.debug("Update Failed : {} {}", responseCode, errorMessage);
				}
			}

			@Override
			public void onRegistrationTimeout(ServerIdentity server, RegisterRequest request) {
				registrationTimeout.inc();
			}

			@Override
			public void onRegistrationSuccess(ServerIdentity server, RegisterRequest request, String registrationID) {
				registrationSuccess.inc();
			}

			@Override
			public void onRegistrationFailure(ServerIdentity server, RegisterRequest request, ResponseCode responseCode,
					String errorMessage, Exception e) {
				registrationFailure.inc();
				if (e != null) {
					if (LOG.isTraceEnabled()) {
						LOG.trace("Registration Failed : {}", extractMessage(e), e);
					} else if (LOG.isDebugEnabled()) {
						LOG.debug("Registration Failed : {}", extractMessage(e));
					}
				} else {
					LOG.debug("Registration Failed : {} {}", responseCode, errorMessage);
				}
			}

			@Override
			public void onDeregistrationTimeout(ServerIdentity server, DeregisterRequest request) {
				deregistrationTimeout.inc();
			}

			@Override
			public void onDeregistrationSuccess(ServerIdentity server, DeregisterRequest request) {
				deregistrationSuccess.inc();
			}

			@Override
			public void onDeregistrationFailure(ServerIdentity server, DeregisterRequest request,
					ResponseCode responseCode, String errorMessage, Exception e) {
				deregistrationFailure.inc();
				if (e != null) {
					if (LOG.isTraceEnabled()) {
						LOG.trace("Deregistration Failed : {}", extractMessage(e), e);
					} else if (LOG.isDebugEnabled()) {
						LOG.debug("Deregistration Failed : {}", extractMessage(e));
					}
				} else {
					LOG.debug("Deregistration Failed : {} {}", responseCode, errorMessage);
				}
			}

			@Override
			public void onBootstrapTimeout(ServerIdentity bsserver, BootstrapRequest request) {
				bootstrapTimeout.inc();
			}

			@Override
			public void onBootstrapSuccess(ServerIdentity bsserver, BootstrapRequest request) {
				bootstrapSuccess.inc();
			}

			@Override
			public void onBootstrapFailure(ServerIdentity bsserver, BootstrapRequest request, ResponseCode responseCode,
					String errorMessage, Exception e) {
				bootstrapFailure.inc();
				if (e != null) {
					if (LOG.isTraceEnabled()) {
						LOG.trace("Bootstrap Failed : {}", extractMessage(e), e);
					} else if (LOG.isDebugEnabled()) {
						LOG.debug("Bootstrap Failed : {}", extractMessage(e));
					}
				} else {
					LOG.debug("Bootstrap Failed : {} {}", responseCode, errorMessage);
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	private <T extends Metric> T registerIfNotExist(MetricRegistry registry, String name, T metric) {
		Metric prev = registry.getMetrics().get(name);
		if (prev == null) {
			registry.register(name, metric);
			return metric;
		} else if (prev.getClass().isAssignableFrom(metric.getClass())) {
			return (T) prev;
		} else {
			throw new IllegalArgumentException("incompatible registry");
		}
	}

	private String extractMessage(Exception e) {
		if (e.getCause() != null && e.getCause().getMessage() != null) {
			return e.getCause().getMessage();
		} else if (e.getMessage() != null) {
			return e.getMessage();
		} else {
			return e.getClass().getName();
		}
	}

	public void start() {
		client.start();
	}

	public void stop(boolean deregister) {
		client.stop(deregister);
	}

	public InetSocketAddress getSocketAddress() {
		return client.getAddress(getCurrentRegisteredServer());
	}

	public boolean triggerUpdate(boolean rehandshake, boolean abbreviated) {
		if (getCurrentRegisteredServer() != null) {
			if (rehandshake) {
				CoapEndpoint endpoint = (CoapEndpoint) client.coap().getServer().getEndpoint(getSocketAddress());
				if (endpoint.getConnector() instanceof DTLSConnector) {
					DTLSConnector connector = (DTLSConnector) endpoint.getConnector();
					if (abbreviated)
						connector.forceResumeAllSessions();
					else
						connector.clearConnectionState();
				}
			}
			client.triggerRegistrationUpdate();
			return true;
		} else {
			return false;
		}
	}

	public ServerIdentity getCurrentRegisteredServer() {
		Map<String, ServerIdentity> registeredServers = client.getRegisteredServers();
		if (registeredServers != null && !registeredServers.isEmpty())
			return registeredServers.values().iterator().next();
		return null;
	}

	public void destroy(boolean deregister) {
		client.destroy(deregister);
	}
}
