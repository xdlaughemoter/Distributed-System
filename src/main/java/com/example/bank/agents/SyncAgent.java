package com.example.bank.agents;

import com.example.bank.config.IpNeighboursManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SyncAgent implements Serializable {
    public Map<String, Boolean> fileList = new ConcurrentHashMap<>();
    private final String fileDirectory = System.getProperty("user.dir")+ File.separator + "uploaded_files";
    private final RestClient restClient;
    private final IpNeighboursManager ipNeighboursManager;
    public static final Logger logger = LoggerFactory.getLogger(SyncAgent.class);

    public SyncAgent(RestClient restClient, IpNeighboursManager ipNeighboursManager) {
        this.restClient = restClient;
        this.ipNeighboursManager = ipNeighboursManager;
        File folder = new File(fileDirectory);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                // Only add files (ignore directories)
                if (file.isFile()) {
                    String fileName = file.getName();
                    fileList.putIfAbsent(fileName, false);
                }
            }
        }
    }

    public void sendSyncToNextNode(){
        logger.info("Sending sync agent to next node");
        try{
            String result = restClient.post()
                    .uri("http://" + ipNeighboursManager.getNextIP() + ":8080/node/syncAgent")
                    .body(this)
                    .retrieve()
                    .body(String.class);
            logger.info("Restclient response"+result);
        } catch (HttpClientErrorException e){
            logger.error(e.getMessage());
        }
    }
    public boolean checkLockFile(String fileName) {
        return fileList.get(fileName);
    }

    public void lockFile(String fileName) {
        sendSyncToNextNode();
        fileList.put(fileName, true);
    }

    public void unlockFile(String fileName) {
        sendSyncToNextNode();
        fileList.put(fileName, false);
    }
}
