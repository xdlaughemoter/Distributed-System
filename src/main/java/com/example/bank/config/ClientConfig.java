package com.example.bank.config;

import com.example.bank.services.NodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ClientConfig {
    public static final Logger logger = LoggerFactory.getLogger(ClientConfig.class);
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(2));
        return builder
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    // One-liner to log the URI
                    logger.info(">>> HTTP Request URI: " + request.getURI());
                    return execution.execute(request, body);
                })
                .build();
    }
}
