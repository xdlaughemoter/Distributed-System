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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/node")
@EnableAsync
public class FileController {
    public static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final NodeService nodeService;
    private final String fileDirectory = System.getProperty("user.dir")+ File.separator + "uploaded_files";
    private final HashingService hashingService = new HashingService();

    public FileController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping("/nodename")
    public ResponseEntity<String> getNodeName() {
        logger.info("Node name requested");
        return ResponseEntity.ok(nodeService.getNodeName());
    }
    @PostMapping("/notifyDeletion/{fileName}")
    public ResponseEntity<String> fileDeletedNotif(@PathVariable String fileName) {
        logger.info("File "+fileName+" has been deleted on another node");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        try{
            File directory = new File(fileDirectory);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File destinationFile = new File(fileDirectory + File.separator +filename);
            file.transferTo(destinationFile);
            logger.info("Replication initiated after upload of file "+filename);
            nodeService.replicateFile(destinationFile);
        } catch (IOException e) {
            logger.error("Could not save file: ", e);
            return ResponseEntity.internalServerError().body("Failed to save file: " + e.getMessage());
        }
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
            logger.info("previous node changed from "+ nodeService.getIpPreviousNode() +" to " + ipadd);
            nodeService.setPreviousNode(nodeName);
            nodeService.setIpPreviousNode(ipadd);
        } else{
            logger.info("next node changed from "+ nodeService.getIpNextNode() +" to " + ipadd);
            nodeService.setNextNode(nodeName);
            nodeService.setIpNextNode(ipadd);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/neighbour-mapping-destroy/{nodeName}/{typeNeighbour}/{ipadd}")
    public ResponseEntity<String> setNeighbourAfterDestroy(@PathVariable String nodeName, @PathVariable String typeNeighbour, @PathVariable String ipadd) {
        if(typeNeighbour.equals("previous")){
            logger.info("previous node changed from "+ nodeService.getIpPreviousNode() +" to " + ipadd);
            nodeService.setPreviousNode(nodeName);
            nodeService.setIpPreviousNode(ipadd);
        } else{
            logger.info("next node changed from "+ nodeService.getIpNextNode() +" to " + ipadd);
            nodeService.setNextNode(nodeName);
            nodeService.setIpNextNode(ipadd);
        }
        return ResponseEntity.ok().build();
    }


    // download request from other users
    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> provideDownload(@PathVariable String fileName, HttpServletRequest request) {
        File file = new File(fileDirectory + File.separator + fileName);
        if (file.exists() && file.isFile()){
            Resource resource = new FileSystemResource(file);
            logger.info("Request for file download completed of file "+fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM) // Generic binary data
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/file-list")
    public ResponseEntity<String> getListLocalFiles() {
        File folder = new File(fileDirectory);
        String fileNames = Arrays.stream(folder.listFiles())
                .filter(File::isFile)               // Optional: filter out directories
                .map(File::getName)                 // Get just the name string
                .collect(Collectors.joining(" "));
        logger.info("Returend filename list "+fileNames);
        return ResponseEntity.ok(fileNames);
    }

    @PostMapping("/receive")
    public ResponseEntity<String> getFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload.");
        }

        try {
            // 2. Create the directory if it doesn't exist
            File directory = new File(fileDirectory);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // 3. Create the full path for the new file
            String fileName = file.getOriginalFilename();
            logger.info("Received file "+fileName);
            File destinationFile = new File(fileDirectory + File.separator +fileName);

            // 4. Save the file to the local disk
            file.transferTo(destinationFile);

            return ResponseEntity.ok("File saved successfully to: " + destinationFile.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Could not save file: ", e);
            return ResponseEntity.internalServerError().body("Failed to save file: " + e.getMessage());
        }
    }



}
