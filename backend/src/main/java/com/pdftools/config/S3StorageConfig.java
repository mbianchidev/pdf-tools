package com.pdftools.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(prefix = "pdf.storage", name = "type", havingValue = "s3")
public class S3StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        if (isBlank(s3.getEndpoint()) || isBlank(s3.getAccessKey()) || isBlank(s3.getSecretKey())) {
            throw new IllegalStateException(
                "S3 storage requires endpoint, access key, and secret key configuration"
            );
        }

        return S3Client.builder()
            .endpointOverride(URI.create(s3.getEndpoint()))
            .region(Region.of(s3.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())
            ))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(s3.isPathStyleAccess())
                .build())
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
