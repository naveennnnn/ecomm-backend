package com.ecomm.ecomm.Service;

import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * Immutable input for creating a product. Decouples the service layer from the
 * web request binding so the service does not depend on HTTP-specific types
 * beyond the uploaded file.
 */
public record CreateProductCommand(
        String name,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        String category,
        String brand,
        int stock,
        MultipartFile image
) {
}
