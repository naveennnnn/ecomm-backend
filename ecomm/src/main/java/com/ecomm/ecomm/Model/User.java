package com.ecomm.ecomm.Model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String firebaseUid;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    private String address;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(nullable = false)
    private boolean profileComplete = false;

    private String refreshToken;

    private LocalDateTime refreshTokenExpiry;

    private LocalDateTime createdAt = LocalDateTime.now();
}
