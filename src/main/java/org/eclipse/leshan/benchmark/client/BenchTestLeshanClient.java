package org.eclipse.leshan.benchmark.client;

import static org.eclipse.leshan.LwM2mId.SECURITY;
import static org.eclipse.leshan.LwM2mId.SERVER;
import static org.eclipse.leshan.client.object.Security.noSec;
import static org.eclipse.leshan.client.object.Security.psk;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.observer.LwM2mClientObserverAdapter;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.StaticModel;
import org.eclipse.leshan.core.request.BindingMode;

public class BenchTestLeshanClient {

	private final AtomicInteger nbRegistrationSuccess = new AtomicInteger();
	private final AtomicInteger nbRegistrationFailure = new AtomicInteger();
	private final AtomicInteger nbRegistrationTimeout = new AtomicInteger();
	private final AtomicInteger nbUpdateSuccess = new AtomicInteger();
	private final AtomicInteger nbUpdateFailure = new AtomicInteger();
	private final AtomicInteger nbUpdateTimeout = new AtomicInteger();
	private final AtomicInteger nbDeregistrationSuccess = new AtomicInteger();
	private final AtomicInteger nbDeregistrationFailure = new AtomicInteger();
	private final AtomicInteger nbDeregistrationTimeout = new AtomicInteger();

	private final boolean secure;

	static {
		List<ObjectModel> objectModels = ObjectLoader.loadDefault();
		objectModels.addAll(ObjectLoader.loadDdfResources(new String[] { "/LWM2M_Software_Management-v1_0.xml" }));
		model = new StaticModel(objectModels);
	}

	private final static LwM2mModel model;

	private LeshanClient client;
	private String endpoint;

	public BenchTestLeshanClient(int id, String serverURI, long lifetimeInSec, final ScheduledExecutorService executor) {

		secure = serverURI.startsWith("coaps");

		// create objects
		ObjectsInitializer initializer = new ObjectsInitializer(model);
		
		// String endpoint;
		if (secure) {
			endpoint = String.format("sec_BenchClient_%d", id);
			initializer.setInstancesForObject(SECURITY, psk(serverURI, 123, endpoint.getBytes(), ("key").getBytes()));
			initializer.setInstancesForObject(SERVER, new Server(123, lifetimeInSec, BindingMode.U, false));

		} else {
			endpoint = String.format("BenchClient_%d", id);
			initializer.setInstancesForObject(SECURITY, noSec(serverURI, 123));
			initializer.setInstancesForObject(SERVER, new Server(123, lifetimeInSec, BindingMode.U, false));
		}
		initializer.setDummyInstancesForObject(5, 9);
		List<LwM2mObjectEnabler> objects = initializer.createAll();

		// build client
		LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
		builder.setObjects(objects);

		NetworkConfig networkConfig = LeshanClientBuilder.createDefaultNetworkConfig();
		networkConfig.set(NetworkConfig.Keys.PREFERRED_BLOCK_SIZE, 1024);

		builder.setSharedExecutor(executor);
		client = builder.build();
	
		client.addObserver(new LwM2mClientObserverAdapter() {

			@Override
			public void onUpdateTimeout(org.eclipse.leshan.client.servers.Server server) {
				nbUpdateTimeout.incrementAndGet();
			}

			@Override
			public void onUpdateSuccess(org.eclipse.leshan.client.servers.Server server, String registrationID) {
				nbUpdateSuccess.incrementAndGet();
			}

			@Override
			public void onUpdateFailure(org.eclipse.leshan.client.servers.Server server, ResponseCode responseCode,
					String errorMessage) {
				nbUpdateFailure.incrementAndGet();
			}

			@Override
			public void onRegistrationTimeout(org.eclipse.leshan.client.servers.Server server) {
				nbRegistrationTimeout.incrementAndGet();
			}

			@Override
			public void onRegistrationSuccess(org.eclipse.leshan.client.servers.Server server, String registrationID) {
				nbRegistrationSuccess.incrementAndGet();
			}

			@Override
			public void onRegistrationFailure(org.eclipse.leshan.client.servers.Server server,
					ResponseCode responseCode, String errorMessage) {
				nbRegistrationFailure.incrementAndGet();

			}

			@Override
			public void onDeregistrationTimeout(org.eclipse.leshan.client.servers.Server server) {
				nbDeregistrationTimeout.incrementAndGet();
			}

			@Override
			public void onDeregistrationSuccess(org.eclipse.leshan.client.servers.Server server,
					String registrationID) {
				nbDeregistrationSuccess.incrementAndGet();
			}

			@Override
			public void onDeregistrationFailure(org.eclipse.leshan.client.servers.Server server,
					ResponseCode responseCode, String errorMessage) {
				nbDeregistrationFailure.incrementAndGet();
			}

			@Override
			public void onBootstrapTimeout(org.eclipse.leshan.client.servers.Server bsserver) {
			}

			@Override
			public void onBootstrapSuccess(org.eclipse.leshan.client.servers.Server bsserver) {
			}

			@Override
			public void onBootstrapFailure(org.eclipse.leshan.client.servers.Server bsserver, ResponseCode responseCode,
					String errorMessage) {
			}
		});
	}

	public void start() {
		client.start();
	}

	public void stop(boolean deregister) {
		client.stop(deregister);
	}

	public InetSocketAddress getSocketAddress() {
		return client.getAddress();
	}

	public boolean triggerUpdate(boolean rehandshake, boolean abbreviated) {
		if (client.getRegistrationId() != null) {
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

	public int getNbRegistrationSuccess() {
		return nbRegistrationSuccess.get();
	}

	public int getNbRegistrationFailure() {
		return nbRegistrationFailure.get();
	}

	public int getNbRegistrationTimeout() {
		return nbRegistrationTimeout.get();
	}

	public int getNbUpdateSuccess() {
		return nbUpdateSuccess.get();
	}

	public int getNbUpdateFailure() {
		return nbUpdateFailure.get();
	}

	public int getNbUpdateTimeout() {
		return nbUpdateTimeout.get();
	}

	public int getNbDeregistrationSuccess() {
		return nbDeregistrationSuccess.get();
	}

	public int getNbDeregistrationFailure() {
		return nbDeregistrationFailure.get();
	}

	public int getNbDeregistrationTimeout() {
		return nbDeregistrationTimeout.get();
	}

	public void destroy(boolean deregister) {
		client.destroy(deregister);
	}
}
