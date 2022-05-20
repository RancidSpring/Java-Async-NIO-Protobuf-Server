# Java-Async-NIO-Protobuf-Server
Java Asynchronous Nonblocking Protobuf Server.

# Execution
Maven was used to build the server. The execution was tested with jdk-17. 

1) First step is to compile:

``` mvn compile```

2) Then we execute the main:

``` mvn exec:java -Dexec.mainClass="MyNettyServer" ```


# Specification
The goal is to implement a Java server that can efficiently and quickly handle multiple client connections. Each client sends messages in a Protobuf format. Possible messages types and message structure is described in the Protobuf [scheme](/src/main/proto/measurements.proto). Client sends a request, whic can be either "postWord" or "getCount".
- **postWords**: message contains an array of bytes containing words to store on the server.
- **getCount**: does not carry any information from the client. Instead, it is an indicator for the server to stop adding the words, reset the internal data structure and respond to the client who send the request with a number of unique words from that structure.

# Implementation
For the implementation was chosen a framework called Netty.

_Netty is an asynchronous event-driven network application framework for rapid development of maintainable high performance protocol servers & clients._ 

Each client connection is handled in a different thread asynchronously. **ConcurrentHashMap** data structure was chosen to store the incoming data and prevent conflicts.

The data coming from clients is also archived with Gzip, so the implementation uses GZIPInputStream to decompress the data.

Note: The implementation relies that each time a client sends a message, there are usually 4 bytes before the message itself representing the size of the message in the number of bytes. The same principle applies to the message that goes back to the client.



