package com.ecomm.ecomm.Service;

import com.ecomm.ecomm.Model.Product;
import com.ecomm.ecomm.Repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link ProductService} implementation.
 * <p>
 * Depends only on abstractions: the {@link ProductRepository} for persistence and
 * the {@link MediaStorageService} for image storage (DIP). Owns the create-product
 * orchestration so the controller stays free of business logic (SRP).
 */
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final MediaStorageService mediaStorageService;

    public ProductServiceImpl(ProductRepository productRepository,
                              MediaStorageService mediaStorageService) {
        this.productRepository = productRepository;
        this.mediaStorageService = mediaStorageService;
    }

    @Override
    public List<Product> getActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    @Override
    public List<Product> getByCategory(String category) {
        return productRepository.findByCategoryAndActiveTrue(category);
    }

    @Override
    public List<Product> searchByName(String query) {
        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(query);
    }

    @Override
    public Optional<Product> getById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    public Product createProduct(CreateProductCommand command) throws IOException {
        String imageUrl = mediaStorageService.uploadFile(command.image());

        Product product = new Product();
        product.setName(command.name());
        product.setDescription(command.description());
        product.setPrice(command.price());
        product.setOriginalPrice(command.originalPrice());
        product.setCategory(command.category());
        product.setBrand(command.brand());
        product.setStock(command.stock());
        product.setImageUrl(imageUrl);

        return productRepository.save(product);
    }
}
