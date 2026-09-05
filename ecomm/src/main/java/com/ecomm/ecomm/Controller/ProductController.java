package com.ecomm.ecomm.Controller;

import com.ecomm.ecomm.Model.Product;
import com.ecomm.ecomm.Service.CreateProductCommand;
import com.ecomm.ecomm.Service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * HTTP layer for products. Delegates all business logic to {@link ProductService}
 * (SRP + DIP) and relies on method security for authorization (OCP) rather than
 * hand-rolled role checks.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get all active products — public endpoint
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productService.getActiveProducts());
    }

    /**
     * Get the latest 10 active products — public endpoint (used by the home carousel)
     */
    @GetMapping("/latest")
    public ResponseEntity<List<Product>> getLatestProducts() {
        return ResponseEntity.ok(productService.getLatestProducts());
    }

    /**
     * Get products by category — public endpoint
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productService.getByCategory(category));
    }

    /**
     * Search products — public endpoint
     */
    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam String q) {
        return ResponseEntity.ok(productService.searchByName(q));
    }

    /**
     * Get single product — public endpoint
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return productService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create a product — ADMIN only (enforced by method security).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createProduct(
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam BigDecimal price,
            @RequestParam BigDecimal originalPrice,
            @RequestParam String category,
            @RequestParam String brand,
            @RequestParam int stock,
            @RequestParam("images") List<MultipartFile> images) throws Exception {

        CreateProductCommand command = new CreateProductCommand(
                name, description, price, originalPrice, category, brand, stock, images);

        Product product = productService.createProduct(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }
}
