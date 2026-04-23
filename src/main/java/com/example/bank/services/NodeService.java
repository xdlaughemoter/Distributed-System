package com.example.bank.services;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.net.*;
import java.util.Enumeration;
import java.util.Objects;

@Service
public class NodeService {

    private final String GROUP_ADDRESS = "230.0.0.0";
    private final int PORT = 4446;
    private String previousNode;
    private String currentNode;
    private String nextNode;

    private String ipPreviousNode;
    private String ipNextNode;
    private final HashingService hashingService = new HashingService();
    private int numNodes;
    public static final Logger logger = LoggerFactory.getLogger(NodeService.class);
    @Value("${app.nodename}")
    private String nodeName;

    private String namingIp;

    private void failureNotifyNamingServ(String nodeName){
        RestClient restClient = RestClient.create();
        try{
            String result = restClient.post()
                    .uri("http://" + namingIp + ":8081/naming/{nodeName}/failure", nodeName)
                    .retrieve()
                    .body(String.class);
            logger.info(result);
        } catch (HttpClientErrorException e){
            logger.error(e.getMessage());
        }
    }

    // --- SENDING A FILE (POST) ---
    public String uploadFile(String url, MultipartFile file) {
        RestClient restClient = RestClient.create();

        // We use a MultiValueMap to wrap the file resource
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        return restClient.post()
                .uri("http://{ip}:8080/node/receive", url) // Assuming port 8080
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    //failureNotifyNamingServ(); dont know the name. if server doesnt respond cant get the name anyhow
                    throw new RuntimeException("Node returned error: " + res.getStatusCode());
                })
                .body(String.class);
    }

    public void sendMulticast(String message) {
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

    public void discoverNodes(){
        try {
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
        this.currentNode = this.nextNode = this.previousNode = nodeName;
        sendMulticast("discover "+nodeName);
    }

    @PreDestroy
    public void onDestroy(){
        RestClient restClient = RestClient.create();
        if(!currentNode.equals(previousNode) && !currentNode.equals(nextNode)){
            return;
        }
        try{
            String result = restClient.post()
                    .uri("http://"+ipNextNode+":8080/node/neighbour-mapping-destroy/{nodeName}/{typeNeighbour}/{ipadd}", previousNode, "previous", ipPreviousNode)
                    .retrieve()
                    .body(String.class);
            logger.info(result);
        } catch (HttpClientErrorException e){
            failureNotifyNamingServ(currentNode); // current node also failed and needs to dissapear somehow
            failureNotifyNamingServ(previousNode);
            logger.error(e.getMessage());
            return;
        }
        try{
            String result2 = restClient.post()
                    .uri("http://"+ipPreviousNode+":8080/node/neighbour-mapping-destroy/{nodeName}/{typeNeighbour}/{ipadd}", nextNode, "next", ipNextNode)
                    .retrieve()
                    .body(String.class);
            logger.info(result2);
        } catch (HttpClientErrorException e){
            failureNotifyNamingServ(currentNode); // current node also failed and needs to dissapear somehow
            failureNotifyNamingServ(nextNode);
            logger.error(e.getMessage());
        }

    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 1. Start the listener in a BACKGROUND thread so it doesn't block Spring
        new Thread(this::receiveMessages).start();

        // 2. Now run your discovery logic
        discoverNodes();
    }

    private void sendChangePrevious(RestClient restClient, String clientIp){
        String result = restClient.post()
                .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "previous")
                .retrieve()
                .body(String.class);
        logger.info(result);
    }

    private void sendChangeNext(RestClient restClient, String clientIp){
        String result2 = restClient.post()
                .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "next")
                .retrieve()
                .body(String.class);
        logger.info(result2);
    }

    private void setPreviousNode(String nodeName, String ip, RestClient restClient) {
        previousNode = nodeName;
        ipPreviousNode = ip;
        try {
            sendChangeNext(restClient, ip);
        } catch (HttpClientErrorException e){
            failureNotifyNamingServ(nodeName);
            logger.error(e.getMessage());
        }
    }

    private void setNextNode(String nodeName, String ip, RestClient restClient) {
        nextNode = nodeName;
        ipNextNode = ip;
        try{
            sendChangePrevious(restClient, ip);
        } catch (HttpClientErrorException e){
            failureNotifyNamingServ(nodeName);
            logger.error(e.getMessage());
        }
    }

    private boolean isBetween(int start, int value, int end) {
        if (start < end)
            return start < value && value < end;

        return value > start || value < end;
    }

    public void receiveMessages() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
            // On modern Java/VMs, it's safer to specify the interface
            socket.joinGroup(group);

            System.out.println("Listening for multicast on " + GROUP_ADDRESS + ":" + PORT);
            RestClient restClient = RestClient.create();
            byte[] buf = new byte[256];

            while (true) {
                ///packet setup
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength());
                if(!received.startsWith("discover")) {
                    continue;
                }

                String[] parts = received.split(" ");
                if(parts.length<=1) {
                    continue;
                }
                String clientIp = packet.getAddress().getHostAddress();
                String receivedNodeName = parts[1];
                if(receivedNodeName.contains("naming")) {
                    // add this node to the naming servers IP list
                    namingIp=clientIp;
                    try {
                        String result2 = restClient.post()
                                .uri("http://" + clientIp + ":8081/naming/{name}/add", nodeName, "next")
                                .retrieve()
                                .body(String.class);
                        logger.info(result2);
                    } catch (HttpClientErrorException e){
                        logger.error(e.getMessage());
                    }

                    continue;
                }



                /// hashing of node names
                int hashReceived = hashingService.hashingFunction(receivedNodeName);
                int hashCurrent = hashingService.hashingFunction(currentNode);
                int hashPrevious = hashingService.hashingFunction(previousNode);
                int hashNext = hashingService.hashingFunction(nextNode);

                /// order logic
                boolean isOnlyNode = Objects.equals(previousNode, currentNode)
                        && Objects.equals(nextNode, currentNode)
                        && !Objects.equals(currentNode, receivedNodeName);

                if (isOnlyNode) {
                    setPreviousNode(receivedNodeName, clientIp, restClient);
                    setNextNode(receivedNodeName, clientIp, restClient);
                } else if (isBetween(hashCurrent, hashReceived, hashNext)) {
                    setNextNode(receivedNodeName, clientIp, restClient);
                } else if (isBetween(hashPrevious, hashReceived, hashCurrent)) {
                    setPreviousNode(receivedNodeName, clientIp, restClient);
                }

                System.out.println("<<< Received: " + received);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setPreviousNode(String name) {
        this.previousNode = name;
    }

    public void setNextNode(String name) {
        this.nextNode = name;
    }
    public String getNamingIp() {
        return namingIp;
    }

    public void setNamingIp(String namingIp) {
        this.namingIp = namingIp;
    }
    public String getIpPreviousNode() {
        return ipPreviousNode;
    }

    public void setIpPreviousNode(String ipPreviousNode) {
        this.ipPreviousNode = ipPreviousNode;
    }

    public String getIpNextNode() {
        return ipNextNode;
    }

    public void setIpNextNode(String ipNextNode) {
        this.ipNextNode = ipNextNode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getNumNodes() {
        return numNodes;
    }

    public void setNumNodes(int numNodes) {
        this.numNodes = numNodes;
    }
}
