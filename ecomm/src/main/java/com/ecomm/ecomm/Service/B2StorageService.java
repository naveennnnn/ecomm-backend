package com.ecomm.ecomm.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * S3-compatible storage backed by Backblaze B2.
 * <p>
 * Kept behind the {@link MediaStorageService} abstraction so the app can switch to
 * Cloudflare R2 (also S3-compatible) later with only configuration changes. Activated
 * when {@code storage.provider=b2}.
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "b2")
public class B2StorageService implements MediaStorageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicBaseUrl;

    public B2StorageService(
            @Value("${b2.endpoint}") String endpoint,
            @Value("${b2.key-id}") String keyId,
            @Value("${b2.application-key}") String applicationKey,
            @Value("${b2.bucket-name}") String bucketName,
            @Value("${b2.public-base-url}") String publicBaseUrl) {

        this.bucketName = bucketName;
        this.publicBaseUrl = publicBaseUrl;

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1) // B2 doesn't use regions but SDK requires one
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(keyId, applicationKey)))
                .forcePathStyle(true)
                .build();
    }

    @Override
    public String uploadFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String key = "products/" + UUID.randomUUID() + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

        return publicBaseUrl + "/" + key;
    }

    @Override
    public void deleteFile(String fileUrl) {
        String key = extractKey(fileUrl);
        if (key == null) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    /**
     * Extracts the object key (e.g. {@code products/uuid.png}) from a public URL.
     */
    private String extractKey(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(publicBaseUrl)) {
            return null;
        }
        String remainder = fileUrl.substring(publicBaseUrl.length());
        return remainder.startsWith("/") ? remainder.substring(1) : remainder;
    }
}
