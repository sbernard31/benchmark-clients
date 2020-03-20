package org.eclipse.leshan.benchmark.client;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

@Command(name = "leshan-clients-launcher", mixinStandardHelpOptions = true, sortOptions = false, version = "0.1", description = "Launch several LWM2M clients. CoAP and CoAPs with PSK is supported")
public class ClientsLauncherCLI implements Callable<Integer> {

	// LOGGER CONFIGURATION
	static {
		// Define a default logback.configurationFile
		String property = System.getProperty("logback.configurationFile");
		if (property == null) {
			System.setProperty("logback.configurationFile", "logback-config.xml");
		}
	}
	private static final Logger LOG = LoggerFactory.getLogger(ClientsLauncherCLI.class);

	// CLI OPTIONS
	@Option(names = { "-u",
			"--server-url" }, required = true, description = "URL of the LWM2M Server or LWM2M bootstrap server if -b option is used, e.g: coap://localhost:5683. Use coaps to use PSK.")
	private String serverURL;
	@Option(names = { "-n",
			"--number-of-client" }, description = "Number of clients to simulate.\nDefault: ${DEFAULT-VALUE} client.")
	private int nbClients = 1;
	@Option(names = { "-s",
			"--start-time" }, description = "Time to start all clients in seconds.\nDefault: number-of-client*3 seconds.")
	private Integer startTime = null;
	@Option(names = { "-c",
			"--communication-period" }, description = "Number of time between 2 update requests in seconds.\nDefault: ${DEFAULT-VALUE} seconds.")
	private int communicationPeriodInSeconds = 60;
	@Option(names = { "-b", "--bootstrap" }, description = "Use this option to bootstrap instead of register.")
	private boolean bootstrap = false;
	@Option(names = { "-r", "--reconnect-on-update" }, description = "Reconnect/rehandshake on update.")
	private boolean reconnectOnUpdate = false;
	@Option(names = { "-f", "--no-resume" }, description = "Force reconnect/rehandshake on update.")
	private boolean noSessionResumption = false;
	@Option(names = { "-d", "--duration" }, description = "Duration of the simulation in seconds.\nDefault: no limit.")
	private Integer durationInSeconds;
	@Option(names = { "-e",
			"--endpoint-pattern" }, description = "A String.format pattern used to create the client endpoint name from this index number.\nDefault: ${DEFAULT-VALUE}.")
	private String endpointPattern = "LESHAN%08d";
	@Option(names = { "-i",
			"--pskid-pattern" }, description = "A String.format pattern used to create the psk identity from this index number.\nDefault: use --endpoint-pattern.")
	private String pskIdPattern;
	@Option(names = { "-k",
			"--pskkey-pattern" }, description = "A String.format pattern used to create the psk identity from this index number. Value must be an Hexadecimal String.\nDefault ${DEFAULT-VALUE}")
	private String pskKeyPattern = "1234567890ABCDEF%08X";
	@Option(names = { "-a", "--additional-attributes" }, description = "Additional attribute use at registration.")
	private Map<String, String> additionalAttributes;

	@ArgGroup(exclusive = false)
	private Graphite graphite;

	static class Graphite {

		@Option(names = { "-g", "--graphite-report" }, description = "Report to graphite server.")
		boolean activate = false;
		@Option(names = "--graphite-url", description = "Url of graphite server.\nDefault: \"${DEFAULT-VALUE}\".")
		InetSocketAddress serverAddress = new InetSocketAddress("localhost", 2003);
		@Option(names = "--graphite-polling-period", description = "Polling period to push data to graphite in seconds.\nDefault: ${DEFAULT-VALUE} seconds.")
		int pollingPeriodInSec = 5;
	}

	public static void main(String[] args) {
		CommandLine commandLine = new CommandLine(new ClientsLauncherCLI());
		commandLine.registerConverter(InetSocketAddress.class, new ITypeConverter<InetSocketAddress>() {

			@Override
			public InetSocketAddress convert(String value) throws Exception {
				String[] hostAndPort = value.split(":");
				if (hostAndPort.length != 2)
					throw new TypeConversionException("Invalid [host:port] value : " + value);

				return new InetSocketAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
			}
		});
		System.exit(commandLine.execute(args));
	}

	@Override
	public Integer call() throws Exception {
		ClientsLauncher launcher = new ClientsLauncher();

		launcher.setServerURI(serverURL);
		launcher.setNbClients(nbClients);
		startTime = startTime == null ? nbClients * 3 : startTime;
		launcher.setTimeToStart(startTime);
		launcher.setCommunicationPeriod(communicationPeriodInSeconds);
		launcher.setBootstrap(bootstrap);
		launcher.setResumeOnConnect(!noSessionResumption);
		launcher.setReconnectOnUpdate(reconnectOnUpdate);
		launcher.setEndpointPattern(endpointPattern);
		pskIdPattern = pskIdPattern == null ? endpointPattern : pskIdPattern;
		launcher.setPskIdPattern(pskIdPattern);
		launcher.setPskKeyPattern(pskKeyPattern);
		launcher.setAdditionalAttributes(additionalAttributes);
		if (durationInSeconds != null)
			launcher.setTestDurationInSeconds(durationInSeconds);
		if (graphite != null) {
			LOG.info("Reporting to graphite server at {} each {}s.", graphite.serverAddress,
					graphite.pollingPeriodInSec);
			launcher.setGraphiteServerAddress(graphite.serverAddress);
			launcher.setGraphitePollingPeriod(graphite.pollingPeriodInSec);
		}

		// TODO add option for :
		// launcher.setNbUpdatesByMinutes(1700);

		LOG.info("Creating {} client(s) for {} to {}...", nbClients, bootstrap ? "bootstraping" : "registering",
				serverURL);
		launcher.createClients();
		LOG.info("Clients range:\n [{}]", clientsRange());
		LOG.info("Starting clients over ~{}s, then use {}s for communication period ...", startTime,
				communicationPeriodInSeconds);
		launcher.start();

		// Report on shutdown
		Runtime.getRuntime().addShutdownHook(new Thread() {

			@Override
			public void run() {
				launcher.destroy(false);
				LOG.info("Finished");
				launcher.logReport();
			}
		});

		launcher.waitToEnd();
		launcher.destroy(false);
		LOG.info("Finished");
		launcher.logReport();
		return 0;
	}

	private String clientsRange() {
		if (serverURL.startsWith("coaps")) {
			String coapsPattern = endpointPattern + "(" + pskIdPattern + "/" + pskKeyPattern + ")";
			String begin = String.format(coapsPattern, 1, 1, 1);
			if (nbClients > 1) {
				return begin + " .. " + String.format(coapsPattern, nbClients, nbClients, nbClients);
			} else {
				return begin;
			}
		} else {
			String coapPattern = endpointPattern;
			String begin = String.format(coapPattern, 1);
			if (nbClients > 1) {
				return begin + ".." + String.format(coapPattern, nbClients);
			} else {
				return begin;
			}
		}
	}
}
