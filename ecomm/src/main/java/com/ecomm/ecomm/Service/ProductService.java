package com.ecomm.ecomm.Service;

import com.ecomm.ecomm.Model.Product;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Application service that owns product-related use cases.
 * <p>
 * The controller depends on this abstraction (DIP) and is left with only HTTP
 * concerns, while orchestration of persistence and media storage lives here (SRP).
 */
public interface ProductService {

    List<Product> getActiveProducts();

    List<Product> getLatestProducts();

    List<Product> getByCategory(String category);

    List<Product> searchByName(String query);

    Optional<Product> getById(Long id);

    /**
     * Creates a product, uploading its image via the configured media storage provider.
     *
     * @param command the validated product data plus the raw image
     * @return the persisted product
     * @throws IOException if the image upload fails
     */
    Product createProduct(CreateProductCommand command) throws IOException;
}
