package com.example.bank.controllers;

import com.example.bank.services.NodeService;
import com.example.bank.services.HashingService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/node")
@EnableAsync
public class FileController {
    public static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final NodeService nodeService;
    private final String uploadDirectory = System.getProperty("user.dir")+ File.separator + "uploaded_files";
    private final HashingService hashingService = new HashingService();

    public FileController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping("/nodename")
    public ResponseEntity<String> getNodeName() {
        return ResponseEntity.ok(nodeService.getNodeName());
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        RestClient restClient = RestClient.create();

        String result = restClient.get()
                .uri("http://localhost:8081/naming/{filename}/file-store", filename)
                .retrieve()
                .body(String.class);
        logger.info(result);
        nodeService.uploadFile(result, file);
        return ResponseEntity.ok().build();
    }

    // received from naming server
    @PostMapping("/discover-response/{numNodes}")
    public ResponseEntity<String> discoverResponse(@PathVariable int numNodes) {
        nodeService.setNumNodes(numNodes);
        logger.info(numNodes +" number of nodes");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/neighbour-mapping/{nodeName}/{typeNeighbour}")
    public ResponseEntity<String> setNeighbour(@PathVariable String nodeName, @PathVariable String typeNeighbour, HttpServletRequest request) {
        String ipadd = request.getRemoteAddr();
        if(typeNeighbour.equals("previous")){
            nodeService.setPreviousNode(nodeName);
            nodeService.setIpPreviousNode(ipadd);
        } else{
            nodeService.setNextNode(nodeName);
            nodeService.setIpNextNode(ipadd);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/neighbour-mapping-destroy/{nodeName}/{typeNeighbour}/{ipadd}")
    public ResponseEntity<String> setNeighbourAfterDestroy(@PathVariable String nodeName, @PathVariable String typeNeighbour, @PathVariable String ipadd) {
        if(typeNeighbour.equals("previous")){
            nodeService.setPreviousNode(nodeName);
            nodeService.setIpPreviousNode(ipadd);
        } else{
            nodeService.setNextNode(nodeName);
            nodeService.setIpNextNode(ipadd);
        }
        return ResponseEntity.ok().build();
    }


    // download request from other users
    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> provideDownload(@PathVariable String fileName, HttpServletRequest request) {
        File file = new File(uploadDirectory + File.separator + fileName);
        String url = request.getRemoteAddr();
        if (file.exists() && file.isFile()){
            // 3. Wrap it in a Resource
            Resource resource = new FileSystemResource(file);

            // 4. Return it with the correct headers so the browser knows it's a file
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM) // Generic binary data
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/receive")
    public ResponseEntity<String> getFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload.");
        }

        try {
            // 2. Create the directory if it doesn't exist
            File directory = new File(uploadDirectory);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // 3. Create the full path for the new file
            String fileName = file.getOriginalFilename();
            File destinationFile = new File(uploadDirectory + File.separator +fileName);

            // 4. Save the file to the local disk
            file.transferTo(destinationFile);

            return ResponseEntity.ok("File saved successfully to: " + destinationFile.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Could not save file: ", e);
            return ResponseEntity.internalServerError().body("Failed to save file: " + e.getMessage());
        }
    }



}
