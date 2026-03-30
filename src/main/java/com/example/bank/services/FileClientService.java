package com.example.bank.services;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
public class FileClientService {

    private final RestTemplate restTemplate = new RestTemplate();

    // --- SENDING A FILE (POST) ---
    public String uploadFile(String url, String filePath) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // MultiValueMap is required for multipart/form-data
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(new File(filePath)));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(url, requestEntity, String.class);
    }

    // --- RECEIVING A FILE (GET) ---
    public void downloadFile(String url, String destinationPath) throws IOException {
        // We request the response as a byte array
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            try (FileOutputStream fos = new FileOutputStream(destinationPath)) {
                fos.write(response.getBody());
            }
        }
    }
}
