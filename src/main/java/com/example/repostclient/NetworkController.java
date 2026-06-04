package com.example.repostclient;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/network")
public class NetworkController {

    private final NamingServerClient namingServerClient;

    public NetworkController(NamingServerClient namingServerClient) {
        this.namingServerClient = namingServerClient;
    }

    @DeleteMapping("/nodes/{nodeName}")
    public ResponseEntity<Void> removeNode(@PathVariable String nodeName) {
        namingServerClient.removeNode(nodeName);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/nodes/neighbours")
    public ResponseEntity<List<String>> getNodeNeighbours(@RequestParam String ip) {
        return ResponseEntity.ok(namingServerClient.getNodeNeighbours(ip));
    }

    @GetMapping("/nodes/{nodeName}/files")
    public ResponseEntity<List<String>> getOwnedFiles(@PathVariable String nodeName) {
        return ResponseEntity.ok(namingServerClient.getOwnedFilesNormal(nodeName));
    }

    @GetMapping("/nodes")
    public ResponseEntity<Map<String, LinkedHashMap<String, String>>> getAllNodes() {
        return ResponseEntity.ok(namingServerClient.getAllNodes());
    }

    @PostMapping("/nodes")
    public ResponseEntity<Void> addNode(@RequestParam String name, @RequestParam String ip) {
        namingServerClient.addNode(name, ip);
        return ResponseEntity.ok().build();
    }
}
