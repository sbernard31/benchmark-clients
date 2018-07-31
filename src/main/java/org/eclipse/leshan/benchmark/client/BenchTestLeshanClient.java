package org.eclipse.leshan.benchmark.client;

import static org.eclipse.leshan.LwM2mId.SECURITY;
import static org.eclipse.leshan.LwM2mId.SERVER;
import static org.eclipse.leshan.client.object.Security.noSec;
import static org.eclipse.leshan.client.object.Security.psk;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.CoapEndpoint.CoapEndpointBuilder;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.observe.ObservationStore;
import org.eclipse.californium.elements.UdpEndpointContextMatcher;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.observer.LwM2mClientObserver;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.servers.DmServerInfo;
import org.eclipse.leshan.client.servers.ServerInfo;
import org.eclipse.leshan.core.californium.EndpointFactory;
import org.eclipse.leshan.core.californium.Lwm2mEndpointContextMatcher;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.request.BindingMode;

import eu.javaspecialists.tjsn.concurrency.stripedexecutor.StripedExecutorService;

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
		// create custom model
		List<ObjectModel> objectModels = ObjectLoader.loadDefault();
		objectModels.addAll(ObjectLoader.loadDdfResources(new String[] { "/LWM2M_Software_Management-v1_0.xml" }));
		try (InputStream input = ClassLoader.getSystemResourceAsStream("10250.json")) {
			objectModels.addAll(ObjectLoader.loadJsonStream(input));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load object 10250");
		}

		model = new LwM2mModel(objectModels);
	}

	private final static LwM2mModel model;

	private LeshanClient client;
	private String endpoint;

	public BenchTestLeshanClient(int id, String serverURI, long lifetimeInSec,
			final ScheduledExecutorService executor) {
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
		List<LwM2mObjectEnabler> objects = initializer.createMandatory();
		objects.addAll(initializer.create(5, 9, 10250));

		// build client
		LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
		if (secure)
			builder.disableUnsecuredEndpoint();
		else
			builder.disableSecuredEndpoint();
		builder.setObjects(objects);

		builder.setEndpointFactory(new EndpointFactory() {

			@Override
			public CoapEndpoint createUnsecuredEndpoint(InetSocketAddress address, NetworkConfig coapConfig,
					ObservationStore store) {
				CoapEndpointBuilder builder = new CoapEndpoint.CoapEndpointBuilder();
				builder.setInetSocketAddress(address);
				builder.setNetworkConfig(coapConfig);
				builder.setEndpointContextMatcher(new UdpEndpointContextMatcher());
				return builder.build();
			}

			@Override
			public CoapEndpoint createSecuredEndpoint(DtlsConnectorConfig dtlsConfig, NetworkConfig coapConfig,
					ObservationStore store) {
				DTLSConnector dtlsConnector = new DTLSConnector(dtlsConfig);
				if (executor != null)
					dtlsConnector.setExecutor(new StripedExecutorService(executor));
				
				CoapEndpointBuilder builder = new CoapEndpoint.CoapEndpointBuilder();
				builder.setConnector(dtlsConnector);
				builder.setNetworkConfig(coapConfig);
				builder.setEndpointContextMatcher(new Lwm2mEndpointContextMatcher());
				return builder.build();
			}
		});

		NetworkConfig networkConfig = LeshanClientBuilder.createDefaultNetworkConfig();
		networkConfig.set(NetworkConfig.Keys.PREFERRED_BLOCK_SIZE, 1024);
		
		
		
		client = builder.build();
		if (executor != null)
			client.getCoapServer().setExecutor(executor);
		
		// For testing block cancelling

//		System.out.println(client.getCoapServer().getEndpoints().size());
//		client.getCoapServer().getEndpoints().get(0).addInterceptor(new MessageInterceptor() {
//			
//			int i = 0;
//			int j = 0;
//			
//			@Override
//			public void sendResponse(Response response) {
//				i++;
//				System.out.println(i);
//				if (i== 60) {
//					try {
//						Thread.sleep(5000);
//						j++;
//						if (j > 0)
//							client.destroy(true);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//				}
//			}
//			
//			@Override
//			public void sendRequest(Request request) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void sendEmptyMessage(EmptyMessage message) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void receiveResponse(Response response) {
//			}
//			
//			@Override
//			public void receiveRequest(Request request) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void receiveEmptyMessage(EmptyMessage message) {
//			}
//		});

		client.addObserver(new LwM2mClientObserver() {

			@Override
			public void onUpdateTimeout(DmServerInfo server) {
				nbUpdateTimeout.incrementAndGet();
			}

			@Override
			public void onUpdateSuccess(DmServerInfo server, String registrationID) {
				nbUpdateSuccess.incrementAndGet();
			}

			@Override
			public void onUpdateFailure(DmServerInfo server, ResponseCode responseCode, String errorMessage) {
				nbUpdateFailure.incrementAndGet();
			}

			@Override
			public void onRegistrationTimeout(DmServerInfo server) {
				nbRegistrationTimeout.incrementAndGet();
			}

			@Override
			public void onRegistrationSuccess(DmServerInfo server, String registrationID) {
				nbRegistrationSuccess.incrementAndGet();
			}

			@Override
			public void onRegistrationFailure(DmServerInfo server, ResponseCode responseCode, String errorMessage) {
				nbRegistrationFailure.incrementAndGet();

			}

			@Override
			public void onDeregistrationTimeout(DmServerInfo server) {
				nbDeregistrationTimeout.incrementAndGet();
			}

			@Override
			public void onDeregistrationSuccess(DmServerInfo server, String registrationID) {
				nbDeregistrationSuccess.incrementAndGet();
			}

			@Override
			public void onDeregistrationFailure(DmServerInfo server, ResponseCode responseCode, String errorMessage) {
				nbDeregistrationFailure.incrementAndGet();
			}

			@Override
			public void onBootstrapTimeout(ServerInfo bsserver) {
			}

			@Override
			public void onBootstrapSuccess(ServerInfo bsserver) {
			}

			@Override
			public void onBootstrapFailure(ServerInfo bsserver, ResponseCode responseCode, String errorMessage) {
			}
		});
	}

	public void start() {
		client.start();
	}

	public void stop() {
		client.stop(false);
	}
	
	public InetSocketAddress getSocketAddress() {
		if (secure) {
			return client.getSecuredAddress();
		}else {
			return client.getUnsecuredAddress();
		}
	}

	public boolean triggerUpdate(boolean rehandshake, boolean abbreviated) {
		if (client.getRegistrationId() != null) {
			if (rehandshake) {
				CoapEndpoint endpoint = (CoapEndpoint) client.getCoapServer().getEndpoint(getSocketAddress());
				DTLSConnector connector = (DTLSConnector)endpoint.getConnector();
				if (abbreviated)
					connector.forceResumeAllSessions();
				else
					connector.clearConnectionState();
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

	public void destroy() {
		client.destroy(false);
	}
}
