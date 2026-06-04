package com.example.bank.agents;

import com.example.bank.config.IpNeighboursManager;
import com.example.bank.services.HashingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

public class FailureAgent implements Serializable, Runnable{
    private final String currentNode;
    private final String failingNode;

    private String ownIP = "hsapwfuawefiopo;ahwefio";

    private IpNeighboursManager ipNeighboursManager;
    public static final Logger logger = LoggerFactory.getLogger(SyncAgent.class);

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    private String nodeName;
    private  HashMap<Integer, String> newOwnersOfFiles = new HashMap<>();
    public FailureAgent(String currentNode, String failingNode, String nodeName) {
        this.currentNode = currentNode;
        this.failingNode = failingNode;
        this.nodeName = nodeName;
    }

    @Override
    public void run() {
        //.run()
        //read file list of curent node
        String fileDirectory = System.getProperty("user.dir")+ File.separator + "uploaded_files";
        File folder = new File(fileDirectory);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        // Get all files and folders in the directory
        File[] files = folder.listFiles();

        HashingService hashingService = new HashingService();

        //if it has a file that needs to be sent to a new owner:
        //	check if it is the new owner
        //	if not, send the file to the new owner through TCP
        //  remove it from the newownerlist
        RestClient restClient = RestClient.create();
        for (File file : files) {
            int hash = hashingService.hashingFunction(file.getName());
            if(newOwnersOfFiles.containsKey(hash)){
                if(!newOwnersOfFiles.get(hash).equals(ownIP)){
                    uploadFile(newOwnersOfFiles.get(hash), file, restClient);
                }
                newOwnersOfFiles.remove(hash);
            }

        }

        //if we are the current node, terminate the failing agent
        //send to next node
        logger.info("Is own name "+nodeName+" equal to "+ currentNode);
        if(!currentNode.equals(nodeName)){
            sendFailureToNextNode(restClient);
        }

    }

    public String uploadFile(String url, File file, RestClient restClient)  {
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

    public void sendFailureToNextNode(RestClient restClient){

        logger.info("Sending failure agent to next node");
        try{
            String result = restClient.post()
                    .uri("http://" + ipNeighboursManager.getNextIP() + ":8080/node/failureAgent")
                    .body(this)
                    .retrieve()
                    .body(String.class);
            logger.info("Restclient response"+result);
        } catch (HttpClientErrorException e){
            logger.error(e.getMessage());
        }
    }

    public HashMap<Integer, String> getNewOwnersOfFiles() {
        return newOwnersOfFiles;
    }

    public void setNewOwnersOfFiles(HashMap<Integer, String> newOwnersOfFiles) {
        this.newOwnersOfFiles = newOwnersOfFiles;
    }
    public void setIpNeighboursManager(IpNeighboursManager ipNeighboursManager) {
        this.ipNeighboursManager = ipNeighboursManager;
    }

    public void setOwnIP(String ownIP) {
        this.ownIP = ownIP;
    }


}
