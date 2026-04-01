package com.example.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class BankApplication {

    public static void main(String[] args) {
        RestClient restClient = RestClient.create();
        // init of node, change name when making second etc node
        String result = restClient.post()
                .uri("http://localhost:8081/naming/node1/add")
                .retrieve()
                .body(String.class);
        SpringApplication.run(BankApplication.class, args);
    }

}
