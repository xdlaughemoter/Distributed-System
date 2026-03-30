package com.example.bank.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/naming")
@EnableAsync
public class AccountController {
    private final Map<Integer, String> ipAddresses = new ConcurrentHashMap<>();
    private ObjectMapper mapper;

    public AccountController() {
        mapper = new ObjectMapper();
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


    // Get current account balance
    @GetMapping("/{name}/get")
    public ResponseEntity<String> getBalance(@PathVariable String name) {
        String resultIP = ipAddresses.getOrDefault(hashingFunction(name), "error");
        return ResponseEntity.ok(resultIP);

    }

    // Add money to the account
    @PostMapping("/{name}/add")
    public ResponseEntity<String> addIP(@PathVariable String name, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        Integer hash = hashingFunction(name);
        if(ipAddresses.containsKey(hash)){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Error! Name " + name + " already in use.");
        }
        ipAddresses.put(hash, clientIp);
        try {
            // writeValue(File, Object) serializes and saves
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File("user.json"), ipAddresses);
            System.out.println("JSON written successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok("Success! IP adress added: " + ipAddresses.get(hash));
    }


}
