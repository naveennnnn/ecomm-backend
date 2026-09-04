package com.ecomm.ecomm.Service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Abstraction over the media storage/CDN provider.
 * <p>
 * Implementations are selected at runtime via the {@code storage.provider} property,
 * so swapping providers (e.g. Cloudinary -> Cloudflare R2) is a configuration change
 * rather than an application-wide rewrite.
 */
public interface MediaStorageService {

    /**
     * Uploads a file and returns a publicly accessible URL for it.
     *
     * @param file the uploaded multipart file
     * @return a public URL pointing to the stored object
     * @throws IOException if the upload fails
     */
    String uploadFile(MultipartFile file) throws IOException;

    /**
     * Deletes a previously uploaded file.
     *
     * @param fileUrl the public URL that was returned by {@link #uploadFile(MultipartFile)}
     * @throws IOException if the delete fails
     */
    void deleteFile(String fileUrl) throws IOException;
}
