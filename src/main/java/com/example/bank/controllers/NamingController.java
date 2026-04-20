package com.example.bank.controllers;

import com.example.bank.service.HashingService;
import com.example.bank.service.MulticastHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/naming")
@EnableAsync
public class NamingController {
    private final HashingService hashingService = new HashingService();
    private final MulticastHandler multicastHandler = new MulticastHandler();


    @GetMapping("/{fileName}/file-store")
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




}
