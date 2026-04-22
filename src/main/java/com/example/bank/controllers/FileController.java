package com.example.bank.controllers;

import com.example.bank.services.FileClientService;
import com.example.bank.services.HashingService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@RestController
@RequestMapping("/node")
@EnableAsync
public class FileController {
    public static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final FileClientService fileClientService;
    private final String uploadDirectory = System.getProperty("user.dir")+ File.separator + "uploaded_files";
    private final HashingService hashingService = new HashingService();

    public FileController(FileClientService fileClientService) {
        this.fileClientService = fileClientService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Resource> uploadFile(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        RestClient restClient = RestClient.create();

        String result = restClient.get()
                .uri("http://localhost:8081/naming/{filename}/file-store", filename)
                .retrieve()
                .body(String.class);
        logger.info(result);
        fileClientService.uploadFile(result, file);
        return ResponseEntity.ok().build();
    }

    // received from naming server
    @PostMapping("/discover-response/{numNodes}")
    public ResponseEntity<Resource> discoverResponse(@PathVariable int numNodes) {
        fileClientService.setNumNodes(numNodes);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/neighbour-mapping/{nodeName}/{typeNeighbour}")
    public ResponseEntity<Resource> setNeighbour(@PathVariable String nodeName, @PathVariable String typeNeighbour) {
        int hash = hashingService.hashingFunction(nodeName);
        if(typeNeighbour.equals("previous")){
            fileClientService.setPreviousNode(hash);
        } else{
            fileClientService.setNextNode(hash);
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
