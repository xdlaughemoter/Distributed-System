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

    private String ipPreviousNode;
    private String ipNextNode;
    private final HashingService hashingService = new HashingService();
    private int numNodes;
    public static final Logger logger = LoggerFactory.getLogger(NodeService.class);

    public String getNodeName() {
        return nodeName;
    }

    @Value("${app.nodename}")
    private String nodeName;

    public int getNumNodes() {
        return numNodes;
    }

    public void setNumNodes(int numNodes) {
        this.numNodes = numNodes;
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
        String result = restClient.post()
                .uri("http://localhost:8080/node/neighbour-mapping-destroy/{nodeName}/{typeNeighbour}/{ipadd}", previousNode, "previous", ipPreviousNode)
                .retrieve()
                .body(String.class);
        logger.info(result);
        String result2 = restClient.post()
                .uri("http://localhost:8080/node/neighbour-mapping-destroy/{nodeName}/{typeNeighbour}/{ipadd}", nodeName, "next", ipNextNode)
                .retrieve()
                .body(String.class);
        logger.info(result2);

    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 1. Start the listener in a BACKGROUND thread so it doesn't block Spring
        new Thread(this::receiveMessages).start();

        // 2. Now run your discovery logic
        discoverNodes();
    }

    public void receiveMessages() {
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(GROUP_ADDRESS);

            // On modern Java/VMs, it's safer to specify the interface
            socket.joinGroup(group);

            System.out.println("Listening for multicast on " + GROUP_ADDRESS + ":" + PORT);

            byte[] buf = new byte[256];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                InetAddress senderAddress = packet.getAddress();

                String clientIp = senderAddress.getHostAddress();
                if(received.startsWith("discover")){
                    String[] parts = received.split(" ");
                    if(parts.length>1){
                        RestClient restClient = RestClient.create();
                        String receivedNodeName = parts[1];
                        if(!receivedNodeName.contains("naming")) {
                            int hashReceived = hashingService.hashingFunction(receivedNodeName);
                            int hashCurrent = hashingService.hashingFunction(currentNode);
                            int hashPrevious = hashingService.hashingFunction(previousNode);
                            int hashNext = hashingService.hashingFunction(nextNode);
                            // current node was the only node, received node is the 2nd
                            if (Objects.equals(previousNode, currentNode) && Objects.equals(nextNode, currentNode) && !Objects.equals(currentNode, receivedNodeName)){
                                previousNode = receivedNodeName;
                                nextNode = receivedNodeName;
                                String result = restClient.post()
                                        .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "previous")
                                        .retrieve()
                                        .body(String.class);
                                logger.info(result);
                                String result2 = restClient.post()
                                        .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "next")
                                        .retrieve()
                                        .body(String.class);
                                logger.info(result2);
                            // current node is the smaller than the received hash
                            // next node is bigger than the received hash
                            } else if (hashCurrent < hashReceived && hashReceived < hashNext) {
                                nextNode = receivedNodeName;
                                ipNextNode = clientIp;
                                String result2 = restClient.post()
                                        .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "previous")
                                        .retrieve()
                                        .body(String.class);
                                logger.info(result2);
                            // current node is the bigger than the received hash
                            // previous node is smaller than the received hash
                            } else if (hashPrevious < hashReceived && hashReceived < hashCurrent) {
                                previousNode = receivedNodeName;
                                ipPreviousNode = clientIp;
                                String result = restClient.post()
                                        .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "next")
                                        .retrieve()
                                        .body(String.class);
                                logger.info(result);

                            // BIGGEST NODE GETS ADDED 2 CASES
                            // we are the smallest, and the biggest gets added
                            } else if (hashPrevious > hashCurrent && hashPrevious < hashReceived) {
                                previousNode = receivedNodeName;
                                ipPreviousNode = clientIp;
                                String result = restClient.post()
                                        .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "next")
                                        .retrieve()
                                        .body(String.class);
                                logger.info(result);
                            // we are the biggest, and a bigger get added
                            } else if (hashNext < hashCurrent && hashCurrent < hashReceived) {
                                nextNode = receivedNodeName;
                                ipPreviousNode = clientIp;
                                String result = restClient.post()
                                        .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "previous")
                                        .retrieve()
                                        .body(String.class);
                                logger.info(result);

                            // SMALLEST NODE GETS ADDED, 2 CASES
                            // we are the smallest, and a smaller gets added
                            } else if (hashPrevious > hashCurrent && hashReceived < hashCurrent) {
                                previousNode = receivedNodeName;
                                ipPreviousNode = clientIp;
                                String result = restClient.post()
                                        .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "next")
                                        .retrieve()
                                        .body(String.class);
                                logger.info(result);
                            // we are the biggest, and the smallest gets added
                            } else if (hashNext < hashCurrent && hashReceived < hashNext) {
                                nextNode = receivedNodeName;
                                ipPreviousNode = clientIp;
                                String result = restClient.post()
                                        .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "previous")
                                        .retrieve()
                                        .body(String.class);
                                logger.info(result);
                            }
                        }
                    }
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
}
