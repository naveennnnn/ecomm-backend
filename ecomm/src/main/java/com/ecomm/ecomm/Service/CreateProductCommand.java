package com.ecomm.ecomm.Service;

import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable input for creating a product. Decouples the service layer from the
 * web request binding so the service does not depend on HTTP-specific types
 * beyond the uploaded files.
 */
public record CreateProductCommand(
        String name,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        String category,
        String brand,
        int stock,
        List<MultipartFile> images
) {
}
