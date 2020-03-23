# benchmark-clients
A Command line tool base on https://www.eclipse.org/leshan/ to simulate a fleet of LWM2M clients.

Get the [last version of benchmark-clients-*.jar](https://github.com/sbernard31/benchmark-clients/releases/latest/).

To launch it : 
```  
`java -jar benchmark-clients-*.jar -u coap://leshan.eclipseprojects.io`
```
**(Please :pray:! Do not use this tool to kill(Dos) the leshan sandbox :sweat_smile:**)

Here is the command option :

```
Usage: leshan-clients-launcher [[-g] [--graphite-url=<serverAddress>]
                               [--graphite-polling-period=<pollingPeriodInSec>]]
                                [-bfhrV] [-c=<communicationPeriodInSeconds>]
                               [-d=<durationInSeconds>] [-e=<endpointPattern>]
                               [-i=<pskIdPattern>] [-k=<pskKeyPattern>]
                               [-n=<nbClients>] [-s=<startTime>] -u=<serverURL>
                               [-a=<String=String>]...
Launch several LWM2M clients. CoAP and CoAPs with PSK is supported
  -u, --server-url=<serverURL>
                          URL of the LWM2M Server or LWM2M bootstrap server if
                            -b option is used, e.g: coap://localhost:5683. Use
                            coaps to use PSK.
  -n, --number-of-client=<nbClients>
                          Number of clients to simulate.
                          Default: 1 client.
  -s, --start-time=<startTime>
                          Time to start all clients in seconds.
                          Default: number-of-client*3 seconds.
  -c, --communication-period=<communicationPeriodInSeconds>
                          Number of time between 2 update requests in seconds.
                          Default: 60 seconds.
  -b, --bootstrap         Use this option to bootstrap instead of register.
  -r, --reconnect-on-update
                          Try to resume connection when it's possible.
  -f, --no-resume         Do not try to resume session always do a full
                            handshake.
  -d, --duration=<durationInSeconds>
                          Duration of the simulation in seconds.
                          Default: no limit.
  -e, --endpoint-pattern=<endpointPattern>
                          A String.format pattern used to create the client
                            endpoint name from this index number.
                          Default: LESHAN%08d.
  -i, --pskid-pattern=<pskIdPattern>
                          A String.format pattern used to create the psk
                            identity from this index number.
                          Default: use --endpoint-pattern.
  -k, --pskkey-pattern=<pskKeyPattern>
                          A String.format pattern used to create the psk
                            identity from this index number. Value must be an
                            Hexadecimal String.
                          Default 1234567890ABCDEF%08X
  -a, --additional-attributes=<String=String>
                          Additional attribute use at registration.
  -g, --graphite-report   Report to graphite server.
      --graphite-url=<serverAddress>
                          Url of graphite server.
                          Default: "localhost/127.0.0.1:2003".
      --graphite-polling-period=<pollingPeriodInSec>
                          Polling period to push data to graphite in seconds.
                          Default: 5 seconds.
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.

```

You can report stats on [graphite](https://graphiteapp.org/).  
Using the ugly default UI, this looks like this :  
![graphite_leshan](https://user-images.githubusercontent.com/840294/76007822-f6062480-5f0e-11ea-9ea7-9ec0416be492.png)

To build it :

```
mvn clean install
```
then run it :

```
java -jar target/benchmark-clients-*-SNAPSHOT-jar-with-dependencies.jar -u coap://leshan.eclipseprojects.io
```
