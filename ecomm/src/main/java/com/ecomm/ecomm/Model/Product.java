package com.ecomm.ecomm.Model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    private String category;

    private String brand;

    @Column(nullable = false)
    private int stock = 0;

    /**
     * Primary/thumbnail image. Kept for backward compatibility and used by list
     * views (carousel, grid). This is the first image of {@link #imageUrls}.
     */
    private String imageUrl;

    /**
     * All images for the product (gallery). Stored in a separate
     * {@code product_images} table via an element collection.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", length = 1000)
    private List<String> imageUrls = new ArrayList<>();

    private double rating = 0.0;

    private int reviewCount = 0;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}
