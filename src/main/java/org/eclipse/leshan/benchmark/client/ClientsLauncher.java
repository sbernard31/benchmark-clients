package org.eclipse.leshan.benchmark.client;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.engine.DefaultRegistrationEngineFactory;
import org.eclipse.leshan.util.Hex;
import org.eclipse.leshan.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Slf4jReporter.LoggingLevel;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;

public class ClientsLauncher {

	private static final Logger LOG = LoggerFactory.getLogger(BenchClient.class);

	// Configuration
	private int nbclients = 1;
	private Integer timeToStartAllClientInS;
	// Could be null if communicationPeriodInSeconds is used
	private Integer nbUpdatesByMinutes;
	// Could be null if nbUpdatesByMinutes is used
	private Integer communicationPeriodInSeconds;
	// Could be null if test should never ends
	private Long testDurationInSeconds;
	// TRUE if device should bootstrap, FALSE if it should register without
	// bootstrap
	private boolean bootstrap = false;
	private boolean reconnectOnUpdate = false;
	private boolean resumeOnConnect = true;
	// LWM2M bootstrap server or LWM2M server URL
	private String serverURI;
	private InetSocketAddress graphiteServerAddress;
	private int graphitePollingPeriodInSec;
	private String endpointPattern;
	private String pskKeyPattern;
	private String pskIdPattern;

	// metric registry
	private MetricRegistry registry = new MetricRegistry();

	// Thread configuration
	private CountDownLatch testEnd = new CountDownLatch(1);
	private ScheduledExecutorService executor = Executors
			.newSingleThreadScheduledExecutor(new NamedThreadFactory("Clients Launcher"));
	private ScheduledExecutorService executorForClients = Executors.newScheduledThreadPool(400,
			new NamedThreadFactory("coap+dtls connector"));

	// Internal state
	private List<BenchClient> clients;
	private int currentClientIndex = 0;
	private Slf4jReporter logReporter;

	private Map<String, String> additionalAttributes;

	public void setNbClients(int nbclients) {
		this.nbclients = nbclients;
	}

	public void setTimeToStart(int timeToStartAllClientInSeconds) {
		this.timeToStartAllClientInS = timeToStartAllClientInSeconds;
	}

	public void setNbUpdatesByMinutes(int nbUpdatesByMinutes) {
		this.nbUpdatesByMinutes = nbUpdatesByMinutes;
		this.communicationPeriodInSeconds = null;
	}

	public void setCommunicationPeriod(int communicationPeriodInSeconds) {
		this.communicationPeriodInSeconds = communicationPeriodInSeconds;
		this.nbUpdatesByMinutes = null;
	}

	public void setTestDurationInSeconds(long testDurationInSeconds) {
		this.testDurationInSeconds = testDurationInSeconds;
	}

	public void setReconnectOnUpdate(boolean reconnectOnUpdate) {
		this.reconnectOnUpdate = reconnectOnUpdate;
	}

	public void setResumeOnConnect(boolean resumeOnConnect) {
		this.resumeOnConnect = resumeOnConnect;
	}

	public void setBootstrap(boolean bootstrap) {
		this.bootstrap = bootstrap;
	}

	public void setServerURI(String serverURI) {
		this.serverURI = serverURI;
	}

	public void setGraphiteServerAddress(InetSocketAddress graphiteServerAddress) {
		this.graphiteServerAddress = graphiteServerAddress;
	}

	public void setGraphitePollingPeriod(int graphitePollingPeriodInSec) {
		this.graphitePollingPeriodInSec = graphitePollingPeriodInSec;
	}

	public void setEndpointPattern(String endpointPattern) {
		this.endpointPattern = endpointPattern;
	}

	public void setPskIdPattern(String pskIdPattern) {
		this.pskIdPattern = pskIdPattern;
	}

	public void setPskKeyPattern(String pskKeyPattern) {
		this.pskKeyPattern = pskKeyPattern;
	}

	public void setAdditionalAttributes(Map<String, String> additionalAttributes) {
		this.additionalAttributes = additionalAttributes;
	}

	public void createClients() {
		clients = new ArrayList<>(nbclients);
		for (int i = 1; i <= nbclients; i++) {
			clients.add(createClient(i));
		}
	}

