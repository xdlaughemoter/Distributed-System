package com.example.bank.services;

import com.example.bank.agents.SyncAgent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.*;
import java.net.*;
import java.util.*;

@Service
@EnableScheduling
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
    private String ownIP;
    private final String fileDirectory = System.getProperty("user.dir")+ File.separator + "uploaded_files";
    private final RestClient restClient; // Define it here

    private final SyncAgent syncAgent;

    // Spring will automatically provide the 'restClient' bean we defined in ClientConfig
    public NodeService(RestClient restClient) {
        this.restClient = restClient;
        this.syncAgent = new SyncAgent();
    }
    public SyncAgent getSyncAgent() {
        return syncAgent;
    }

    private void failureNotifyNamingServ(String nodeName){
        logger.info("Failure notification to naming server");
        try{
            String result = restClient.post()
                    .uri("http://" + namingIp + ":8081/naming/{nodeName}/failure", nodeName)
                    .retrieve()
                    .body(String.class);
            logger.info("Restclient response"+result);
        } catch (HttpClientErrorException e){
            logger.error(e.getMessage());
        }
    }

    public void sendSyncToNextNode(){
        logger.info("Sending sync agent to next node");
        try{
            String result = restClient.post()
                    .uri("http://" + ipNextNode + ":8080/node/syncAgent", syncAgent)
                    .body(syncAgent)
                    .retrieve()
                    .body(String.class);
            logger.info("Restclient response"+result);
        } catch (HttpClientErrorException e){
            logger.error(e.getMessage());
        }
    }

    // --- SENDING A FILE (POST) ---
    public String uploadFile(String url, File file)  {
        // We use a MultiValueMap to wrap the file resource
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        logger.info("File "+file.getName()+" uploaded to "+ url);
        try {
            if(url.equals(InetAddress.getLocalHost().getHostAddress()))
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
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return "nblabla;";
    }

    public void sendMulticast(String message) {
        try (MulticastSocket socket = new MulticastSocket()) {
            InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
            byte[] buf = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, PORT);
            socket.send(packet);
            logger.info(">>> Multicast Sent: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void discoverNodes(){
        try {
            logger.info("Discover node message");
            // find own IP adress
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
                    ownIP = addr.getHostAddress();
                    logger.info(iface.getDisplayName() + " IP: " + addr.getHostAddress());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // init situation (we assume we are the only node
        this.currentNode = this.nextNode = this.previousNode = nodeName;
        // send out multicast to discover different servers
        sendMulticast("discover "+nodeName);
    }

    @PreDestroy
    public void onDestroy(){
        logger.info("On Destroy triggered");

        File folder = new File(fileDirectory);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        // Get all files and folders in the directory
        File[] files = folder.listFiles();

        List<String> fileNamesPrev= new ArrayList<>();
        if(!(currentNode.equals(previousNode) && currentNode.equals(nextNode))) {
            try {
                logger.info("Get list of files on previous node");
                String result = restClient.get()
                        .uri("http://" + ipPreviousNode + ":8080/node/file-list")
                        .retrieve()
                        .body(String.class);
                logger.info("Restclient response" + result);
                if (result != null) {
                    fileNamesPrev = Arrays.stream(result.split(" ")).toList();
                }
            } catch (ResourceAccessException e) {
                failureNotifyNamingServ(previousNode);
                failureNotifyNamingServ(currentNode);
                logger.info("failurenotify should have ran");
                logger.warn(e.getMessage());
            }
        }

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    logger.info("Check file "+file.getName());
                    if(!fileNamesPrev.stream().anyMatch(name -> name.equals(file.getName()))){
                        // if im not the owner, dont send it to previous
                        logger.info("File does not exist on previous node");
                        try {
                            logger.info("Check who is the owner of the file");
                            String result = restClient.get()
                                    .uri("http://" + getNamingIp() + ":8081/naming/{filename}/file-search", file.getName())
                                    .retrieve()
                                    .body(String.class);
                            logger.info("Restclient response"+result);
                            logger.info(ownIP);// just gives localhost
                            if(!result.equals(ownIP) && !(currentNode.equals(previousNode) && currentNode.equals(nextNode))){
                                logger.info("We are not the owner, so notify the owner and continue");
                                String result2 = restClient.post()
                                        .uri("http://"+result+":8080/node/notifyDeletion/{fileName}", file.getName())
                                        .retrieve()
                                        .body(String.class);
                                logger.info("Restclient response"+result2);
                                continue;
                            }
                        }catch (HttpClientErrorException e){
                            logger.error(e.getMessage());
                        }
                        logger.info("We are the owner, so send to previous node and make him owner");
                        sendFileToPreviousNode(file);
                    }
                }
            }
        } else {
            System.err.println("The path is not a directory or an I/O error occurred.");
        }

        logger.info("Remove node from naming server");
        String result3 = restClient.delete()
                .uri("http://"+getNamingIp()+":8081/naming/{nodeName}/remove-node", nodeName)
                .retrieve()
                .body(String.class);
        logger.info("Restclient response"+result3);

        if(currentNode.equals(previousNode) && currentNode.equals(nextNode)){
            return;
        }
        try{
            logger.info("Destroy neighbour mapping");
            String result = restClient.post()
                    .uri("http://"+ipNextNode+":8080/node/neighbour-mapping-destroy/{nodeName}/{typeNeighbour}/{ipadd}", previousNode, "previous", ipPreviousNode)
                    .retrieve()
                    .body(String.class);
            logger.info("Restclient response"+result);
        } catch (ResourceAccessException e){
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
            logger.info("Restclient response"+result2);
        } catch (ResourceAccessException e){
            failureNotifyNamingServ(currentNode); // current node also failed and needs to dissapear somehow
            failureNotifyNamingServ(nextNode);
            logger.error(e.getMessage());
        }

    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 1. Start the listener in a BACKGROUND thread so it doesn't block Spring
        logger.info("Application ready");
        new Thread(this::receiveMessages).start();
        new Thread(this::receiveFiles).start();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Handle the interruption if necessary
        }
        // 2. Now run your discovery logic
        discoverNodes();
        distributeFiles();
    }

    @Scheduled(fixedDelay = 5000) // every 5 seconds
    private void distributeFiles(){
        File folder = new File(fileDirectory);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // Get all files and folders in the directory
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    replicateFile(file);
                }
            }
        } else {
            System.err.println("The path is not a directory or an I/O error occurred.");
        }
    }

    public void sendFileToPreviousNode(File file){
        logger.info("Send file to previous node through TCP");
        try (Socket socket = new Socket(ipPreviousNode, 9000);
             FileInputStream fis = new FileInputStream(file);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            // 1. Send metadata (Filename and Size)
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            // 2. Stream file bytes
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
            dos.flush();
            logger.info("File "+file.getName()+" sent successfully.");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void receiveFiles() {
        try {
            ServerSocket serverSocket = new ServerSocket(9000);
            logger.info("TCP Server listening on port 9000 for file transfers...");

            try (Socket socket = serverSocket.accept();
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                // 1. Read metadata
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                try (FileOutputStream fos = new FileOutputStream(fileDirectory+File.separator + fileName)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;

                    // 2. Read file content based on size
                    while (totalRead < fileSize && (bytesRead = dis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                    logger.info("File " + fileName + " received successfully.");
                }
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            } catch (IOException e){
                logger.error(e.getMessage());

            }
        }

    public void replicateFile(File file){
            String filename = file.getName();
            try{
                logger.info("Replication begin");
                if(namingIp == null){
                    return;
                }
                String result = restClient.get()
                    .uri("http://"+getNamingIp()+":8081/naming/{filename}/file-store", filename)
                    .retrieve()
                    .body(String.class);
                logger.info("File replicated to "+result);
                uploadFile(result, file);
            } catch (HttpClientErrorException e){
                logger.warn(e.getMessage());
            }
        }

    private void sendChangePrevious(RestClient restClient, String clientIp){
        String result = restClient.post()
                .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "previous")
                .retrieve()
                .body(String.class);
        logger.info("Restclient response"+result);
    }

    private void sendChangeNext(RestClient restClient, String clientIp){
        String result = restClient.post()
                .uri("http://" + clientIp + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nodeName, "next")
                .retrieve()
                .body(String.class);
        logger.info("Restclient response"+result);
    }

    private void setPreviousNode(String nodeName, String ip, RestClient restClient) {
        previousNode = nodeName;
        ipPreviousNode = ip;
        try {
            sendChangeNext(restClient, ip);
        } catch (ResourceAccessException e){
            failureNotifyNamingServ(nodeName);
            logger.error(e.getMessage());
        }
    }

    private void setNextNode(String nodeName, String ip, RestClient restClient) {
        nextNode = nodeName;
        ipNextNode = ip;
        try{
            sendChangePrevious(restClient, ip);
        } catch (ResourceAccessException e){
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
        logger.info("Open socket to receive multicasts");
        try (MulticastSocket socket = new MulticastSocket(PORT)) {
            InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
            // On modern Java/VMs, it's safer to specify the interface
            socket.joinGroup(group);

            logger.info("Listening for multicast on " + GROUP_ADDRESS + ":" + PORT);
            byte[] buf = new byte[256];

            while (true) {
                ///packet setup
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength());
                logger.info("Multicast packet received: "+ received);
                // check for correct package start
                if(!received.startsWith("discover")) {
                    logger.info("Multicast packet didnt start with discover");
                    continue;
                }

                // check if a name was passed on
                String[] parts = received.split(" ");
                if(parts.length<=1) {
                    continue;
                }
                // get ip adress of sender
                String clientIp = packet.getAddress().getHostAddress();
                // get name of sender
                String receivedNodeName = parts[1];
                // if it is naming server
                if(receivedNodeName.contains("naming")) {
                    logger.info("Naming server discovered");
                    // add this node to the naming servers IP list
                    namingIp=clientIp;
                    logger.info("Naming server IP set to: "+ clientIp);
                    try {
                        logger.info("Add current node to naming server");
                        String result2 = restClient.post()
                                .uri("http://" + clientIp + ":8081/naming/{name}/add", nodeName, "next")
                                .retrieve()
                                .body(String.class);
                        logger.info("Restclient response"+result2);
                    } catch (HttpClientErrorException e){
                        logger.error(e.getMessage());
                    }

                    continue;
                }
                logger.info("Node discover received");
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
                    logger.info("Second node, set next and previous node to "+receivedNodeName);
                    setPreviousNode(receivedNodeName, clientIp, restClient);
                    setNextNode(receivedNodeName, clientIp, restClient);
                } else if (isBetween(hashCurrent, hashReceived, hashNext)) {
                    logger.info("Set next node to "+receivedNodeName);
                    setNextNode(receivedNodeName, clientIp, restClient);
                } else if (isBetween(hashPrevious, hashReceived, hashCurrent)) {
                    logger.info("Set previous node to "+receivedNodeName);
                    setPreviousNode(receivedNodeName, clientIp, restClient);
                } else{
                    logger.info("Neither next or previous node");
                }


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
