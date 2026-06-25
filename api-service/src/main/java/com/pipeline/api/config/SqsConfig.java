package com.pipeline.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pipeline.messaging.SqsJsonMessenger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class SqsConfig {

    @Bean
    SqsClient sqsClient(SqsProperties properties) {
        var builder = SqsClient.builder()
                .region(Region.of(properties.getRegion()));

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        return builder.build();
    }

    @Bean
    ObjectMapper sqsObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    SqsJsonMessenger sqsJsonMessenger(SqsClient sqsClient, ObjectMapper sqsObjectMapper) {
        return new SqsJsonMessenger(sqsClient, sqsObjectMapper);
    }
}
