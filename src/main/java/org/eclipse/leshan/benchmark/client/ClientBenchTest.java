package org.eclipse.leshan.benchmark.client;

public class ClientBenchTest {

	public static void main(String[] args) throws InterruptedException {
		ClientsLauncher launcher = new ClientsLauncher();
		
		launcher.setNbClients(4000);
		launcher.setNbUpdatesByMinutes(1700);
		launcher.setTestDurationInSeconds(60);
		launcher.setTimeToRegistrerAllClientsInSeconds(30);
		
//		int nbClients = 3;
//		launcher.setNbClients(nbClients);
//		launcher.setNbUpdatesByMinutes(nbClients*2);
//		launcher.setTestDurationInSeconds(40);
//		launcher.setTimeToRegistrerAllClientsInSeconds(10);
//
//		launcher.setNbClients(1);
//		launcher.setNbUpdatesByMinutes(3);
//		launcher.setTestDurationInSeconds(10);
//		launcher.setTimeToRegistrerAllClientsInSeconds(2);
		
		
		//launcher.setServerURI("coap://127.0.0.1:5683");
		launcher.setServerURI("coaps://127.0.0.1:5684");
		//launcher.setServerURI("coap://127.0.0.1:5685");
		//launcher.setServerURI("coaps://127.0.0.1:5686");

		System.out.println("Initializing ...");
		launcher.createClients();
		System.out.println("Initialized");
		
		System.out.println("starting ...");
		launcher.start();
		System.out.println("started.");

		launcher.waitToEnd();
		System.out.println("Finished");
		System.out.println(launcher.getReport());
	}
}
