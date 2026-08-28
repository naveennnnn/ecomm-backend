package com.ecomm.ecomm.Controller;

import com.ecomm.ecomm.Model.Product;
import com.ecomm.ecomm.Model.User;
import com.ecomm.ecomm.Repository.ProductRepository;
import com.ecomm.ecomm.Repository.UserRepository;
import com.ecomm.ecomm.Service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public ProductController(ProductRepository productRepository,
                             UserRepository userRepository,
                             StorageService storageService) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    /**
     * Get all active products — public endpoint
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productRepository.findByActiveTrue());
    }

    /**
     * Get products by category — public endpoint
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productRepository.findByCategoryAndActiveTrue(category));
    }

    /**
     * Search products — public endpoint
     */
    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam String q) {
        return ResponseEntity.ok(productRepository.findByNameContainingIgnoreCaseAndActiveTrue(q));
    }

    /**
     * Get single product — public endpoint
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        Optional<Product> product = productRepository.findById(id);
        if (product.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product.get());
    }

    /**
     * Create a product — ADMIN only
     */
    @PostMapping
    public ResponseEntity<?> createProduct(
            Authentication authentication,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam("originalPrice") BigDecimal originalPrice,
            @RequestParam("category") String category,
            @RequestParam("brand") String brand,
            @RequestParam("stock") int stock,
            @RequestParam("image") MultipartFile image) {

        // Check admin role
        String uid = (String) authentication.getPrincipal();
        Optional<User> optionalUser = userRepository.findByFirebaseUid(uid);
        if (optionalUser.isEmpty() || !"ADMIN".equals(optionalUser.get().getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Admin access required"));
        }

        try {
            String imageUrl = storageService.uploadFile(image);

            Product product = new Product();
            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);
            product.setOriginalPrice(originalPrice);
            product.setCategory(category);
            product.setBrand(brand);
            product.setStock(stock);
            product.setImageUrl(imageUrl);

            productRepository.save(product);

            return ResponseEntity.status(HttpStatus.CREATED).body(product);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create product: " + e.getMessage()));
        }
    }
}
