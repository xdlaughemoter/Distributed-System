package com.example.bank.controllers;

import com.example.bank.services.HashingService;
import com.example.bank.services.MulticastHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Map;


@RestController
@RequestMapping("/naming")
@EnableAsync
public class NamingController {
    private final HashingService hashingService = new HashingService();
    private final MulticastHandler multicastHandler;
    public static final Logger logger = LoggerFactory.getLogger(NamingController.class);

    private final RestClient restClient; // Define it here

    // Spring will automatically provide the 'restClient' bean we defined in ClientConfig
    public NamingController(MulticastHandler multicastHandler, RestClient restClient) {
        this.multicastHandler = multicastHandler;
        this.restClient = restClient;
    }

    @GetMapping("/{fileName}/file-store")
    public ResponseEntity<String> determineNodeToStore(@PathVariable String fileName) {
        logger.info("Request to determine node to store file of name: "+fileName);
        int hashFile = hashingService.hashingFunction(fileName);
        Map<Integer, String> ipAddresses = multicastHandler.getIpAddresses();
        logger.info(ipAddresses.toString());
        if(ipAddresses.isEmpty()){
            return ResponseEntity.badRequest().body("no ip adresses on naming server");
        }
        int smallestHashDifference = Collections.max(ipAddresses.keySet());
        for (Integer i : ipAddresses.keySet()) {
            if(i>hashFile) continue;
            if (hashFile - i < Math.abs(smallestHashDifference - i)) { // absolute value bcus init can be negative
                logger.info(smallestHashDifference+" is current smalles has diff");
                smallestHashDifference = i;
            }
        }
        logger.info("Node found with smallest hash diff: "+smallestHashDifference);

        if(multicastHandler.insertFileToNodeIP(hashFile, ipAddresses.get(smallestHashDifference))){
            logger.info("Not a duplicate, file inserted");
            return ResponseEntity.ok(ipAddresses.get(smallestHashDifference));
        }
        return ResponseEntity.badRequest().body("File was already replicated, hash is duplicate");
    }


    @GetMapping("/{file}/file-search")
    public ResponseEntity<String> getNodeWhichHasFile(@PathVariable String file) {
        logger.info("Search which node has file: "+file);
        int hash = hashingService.hashingFunction(file);
        Map<Integer, String> fileToNodeIP = multicastHandler.getFileToNodeIP();
        if(!fileToNodeIP.containsKey(hash)){
            return ResponseEntity.notFound().build();
        }
        String ip = fileToNodeIP.get(hashingService.hashingFunction(file));
        logger.info("Node "+ip+" has the file");

        return ResponseEntity.ok(ip);
    }

    @DeleteMapping("/{name}/remove-node")
    public ResponseEntity<String> removeNode(@PathVariable String name, HttpServletRequest request) {
        logger.info("Remove node "+ name);
        String ipadd = request.getRemoteAddr();
        int hash = hashingService.hashingFunction(name);
        multicastHandler.removeIPFromFileToNode(hash);
        multicastHandler.removeIpAddress(hash);
        return ResponseEntity.ok().body("balbalbabla");
    }
    @GetMapping("/{name}/get")
    public ResponseEntity<String> getIPByNode(@PathVariable String name) {
        logger.info("get IP of node "+ name);
        Map<Integer, String> ipAddresses = multicastHandler.getIpAddresses();
        String resultIP = ipAddresses.getOrDefault(hashingService.hashingFunction(name), "error");
        return ResponseEntity.ok(resultIP);
    }

    @PostMapping("/{name}/add")
    public ResponseEntity<String> addIP(@PathVariable String name, HttpServletRequest request) {
        logger.info("Add node "+ name);
        String clientIp = request.getRemoteAddr();
        Map<Integer, String> ipAddresses = multicastHandler.getIpAddresses();
        if(ipAddresses.containsValue(clientIp)){
            return ResponseEntity.ok("IP already added");
        }
        Integer hash = hashingService.hashingFunction(name);
        if(ipAddresses.containsKey(hash)){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Error! Name " + name + " already in use.");
        }
        multicastHandler.insertIpAddress(hash, clientIp);


        return ResponseEntity.ok("Success! IP adress added: " + ipAddresses.get(hash));
    }

    @PostMapping("/{name}/failure")
    public ResponseEntity<String> failureNodeDelete(@PathVariable String name) {
        logger.info("Failure in node "+ name);
        int hashFailed = hashingService.hashingFunction(name);
        int nextHash = multicastHandler.getNextHashofNodeHash(hashFailed);
        int previousHash = multicastHandler.getPreviousHashofNodeHash(hashFailed);
        Map<Integer, String> ipAddresses = multicastHandler.getIpAddresses();

        String ipPrevNode = ipAddresses.get(previousHash);
        logger.info("IP previous node "+ ipPrevNode);
        String namePreviousNode = restClient.get()
                .uri("http://"+ipPrevNode+":8080/node/nodename")
                .retrieve()
                .body(String.class);
        logger.info(namePreviousNode);
        String ipNextNode = ipAddresses.get(nextHash);
        logger.info("IP next node "+ ipNextNode);
        String nameNextNode = restClient.get()
                .uri("http://"+ipNextNode+":8080/node/nodename")
                .retrieve()
                .body(String.class);
        logger.info(nameNextNode);
        logger.info("Neighbour mapping ");
        String result = restClient.post()
                .uri("http://" + ipPrevNode + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", nameNextNode, "next")
                .retrieve()
                .body(String.class);
        logger.info(result);
        String result2 = restClient.post()
                .uri("http://" + ipNextNode + ":8080/node/neighbour-mapping/{nodeName}/{typeNeighbour}", namePreviousNode, "previous")
                .retrieve()
                .body(String.class);
        logger.info(result2);
        logger.info("Remove node from hashmappings ");
        multicastHandler.removeIPFromFileToNode(hashFailed);
        multicastHandler.removeIpAddress(hashFailed);

        return ResponseEntity.ok("Success! Node deleted ");
    }




}
