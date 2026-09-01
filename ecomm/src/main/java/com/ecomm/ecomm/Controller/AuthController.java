package com.ecomm.ecomm.Controller;

import com.ecomm.ecomm.Model.User;
import com.ecomm.ecomm.Repository.UserRepository;
import com.ecomm.ecomm.Service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${jwt.access-expiry}")
    private long accessExpiry;

    @Value("${jwt.refresh-expiry}")
    private long refreshExpiry;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    /**
     * Returns the current authenticated user's profile including role.
     * Authenticated via the access_token cookie (JwtCookieFilter sets principal to firebase UID).
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }

        String uid = (String) authentication.getPrincipal();
        Optional<User> optionalUser = userRepository.findByFirebaseUid(uid);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        User user = optionalUser.get();
        return ResponseEntity.ok(Map.of(
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "profileComplete", user.isProfileComplete()
        ));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> body) {
        String uid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        if (userRepository.findByFirebaseUid(uid).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "User already exists"));
        }

        User user = new User();
        user.setFirebaseUid(uid);
        user.setEmail(email);
        user.setName(body.get("name"));
        user.setPhone(body.get("phone"));
        user.setAddress(body.get("address"));
        user.setVerified(false);
        user.setProfileComplete(true);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User registered. Please verify your email."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        String uid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaim("email_verified");

        if (emailVerified == null || !emailVerified) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Email not verified"));
        }

        Optional<User> optionalUser = userRepository.findByFirebaseUid(uid);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found. Please sign up first."));
        }

        User user = optionalUser.get();
        user.setVerified(true);

        String accessToken = jwtService.generateAccessToken(uid, email);
        String refreshToken = jwtService.generateRefreshToken();
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiry(LocalDateTime.now().plus(Duration.ofMillis(refreshExpiry)));
        userRepository.save(user);

        addTokenCookies(response, accessToken, refreshToken);

        return ResponseEntity.ok(Map.of("profileComplete", user.isProfileComplete()));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleAuth(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        String uid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        Optional<User> optionalUser = userRepository.findByFirebaseUid(uid);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            String accessToken = jwtService.generateAccessToken(uid, email);
            String refreshToken = jwtService.generateRefreshToken();
            user.setRefreshToken(refreshToken);
            user.setRefreshTokenExpiry(LocalDateTime.now().plus(Duration.ofMillis(refreshExpiry)));
            userRepository.save(user);

            addTokenCookies(response, accessToken, refreshToken);

            return ResponseEntity.ok(Map.of("profileComplete", user.isProfileComplete()));
        } else {
            User user = new User();
            user.setFirebaseUid(uid);
            user.setEmail(email);
            user.setName(name != null ? name : "");
            user.setVerified(true);
            user.setProfileComplete(false);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "profileComplete", false,
                    "message", "Please complete your profile"
            ));
        }
    }

    @PostMapping("/complete-profile")
    public ResponseEntity<?> completeProfile(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody Map<String, String> body,
                                             HttpServletResponse response) {
        String uid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        Optional<User> optionalUser = userRepository.findByFirebaseUid(uid);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        User user = optionalUser.get();
        user.setPhone(body.get("phone"));
        user.setAddress(body.get("address"));
        user.setProfileComplete(true);

        String accessToken = jwtService.generateAccessToken(uid, email);
        String refreshToken = jwtService.generateRefreshToken();
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiry(LocalDateTime.now().plus(Duration.ofMillis(refreshExpiry)));
        userRepository.save(user);

        addTokenCookies(response, accessToken, refreshToken);

        return ResponseEntity.ok(Map.of("profileComplete", true));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refresh_token");

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "No refresh token"));
        }

        Optional<User> optionalUser = userRepository.findByRefreshToken(refreshToken);
        if (optionalUser.isEmpty()) {
            clearTokenCookies(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid refresh token"));
        }

        User user = optionalUser.get();

        if (user.getRefreshTokenExpiry().isBefore(LocalDateTime.now())) {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiry(null);
            userRepository.save(user);
            clearTokenCookies(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh token expired. Please login again."));
        }

        String accessToken = jwtService.generateAccessToken(user.getFirebaseUid(), user.getEmail());
        addAccessTokenCookie(response, accessToken);

        return ResponseEntity.ok(Map.of("message", "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refresh_token");

        if (refreshToken != null) {
            Optional<User> optionalUser = userRepository.findByRefreshToken(refreshToken);
            optionalUser.ifPresent(user -> {
                user.setRefreshToken(null);
                user.setRefreshTokenExpiry(null);
                userRepository.save(user);
            });
        }

        clearTokenCookies(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // --- Cookie helpers ---

    private void addTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        addAccessTokenCookie(response, accessToken);

        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(cookieSecure);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge((int) (refreshExpiry / 1000));
        response.addCookie(refreshCookie);
    }

    private void addAccessTokenCookie(HttpServletResponse response, String accessToken) {
        Cookie accessCookie = new Cookie("access_token", accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(cookieSecure);
        accessCookie.setPath("/");
        accessCookie.setMaxAge((int) (accessExpiry / 1000));
        response.addCookie(accessCookie);
    }

    private void clearTokenCookies(HttpServletResponse response) {
        Cookie accessCookie = new Cookie("access_token", "");
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(cookieSecure);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie("refresh_token", "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(cookieSecure);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
