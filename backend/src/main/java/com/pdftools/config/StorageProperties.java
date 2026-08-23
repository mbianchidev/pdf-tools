package com.pdftools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "pdf.storage")
public class StorageProperties {

    private String type = "local";
    private Path localRoot = Path.of("/tmp/pdf-storage/jobs");
    private final S3 s3 = new S3();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Path getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(Path localRoot) {
        this.localRoot = localRoot;
    }

    public S3 getS3() {
        return s3;
    }

    public static class S3 {
        private String endpoint;
        private String region = "us-east-1";
        private String bucket = "pdf-tools";
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = true;
        private boolean createBucket;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }

        public boolean isCreateBucket() {
            return createBucket;
        }

        public void setCreateBucket(boolean createBucket) {
            this.createBucket = createBucket;
        }
    }
}
