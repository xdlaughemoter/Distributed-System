package com.example.bank.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/naming")
@EnableAsync
public class NamingController {
    private final Map<Integer, String> ipAddresses = new ConcurrentHashMap<>();
    private final Map<Integer, String> fileToNodeIP = new ConcurrentHashMap<>();
    private ObjectMapper mapper;
    private File ipFile = new File("ipAddresses.json");
    private File nodeFile = new File("fileToNodeIP.json");

    public NamingController() {
        mapper = new ObjectMapper();

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

    private Integer hashingFunction(String input){
        int hash = 0;
        // P is a prime number (31 is common for ASCII strings)
        int P = 31;
        // M is a large prime to keep the hash within a specific range
        int M = 1_000_000_009;

        for (int i = 0; i < input.length(); i++) {
            // Get the Unicode value of the character
            char c = input.charAt(i);

            // Formula: (hash * P + char_code) % M
            hash = (hash * P + c) % M;
        }

        return hash;
    }

    @GetMapping("/{fileName}/file-store")
    public ResponseEntity<String> determineNodeToStore(@PathVariable String fileName) {
        int hashFile = hashingFunction(fileName);
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

        fileToNodeIP.put(hashFile, ipAddresses.get(smallestHashDifference));
        try {
            // writeValue(File, Object) serializes and saves
            mapper.writerWithDefaultPrettyPrinter().writeValue(nodeFile, fileToNodeIP);
            System.out.println("JSON written successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok(ipAddresses.get(smallestHashDifference));
    }

    @GetMapping("/{file}/fileSearch")
    public ResponseEntity<String> getNodeWhichHasFile(@PathVariable String file) {
        int hash = hashingFunction(file);
        if(!fileToNodeIP.containsKey(hash)){
            return ResponseEntity.notFound().build();
        }
        String ip = fileToNodeIP.get(hashingFunction(file));

        return ResponseEntity.ok(ip);
    }
    // Get current account balance
    @GetMapping("/{name}/get")
    public ResponseEntity<String> getIPByNode(@PathVariable String name) {
        String resultIP = ipAddresses.getOrDefault(hashingFunction(name), "error");
        return ResponseEntity.ok(resultIP);
    }

    // Add money to the account
    @PostMapping("/{name}/add")
    public ResponseEntity<String> addIP(@PathVariable String name, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        if(ipAddresses.containsValue(clientIp)){
            return ResponseEntity.ok("IP already added");
        }
        Integer hash = hashingFunction(name);
        if(ipAddresses.containsKey(hash)){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Error! Name " + name + " already in use.");
        }
        ipAddresses.put(hash, clientIp);
        try {
            // writeValue(File, Object) serializes and saves
            mapper.writerWithDefaultPrettyPrinter().writeValue(ipFile, ipAddresses);
            System.out.println("JSON written successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok("Success! IP adress added: " + ipAddresses.get(hash));
    }


}
