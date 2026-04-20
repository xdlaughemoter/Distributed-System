package com.example.bank.services;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

@Service
public class FileClientService {

    private final String GROUP_ADDRESS = "230.0.0.0";
    private final int PORT = 4446;
    private int previousNode;
    private int currentNode;
    private int nextNode;
    private final HashingService hashingService = new HashingService();
    private int numNodes;
    public static final Logger logger = LoggerFactory.getLogger(FileClientService.class);

    @Value("${app.nodename}")
    private String nodeName;

    public FileClientService() {
        this.currentNode = this.nextNode = this.previousNode = hashingService.hashingFunction(nodeName);
    }

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

    @PostConstruct
    public void discoverNodes(){
        sendMulticast("discover "+nodeName);
    }

    @Bean
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
                if(received.startsWith("discover")){
                    String[] parts = received.split(" ");
                    String receivedNodeName = parts[1];
                    int hash = hashingService.hashingFunction(receivedNodeName);
                    if (previousNode==currentNode && nextNode==currentNode){
                        previousNode = hash;
                        nextNode = hash;
                        RestClient restClient = RestClient.create();

                        String result = restClient.get()
                                .uri("http://localhost:8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "previous")
                                .retrieve()
                                .body(String.class);
                        logger.info(result);
                        String result2 = restClient.get()
                                .uri("http://localhost:8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "next")
                                .retrieve()
                                .body(String.class);
                        logger.info(result2);
                    }

                }
                System.out.println("<<< Received: " + received);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setPreviousNode(int hash) {
        this.previousNode = hash;
    }

    public void setNextNode(int hash) {
        this.nextNode = hash;
    }
}
