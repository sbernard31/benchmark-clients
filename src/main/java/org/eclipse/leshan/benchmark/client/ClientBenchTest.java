package org.eclipse.leshan.benchmark.client;

public class ClientBenchTest {

	public static void main(String[] args) throws InterruptedException {
		ClientsLauncher launcher = new ClientsLauncher();
		
		launcher.setNbClients(2000);
		launcher.setNbUpdatesByMinutes(1700);
		launcher.setTestDurationInSeconds(120);
		launcher.setTimeToRegistrerAllClientsInSeconds(30);
		
//		launcher.setNbClients(1);
//		launcher.setNbUpdatesByMinutes(1);
//		launcher.setTestDurationInSeconds(30);
//		launcher.setTimeToRegistrerAllClientsInSeconds(2);
		
		
		//launcher.setServerURI("coap://127.0.0.1:5683");
		//launcher.setServerURI("coaps://127.0.0.1:5684");
		//launcher.setServerURI("coap://127.0.0.1:5685");
		launcher.setServerURI("coaps://127.0.0.1:5686");

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
