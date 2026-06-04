package com.example.repostclient;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NamingServerClient {

    private final RestClient restClient = RestClient.builder().build();

    // Replace this with your actual Naming Server Base URL (e.g., "http://localhost:8081")
    private static final String NAMING_SERVER_BASE = "http://143.129.43.59:8081/naming";

    public record IpInfo(String address, String nodeName) {}
    public record FileInfo(String address, String fileName) {}

    /**
     * 1. DELETE: ${NAMING_SERVER_BASE}/${nodeName}/remove-node
     */
    public void removeNode(String nodeName) {
        restClient.delete()
                .uri(NAMING_SERVER_BASE + "/{nodeName}/remove-node", nodeName)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * 2. GET: http://${nodeIp}:8080/node/neighbours
     */
    public List<String> getNodeNeighbours(String nodeIp) {
        String url = String.format("http://%s:8080/node/neighbours", nodeIp);
        return restClient.get()
                .uri(url)
                .retrieve()
                .body(List.class); // Adjust return type (e.g., List<String> or a custom DTO) as needed
    }

    /**
     * 3. GET: ${NAMING_SERVER_BASE}/${nodeName}/getOwnedFilesNormal
     */
    public List<String> getOwnedFilesNormal(String nodeName) {
        return restClient.get()
                .uri(NAMING_SERVER_BASE + "/{nodeName}/getOwnedFilesNormal", nodeName)
                .retrieve()
                .body(List.class);
    }

    /**
     * 4. GET: ${NAMING_SERVER_BASE}/getALlNodes
     */
    public Map<String, LinkedHashMap<String, String>> getAllNodes() {
        return restClient.get()
                .uri(NAMING_SERVER_BASE + "/getALlNodes")
                .retrieve()
                .body(Map.class); // Adjust to your actual network topology map/list structure
    }

    /**
     * 5. POST: ${NAMING_SERVER_BASE}/${newName}/addNode/${newIp}
     */
    public void addNode(String newName, String newIp) {
        restClient.post()
                .uri(NAMING_SERVER_BASE + "/{newName}/addNode/{newIp}", newName, newIp)
                .retrieve()
                .toBodilessEntity();
    }
}