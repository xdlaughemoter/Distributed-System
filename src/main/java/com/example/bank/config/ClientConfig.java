package com.example.bank.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {
    public static final Logger logger = LoggerFactory.getLogger(ClientConfig.class);
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder
                .requestInterceptor((request, body, execution) -> {
                    // One-liner to log the URI
                    logger.info(">>> HTTP Request URI: " + request.getURI());
                    return execution.execute(request, body);
                })
                .build();
    }
}
