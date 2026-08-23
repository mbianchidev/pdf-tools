package com.pdftools.storage;

import com.pdftools.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "pdf.storage", name = "type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client client;
    private final String bucket;

    public S3StorageService(S3Client client, StorageProperties properties) {
        this.client = client;
        this.bucket = properties.getS3().getBucket();
        if (properties.getS3().isCreateBucket()) {
            ensureBucket();
        }
    }

    @Override
    public StoredObject put(
            String key,
            InputStream inputStream,
            long contentLength,
            String mediaType) throws IOException {
        if (contentLength < 0) {
            throw new IOException("S3 uploads require a known content length");
        }

        MessageDigest digest = sha256();
        try (DigestInputStream digestInput = new DigestInputStream(inputStream, digest)) {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(mediaType)
                    .build(),
                RequestBody.fromInputStream(digestInput, contentLength)
            );
        } catch (SdkException exception) {
            throw new IOException("Failed to store S3 object " + key, exception);
        }

        return new StoredObject(
            key,
            contentLength,
            HexFormat.of().formatHex(digest.digest()),
            mediaType
        );
    }

    @Override
    public StoredResource get(String key) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> response = client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build()
            );
            return new StoredResource(
                response,
                response.response().contentLength(),
                response.response().contentType()
            );
        } catch (SdkException exception) {
            throw new IOException("Failed to read S3 object " + key, exception);
        }
    }

    @Override
    public List<StoredObjectInfo> list(String prefix) throws IOException {
        try {
            return client.listObjectsV2Paginator(
                    ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build()
                )
                .contents()
                .stream()
                .map(object -> new StoredObjectInfo(object.key(), object.lastModified()))
                .sorted(Comparator.comparing(StoredObjectInfo::key))
                .toList();
        } catch (SdkException exception) {
            throw new IOException("Failed to list S3 objects under " + prefix, exception);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException exception) {
            throw new IOException("Failed to delete S3 object " + key, exception);
        }
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
