package com.scheduler.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

@Configuration
@Slf4j
public class SqsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.sqs.enabled:false}")
    private boolean sqsEnabled;

    @Value("${aws.accessKeyId:}")
    private String accessKeyId;

    @Value("${aws.secretAccessKey:}")
    private String secretAccessKey;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        if (!sqsEnabled) {
            log.info("SQS is disabled. Using PostgreSQL polling instead.");
            return null;
        }

        log.info("Initializing AWS SQS client for region: {}", awsRegion);

        AwsCredentialsProvider credentialsProvider;

        // Use explicit credentials if provided, otherwise use default chain
        if (accessKeyId != null && !accessKeyId.isEmpty() &&
            secretAccessKey != null && !secretAccessKey.isEmpty()) {
            log.info("Using explicit AWS credentials");
            credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            );
        } else {
            log.info("Using default AWS credentials chain (IAM role, env vars, etc.)");
            credentialsProvider = DefaultCredentialsProvider.create();
        }

        // Configure HTTP client with increased timeouts
        var httpClient = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(30))      // Connection timeout: 30 seconds
                .connectionAcquisitionTimeout(Duration.ofSeconds(60))  // Wait for connection from pool
                .readTimeout(Duration.ofSeconds(60))            // Read timeout: 60 seconds
                .writeTimeout(Duration.ofSeconds(60))           // Write timeout: 60 seconds
                .maxConcurrency(50)                             // Max concurrent requests
                .build();

        // Configure client with retry policy
        ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(90))         // Total API call timeout
                .apiCallAttemptTimeout(Duration.ofSeconds(30))  // Per-attempt timeout
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(3)                          // Retry up to 3 times
                        .build())
                .build();

        return SqsAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .httpClient(httpClient)
                .overrideConfiguration(clientConfig)
                .build();
    }

    @Bean
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
        if (!sqsEnabled || sqsAsyncClient == null) {
            return null;
        }

        // Configure ObjectMapper to handle Java 8 date/time types
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        return SqsTemplate.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configureDefaultConverter(converter -> {
                    converter.setObjectMapper(objectMapper);
                })
                .build();
    }

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient) {
        if (!sqsEnabled || sqsAsyncClient == null) {
            return null;
        }

        return SqsMessageListenerContainerFactory
                .builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        // Max number of messages to fetch at once
                        .maxMessagesPerPoll(10)
                        // How long to wait for messages (long polling)
                        .pollTimeout(Duration.ofSeconds(10))
                        // Max concurrent messages being processed
                        .maxConcurrentMessages(30)
                        // Visibility timeout extension
                        .messageVisibility(Duration.ofSeconds(30))
                )
                .build();
    }
}

