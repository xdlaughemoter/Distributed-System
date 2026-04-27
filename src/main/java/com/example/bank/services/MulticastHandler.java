package com.example.bank.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableAsync
public class MulticastHandler {

    @Value("${app.nodename}")
    private String nodeName;
    private final String GROUP_ADDRESS = "230.0.0.0";
    private final int PORT = 4446;
    private final HashingService hashingService = new HashingService();
    private final RestClient restClient; // Define it here
    public MulticastHandler(RestClient restClient) {
        this.restClient = restClient;
        mapper = new ObjectMapper();
        logger.info("Reading json on init...");
        // Load IP Addresses
        if (ipFile.exists()) {
            try {
                Map<Integer, String> loadedIps = mapper.readValue(ipFile, new TypeReference<Map<Integer, String>>() {});
                this.ipAddresses.putAll(loadedIps);
                System.out.println("Loaded IP addresses from file.");
            } catch (IOException e) {
                System.err.println("Could not parse ipAddresses.json: " + e.getMessage());
            }
        }

        if (nodeFile.exists()) {
            try {
                Map<Integer, String> loadedNodes = mapper.readValue(nodeFile, new TypeReference<Map<Integer, String>>() {});
                this.fileToNodeIP.putAll(loadedNodes);
                System.out.println("Loaded node mappings from file.");
            } catch (IOException e) {
                System.err.println("Could not parse nodeToFile.json: " + e.getMessage());
            }
        }

    }



    public Map<Integer, String> getIpAddresses() {
        return ipAddresses;
    }

    public Map<Integer, String> getFileToNodeIP() {
        return fileToNodeIP;
    }
    public void insertIpAddress(int hash, String ipAddr){
        logger.info("Insert IP adress "+ ipAddresses);
        ipAddresses.put(hash, ipAddr);
        try {
            // writeValue(File, Object) serializes and saves
            mapper.writerWithDefaultPrettyPrinter().writeValue(ipFile, ipAddresses);
            System.out.println("JSON written successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public boolean insertFileToNodeIP(int hash, String ipAddr){
        if (fileToNodeIP.containsKey(hash)){
            return false;
        }
        logger.info("File added with owner "+ ipAddr);
        fileToNodeIP.put(hash, ipAddr);
        try {
            // writeValue(File, Object) serializes and saves
            mapper.writerWithDefaultPrettyPrinter().writeValue(nodeFile, fileToNodeIP);
            System.out.println("JSON written successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
    public void removeIpAddress(int hash){
        logger.info("Remove ip adress of hash "+ hash);
        ipAddresses.remove(hash);
        try {
            // writeValue(File, Object) serializes and saves
            mapper.writerWithDefaultPrettyPrinter().writeValue(ipFile, ipAddresses);
            System.out.println("JSON written successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public int getPreviousHashofNodeHash(int hashNode){
        int lowerHash=0;
        for (Integer i : ipAddresses.keySet()) {
            if(lowerHash<i && i<hashNode){
                lowerHash=i;
            }
        }
        if(lowerHash == 0){
            lowerHash = ipAddresses.keySet().stream()
                    .max(Integer::compare)
                    .orElse(0);
        }
        return lowerHash;
    }
    public int getNextHashofNodeHash(int hashNode){
        int higherHash=Integer.MAX_VALUE;
        for (Integer i : ipAddresses.keySet()) {
            if(hashNode<i && i<higherHash){
                higherHash=i;
            }
        }
        if(higherHash == Integer.MAX_VALUE){
            higherHash =  ipAddresses.keySet().stream()
                    .min(Integer::compare)
                    .orElse(0);
        }
        return higherHash;
    }
    public void removeIPFromFileToNode(int hash){
        logger.info("Change ownership of files of current node to previous node");
        int previousHash = getPreviousHashofNodeHash(hash);
        String ipPrevious = ipAddresses.get(previousHash);
        String ipCurrent = ipAddresses.get(hash);
        logger.info("IP previous node "+ ipPrevious);
        logger.info("IP current node "+ ipCurrent);
        fileToNodeIP.replaceAll((key, value) -> value.equals(ipCurrent) ? ipPrevious : value);
        try {
            // writeValue(File, Object) serializes and saves
            mapper.writerWithDefaultPrettyPrinter().writeValue(nodeFile, fileToNodeIP);
            System.out.println("JSON written successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void removeFileToNodeIP(int hash){
        fileToNodeIP.remove(hash);
        try {
            // writeValue(File, Object) serializes and saves
            mapper.writerWithDefaultPrettyPrinter().writeValue(nodeFile, fileToNodeIP);
            System.out.println("JSON written successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final Map<Integer, String> ipAddresses = new ConcurrentHashMap<>();
    private final Map<Integer, String> fileToNodeIP = new ConcurrentHashMap<>();
    private File ipFile = new File("ipAddresses.json");
    private File nodeFile = new File("fileToNodeIP.json");
    private ObjectMapper mapper;
    public static final Logger logger = LoggerFactory.getLogger(MulticastHandler.class);


    public void sendMulticast(String message) {
        logger.info("Send multicast with message: "+ message);
        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
            byte[] buf = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, PORT);
            socket.send(packet);
            System.out.println(">>> Sent: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 1. Start the listener in a BACKGROUND thread so it doesn't block Spring
        logger.info("Application ready");
        new Thread(this::receiveMessages).start();

        // 2. Now run your discovery logic
        discoverNodes();
    }

    public void discoverNodes(){
        try {
            logger.info("Discover nodes");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Filters out 127.0.0.1 and inactive interfaces
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Check for IPv4 address
                    if (addr.getHostAddress().contains(":")) continue;

                    System.out.println(iface.getDisplayName() + " IP: " + addr.getHostAddress());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendMulticast("discover "+nodeName);
    }

    public void receiveMessages() {
        logger.info("Open socket to receive multicasts");
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(GROUP_ADDRESS);

            // On modern Java/VMs, it's safer to specify the interface
            socket.joinGroup(group);

            logger.info("Listening for multicast on " + GROUP_ADDRESS + ":" + PORT);

            byte[] buf = new byte[256];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                logger.info("Multicast packet received: "+ received);
                if(received.startsWith("discover")){
                    String[] parts = received.split(" ");
                    String receivedNodeName = parts[1];
                    int hashNodeName = hashingService.hashingFunction(receivedNodeName);
                    // 1. Get the InetAddress object
                    InetAddress senderAddress = packet.getAddress();
                    if(receivedNodeName.contains("naming")) {
                        continue;
                    }
                    sendMulticast("discover " + nodeName);

                    // 2. Get the IP as a String
                    String clientIp = senderAddress.getHostAddress();
                    if(ipAddresses.containsValue(clientIp)){
                        logger.error("already contains ip of node");
                        continue;
                    }
                    if(ipAddresses.containsKey(hashNodeName)){
                        logger.error("already contains name of node");
                        continue;
                    }
                    int numNodes = ipAddresses.size();
                    insertIpAddress(hashNodeName, clientIp);

                    String result = restClient.post()
                            .uri("http://"+clientIp+":8080/node/discover-response/{numNodes}", numNodes)
                            .retrieve()
                            .body(String.class);
                    logger.info(result);

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}