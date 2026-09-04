package com.ecomm.ecomm.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Media storage backed by Cloudinary.
 * <p>
 * Default provider. Activated when {@code storage.provider=cloudinary} (or unset,
 * since it is treated as the default via {@code matchIfMissing = true}).
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "cloudinary", matchIfMissing = true)
public class CloudinaryStorageService implements MediaStorageService {

    private static final String FOLDER = "products";

    private final Cloudinary cloudinary;

    public CloudinaryStorageService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {

        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    @Override
    public String uploadFile(MultipartFile file) throws IOException {
        String publicId = FOLDER + "/" + UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "public_id", publicId,
                        "resource_type", "image",
                        "overwrite", false
                ));

        Object secureUrl = result.get("secure_url");
        if (secureUrl == null) {
            throw new IOException("Cloudinary upload did not return a secure_url");
        }
        return secureUrl.toString();
    }

    @Override
    public void deleteFile(String fileUrl) throws IOException {
        String publicId = extractPublicId(fileUrl);
        if (publicId == null) {
            return;
        }
        cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
    }

    /**
     * Derives the Cloudinary public_id (e.g. {@code products/uuid}) from a delivery URL.
     * Cloudinary URLs look like:
     * {@code https://res.cloudinary.com/<cloud>/image/upload/v123/products/uuid.png}
     */
    private String extractPublicId(String fileUrl) {
        if (fileUrl == null) {
            return null;
        }
        int uploadIdx = fileUrl.indexOf("/upload/");
        if (uploadIdx < 0) {
            return null;
        }
        String afterUpload = fileUrl.substring(uploadIdx + "/upload/".length());

        // Strip an optional version segment like "v1699999999/"
        if (afterUpload.startsWith("v")) {
            int slash = afterUpload.indexOf('/');
            if (slash > 0 && afterUpload.substring(1, slash).chars().allMatch(Character::isDigit)) {
                afterUpload = afterUpload.substring(slash + 1);
            }
        }

        // Strip the file extension
        int dot = afterUpload.lastIndexOf('.');
        return dot > 0 ? afterUpload.substring(0, dot) : afterUpload;
    }
}