	public BenchClient createClient(int i) {
		String endpoint = String.format(endpointPattern, i);
		LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
		if (additionalAttributes != null) {
			HashMap<String, String> attrs = new HashMap<>();
			for (Entry<String, String> entry : additionalAttributes.entrySet()) {
				attrs.put(String.format(entry.getKey(), i), String.format(entry.getValue(), i));
			}
			builder.setAdditionalAttributes(attrs);
		}
		builder.setSharedExecutor(executorForClients);

		// Configure Registration Engine
		DefaultRegistrationEngineFactory engineFactory = new DefaultRegistrationEngineFactory();
		if (communicationPeriodInSeconds != null)
			engineFactory.setCommunicationPeriod(communicationPeriodInSeconds * 1000);
		engineFactory.setRetryWaitingTimeInMs(30000);
		engineFactory.setReconnectOnUpdate(reconnectOnUpdate);
		engineFactory.setResumeOnConnect(resumeOnConnect);
		builder.setRegistrationEngineFactory(engineFactory);

		long lifetime = Math.max(testDurationInSeconds == null ? 0 : testDurationInSeconds, 300);
		return new BenchClient(builder, serverURI, bootstrap, String.format(pskIdPattern, i),
				Hex.decodeHex(String.format(pskKeyPattern, i).toCharArray()), lifetime, registry);
	}

	public void start() throws InterruptedException {
		logReporter = Slf4jReporter.forRegistry(registry).outputTo(LOG).withLoggingLevel(LoggingLevel.INFO)
				.convertRatesTo(TimeUnit.SECONDS).convertDurationsTo(TimeUnit.MILLISECONDS).build();

		if (graphiteServerAddress != null) {
			final Graphite graphite = new Graphite(graphiteServerAddress);
			final GraphiteReporter reporter = GraphiteReporter.forRegistry(registry).convertRatesTo(TimeUnit.SECONDS)
					.convertDurationsTo(TimeUnit.MILLISECONDS).filter(MetricFilter.ALL).build(graphite);
			reporter.start(graphitePollingPeriodInSec, TimeUnit.SECONDS);
		}

		// Plan the end of the test if needed
		if (testDurationInSeconds != null)
			executor.schedule(new Runnable() {

				@Override
				public void run() {
					for (BenchClient client : clients) {
						client.stop(true);
					}
					testEnd.countDown();
				}
			}, testDurationInSeconds, TimeUnit.SECONDS);

		// Start client
		clients.get(0).start();
		if (nbclients > 1) {
			executor.submit(new Runnable() {

				@Override
				public void run() {
					int timeBetweenLaunch = timeToStartAllClientInS / (nbclients - 1);
					boolean interrupted = false;
					for (int i = 1; i < nbclients && !interrupted; i++) {
						try {
							Thread.sleep(timeBetweenLaunch * 1000);
						} catch (InterruptedException e) {
							interrupted = true;
						}
						clients.get(i).start();
					}
				}
			});
		}

		// Manually send update if needed
		if (nbUpdatesByMinutes != null) {
			int timebetween2lifetime = (int) (60000d / nbUpdatesByMinutes);
			executor.scheduleAtFixedRate(new Runnable() {

				@Override
				public void run() {
					while (!clients.get(currentClientIndex).triggerUpdate(true, true)) {
						currentClientIndex = (currentClientIndex + 1) % nbclients;
					}
					currentClientIndex = (currentClientIndex + 1) % nbclients;
				}
			}, timebetween2lifetime, timebetween2lifetime, TimeUnit.MILLISECONDS);
		}
	}

	public void waitToEnd() throws InterruptedException {
		testEnd.await();
	}

	public boolean waitToEnd(long timeoutInSec) throws InterruptedException {
		if (testEnd.await(timeoutInSec, TimeUnit.SECONDS)) {
			for (int i = 0; i < nbclients; i++) {
				clients.get(i).destroy(true);
			}
			return true;
		} else {
			return false;
		}
	}

	public void destroy(boolean deregister) {
		for (BenchClient client : clients) {
			client.destroy(deregister);
		}
		executorForClients.shutdown();
		executor.shutdown();
	}

	public void logReport() {
		logReporter.report();
	}
}
