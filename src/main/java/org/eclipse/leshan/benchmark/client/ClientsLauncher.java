package org.eclipse.leshan.benchmark.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.leshan.util.NamedThreadFactory;

public class ClientsLauncher {

	private int nbclients;
	private int timeToRegistrerAllClientsInSeconds;
	// private int nbRegistrationByMinutes;
	private int nbUpdatesByMinutes;
	private long testDurationInSeconds;
	private String serverURI;

	private List<BenchTestLeshanClient> clients;
	private CountDownLatch testEnd = new CountDownLatch(1);
	private ScheduledExecutorService executor = Executors
			.newSingleThreadScheduledExecutor(new NamedThreadFactory("Clients Launcher"));
	private ScheduledExecutorService executorForClients = Executors.newScheduledThreadPool(400,
			new NamedThreadFactory("coap+dtls connector"));
	private int currentClientIndex = 0;

	public void setNbClients(int nbclients) {
		this.nbclients = nbclients;
	}

	public void setTimeToRegistrerAllClientsInSeconds(int timeToRegistrerAllClientsInSeconds) {
		this.timeToRegistrerAllClientsInSeconds = timeToRegistrerAllClientsInSeconds;
	}

	public void setNbUpdatesByMinutes(int nbUpdatesByMinutes) {
		this.nbUpdatesByMinutes = nbUpdatesByMinutes;
	}

	public void setTestDurationInSeconds(long testDurationInSeconds) {
		this.testDurationInSeconds = testDurationInSeconds;
	}

	public void setServerURI(String serverURI) {
		this.serverURI = serverURI;
	}

	public void createClients() {
		clients = new ArrayList<>(nbclients);
		for (int i = 0; i < nbclients; i++) {
			clients.add(new BenchTestLeshanClient(i, serverURI, testDurationInSeconds, executorForClients));
		}
	}

	public void start() throws InterruptedException {
		executor.schedule(new Runnable() {

			@Override
			public void run() {
				for (BenchTestLeshanClient client : clients) {
					client.stop(false);
				}
				testEnd.countDown();
			}
		}, testDurationInSeconds, TimeUnit.SECONDS);

		clients.get(0).start();
		if (nbclients > 1) {
			int timeBetweenLaunch = timeToRegistrerAllClientsInSeconds / (nbclients - 1);
			for (int i = 1; i < nbclients; i++) {
				Thread.sleep(timeBetweenLaunch * 1000);
				clients.get(i).start();
			}
		}

		int timebetween2lifetime = (int) (60000d / nbUpdatesByMinutes);
		executor.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				while (!clients.get(currentClientIndex).triggerUpdate(true, true)) {
					currentClientIndex = (currentClientIndex + 1) % nbclients;
				}
				;
				currentClientIndex = (currentClientIndex + 1) % nbclients;
			}
		}, timebetween2lifetime, timebetween2lifetime, TimeUnit.MILLISECONDS);

	}

	public void waitToEnd() throws InterruptedException {
		testEnd.await();
		for (int i = 0; i < nbclients; i++) {
			clients.get(i).destroy(false);
		}
		executor.shutdown();
	}

	public String getReport() {
		int nbRegistrationSuccess = 0;
		int nbRegistrationFailure = 0;
		List<BenchTestLeshanClient> clientWithRegFailure = new ArrayList<>();
		int nbRegistrationTimeout = 0;
		List<BenchTestLeshanClient> clientWithRegTimeout = new ArrayList<>();

		int nbUpdateSuccess = 0;
		int nbUpdateFailure = 0;
		List<BenchTestLeshanClient> clientWithUpdateFailure = new ArrayList<>();
		int nbUpdateTimeout = 0;
		List<BenchTestLeshanClient> clientWithUpdateTimeout = new ArrayList<>();
		int nbDeregistrationSuccess = 0;
		int nbDeregistrationFailure = 0;
		List<BenchTestLeshanClient> clientWithDeregFailure = new ArrayList<>();
		int nbDeregistrationTimeout = 0;
		List<BenchTestLeshanClient> clientWithDeregTimeout = new ArrayList<>();

		for (BenchTestLeshanClient client : clients) {
			nbRegistrationSuccess += client.getNbRegistrationSuccess();
			nbRegistrationFailure += client.getNbRegistrationFailure();
			nbRegistrationTimeout += client.getNbRegistrationTimeout();
			nbUpdateSuccess += client.getNbUpdateSuccess();
			nbUpdateFailure += client.getNbUpdateFailure();
			nbUpdateTimeout += client.getNbUpdateTimeout();
			nbDeregistrationSuccess += client.getNbDeregistrationSuccess();
			nbDeregistrationFailure += client.getNbDeregistrationFailure();
			nbDeregistrationTimeout += client.getNbDeregistrationTimeout();

			if (client.getNbRegistrationFailure() > 0)
				clientWithRegFailure.add(client);
			if (client.getNbRegistrationTimeout() > 0)
				clientWithRegTimeout.add(client);

			if (client.getNbUpdateFailure() > 0)
				clientWithUpdateFailure.add(client);
			if (client.getNbUpdateTimeout() > 0)
				clientWithUpdateTimeout.add(client);

			if (client.getNbDeregistrationFailure() > 0)
				clientWithDeregFailure.add(client);
			if (client.getNbDeregistrationTimeout() > 0)
				clientWithDeregTimeout.add(client);
		}

		StringBuilder b = new StringBuilder();
		b.append("=======================================\n");
		b.append("Registration : ");
		b.append(nbRegistrationSuccess);
		b.append(" success, ");
		b.append(nbRegistrationFailure);
		b.append(" failure( ");
//		for (BenchTestLeshanClient client : clientWithRegFailure) {
//			b.append(client.getSocketAddress().getPort());
//			b.append(" ");
//		}
		b.append(")");
		b.append(nbRegistrationTimeout);
		b.append(" Timeout( ");
//		for (BenchTestLeshanClient client : clientWithRegTimeout) {
//			b.append(client.getSocketAddress().getPort());
//			b.append(" ");
//		}
		b.append(").\n");

		b.append("Update : ");
		b.append(nbUpdateSuccess);
		b.append(" success, ");
		b.append(nbUpdateFailure);
		b.append(" failure( ");
//		for (BenchTestLeshanClient client : clientWithUpdateFailure) {
//			b.append(client.getSocketAddress().getPort());
//			b.append(" ");
//		}
		b.append(")");
		b.append(nbUpdateTimeout);
		b.append(" Timeout( ");
//		for (BenchTestLeshanClient client : clientWithUpdateTimeout) {
//			b.append(client.getSocketAddress().getPort());
//			b.append(" ");
//		}
		b.append(").\n");

		b.append("Deregistration : ");
		b.append(nbDeregistrationSuccess);
		b.append(" success, ");
		b.append(nbDeregistrationFailure);
		b.append(" failure( ");
//		for (BenchTestLeshanClient client : clientWithDeregFailure) {
//			b.append(client.getSocketAddress().getPort());
//			b.append(" ");
//		}
		b.append(")");
		b.append(nbDeregistrationTimeout);
		b.append(" Timeout( ");
//		for (BenchTestLeshanClient client : clientWithDeregTimeout) {
//			b.append(client.getSocketAddress().getPort());
//			b.append(" ");
//		}
		b.append(").\n");
		b.append("=======================================\n");

		executorForClients.shutdown();
		
		return b.toString();
	}
}
