package com.example.bank.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/naming")
@EnableAsync
public class FileController {
    private final Map<Integer, String> filePaths = new ConcurrentHashMap<>();

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


    // download request from other users
    @GetMapping("/{name}/download")
    public ResponseEntity<Resource> provideDownload(@PathVariable String name) {
        int hash = hashingFunction(name);

        if (!filePaths.containsKey(hash)) {
            return ResponseEntity.notFound().build();
        }

        String pathFile = filePaths.get(hash);
        File file = new File(pathFile);

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM) // Generic binary data
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    @GetMapping("/{name}/get")
    public ResponseEntity<String> getFile(@PathVariable String name) {

        return ResponseEntity.ok(resultIP);
    }



}
