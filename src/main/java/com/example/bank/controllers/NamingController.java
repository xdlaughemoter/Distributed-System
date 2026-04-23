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
import java.util.Objects;


@RestController
@RequestMapping("/naming")
@EnableAsync
public class NamingController {
    private final HashingService hashingService = new HashingService();
    private final MulticastHandler multicastHandler = new MulticastHandler();
    public static final Logger logger = LoggerFactory.getLogger(NamingController.class);


    @GetMapping("/{fileName}/node-destroy")
    public ResponseEntity<String> determineNodeToStore(@PathVariable String fileName) {
        int hashFile = hashingService.hashingFunction(fileName);
        Map<Integer, String> ipAddresses = multicastHandler.getIpAddresses();
        if(ipAddresses.isEmpty()){
            return ResponseEntity.badRequest().body("no ip adresses on naming server");
        }
        int smallestHashDifference = Collections.max(ipAddresses.keySet());
        for (Integer i : ipAddresses.keySet()) {
            if(i>hashFile) continue;
            if (hashFile - i < Math.abs(smallestHashDifference - i)) { // absolute value bcus init can be negative
                smallestHashDifference = hashFile;
            }

        }

        multicastHandler.insertFileToNodeIP(hashFile, ipAddresses.get(smallestHashDifference));
        return ResponseEntity.ok(ipAddresses.get(smallestHashDifference));
    }


    @GetMapping("/{file}/fileSearch")
    public ResponseEntity<String> getNodeWhichHasFile(@PathVariable String file) {
        int hash = hashingService.hashingFunction(file);
        Map<Integer, String> fileToNodeIP = multicastHandler.getFileToNodeIP();
        if(!fileToNodeIP.containsKey(hash)){
            return ResponseEntity.notFound().build();
        }
        String ip = fileToNodeIP.get(hashingService.hashingFunction(file));

        return ResponseEntity.ok(ip);
    }
    @GetMapping("/{name}/get")
    public ResponseEntity<String> getIPByNode(@PathVariable String name) {
        Map<Integer, String> ipAddresses = multicastHandler.getIpAddresses();
        String resultIP = ipAddresses.getOrDefault(hashingService.hashingFunction(name), "error");
        return ResponseEntity.ok(resultIP);
    }

    @PostMapping("/{name}/add")
    public ResponseEntity<String> addIP(@PathVariable String name, HttpServletRequest request) {
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
        int hashFailed = hashingService.hashingFunction(name);
        int higherHash = Integer.MAX_VALUE;
        int lowerHash = 0;
        Map<Integer, String> ipAddresses = multicastHandler.getIpAddresses();
        RestClient restClient = RestClient.create();
        for (Integer i : ipAddresses.keySet()) {
            if(lowerHash<i && i<hashFailed){
                lowerHash=i;
            } else if(hashFailed<i && i<higherHash){
                higherHash=i;
            }
        }
        if(lowerHash == 0){
            lowerHash = ipAddresses.keySet().stream()
                    .max(Integer::compare)
                    .orElse(0);
        }
        if(higherHash == Integer.MAX_VALUE){
            higherHash =  ipAddresses.keySet().stream()
                    .min(Integer::compare)
                    .orElse(0);
        }
        String ipPrevNode = ipAddresses.get(lowerHash);
        String namePreviousNode = restClient.get()
                .uri("http://"+ipPrevNode+":8080/node/nodename")
                .retrieve()
                .body(String.class);
        logger.info(namePreviousNode);
        String ipNextNode = ipAddresses.get(higherHash);
        String nameNextNode = restClient.get()
                .uri("http://"+ipNextNode+":8080/node/nodename")
                .retrieve()
                .body(String.class);
        logger.info(nameNextNode);

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
        multicastHandler.removeFileToNodeIP(hashFailed);
        multicastHandler.removeIpAddress(hashFailed);

        return ResponseEntity.ok("Success! Node deleted ");
    }




}
