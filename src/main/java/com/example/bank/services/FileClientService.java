package com.example.bank.services;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
public class FileClientService {

    private final RestTemplate restTemplate = new RestTemplate();

    // --- SENDING A FILE (POST) ---
    public String uploadFile(String url, MultipartFile file) {
        RestClient restClient = RestClient.create();

        // We use a MultiValueMap to wrap the file resource
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        return restClient.post()
                .uri("http://{ip}:8080/node/receive", url) // Assuming port 8080
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new RuntimeException("Node returned error: " + res.getStatusCode());
                })
                .body(String.class);
    }
}
