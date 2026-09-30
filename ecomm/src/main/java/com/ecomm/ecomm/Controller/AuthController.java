package com.ecomm.ecomm.Controller;

import com.ecomm.ecomm.Model.User;
import com.ecomm.ecomm.Repository.UserRepository;
import com.ecomm.ecomm.Service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Auth endpoints using a token-based flow (no cookies) so the app works reliably
 * across sites and on mobile browsers that block third-party cookies.
 *
 * <ul>
 *   <li>Access token: returned in the response body; the client keeps it in memory
 *       and sends it as {@code Authorization: Bearer <token>}.</li>
 *   <li>Refresh token: returned in the response body; the client stores it (e.g.
 *       localStorage) and posts it back to {@code /refresh}. Refresh tokens are
 *       rotated on every use and protected by reuse detection.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${jwt.refresh-expiry}")
    private long refreshExpiry;

    public AuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    /**
     * Returns the current authenticated user's profile including role.
     * Authenticated via the Authorization: Bearer access token.
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
    public ResponseEntity<?> login(@AuthenticationPrincipal Jwt jwt) {
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

        return ResponseEntity.ok(issueTokens(user, Map.of("profileComplete", user.isProfileComplete())));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleAuth(@AuthenticationPrincipal Jwt jwt) {
        String uid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        Optional<User> optionalUser = userRepository.findByFirebaseUid(uid);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            return ResponseEntity.ok(issueTokens(user, Map.of("profileComplete", user.isProfileComplete())));
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
                                             @RequestBody Map<String, String> body) {
        String uid = jwt.getSubject();

        Optional<User> optionalUser = userRepository.findByFirebaseUid(uid);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found"));
        }

        User user = optionalUser.get();
        user.setPhone(body.get("phone"));
        user.setAddress(body.get("address"));
        user.setProfileComplete(true);

        return ResponseEntity.ok(issueTokens(user, Map.of("profileComplete", true)));
    }

    /**
     * Rotating refresh with reuse detection.
     * Client posts {"refreshToken": "..."}. On success a NEW refresh token is
     * returned and the old one is invalidated. If a token that does not match the
     * user's current refresh token is presented, we treat it as a stolen/replayed
     * token and revoke the whole chain (forcing re-login).
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String presented = body.get("refreshToken");
        if (presented == null || presented.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "No refresh token"));
        }

        // The presented token encodes a family id so we can locate the user even
        // after the token value itself has been rotated away.
        String familyId = jwtService.extractRefreshFamily(presented);
        if (familyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid refresh token"));
        }

        Optional<User> optionalUser = userRepository.findByRefreshTokenFamily(familyId);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid refresh token"));
        }

        User user = optionalUser.get();

        // Reuse detection: the presented token must match the current stored token.
        // A mismatch means an older (already-rotated) token was replayed -> revoke.
        if (user.getRefreshToken() == null || !user.getRefreshToken().equals(presented)) {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiry(null);
            user.setRefreshTokenFamily(null);
            userRepository.save(user);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh token reuse detected. Please login again."));
        }

        if (user.getRefreshTokenExpiry() == null
                || user.getRefreshTokenExpiry().isBefore(LocalDateTime.now())) {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiry(null);
            user.setRefreshTokenFamily(null);
            userRepository.save(user);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh token expired. Please login again."));
        }

        // Rotate: issue a new refresh token within the same family, plus a new access token.
        String accessToken = jwtService.generateAccessToken(user.getFirebaseUid(), user.getEmail());
        String newRefresh = jwtService.generateRefreshToken(familyId);
        user.setRefreshToken(newRefresh);
        user.setRefreshTokenExpiry(LocalDateTime.now().plus(Duration.ofMillis(refreshExpiry)));
        userRepository.save(user);

        Map<String, Object> resp = new HashMap<>();
        resp.put("accessToken", accessToken);
        resp.put("refreshToken", newRefresh);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(@RequestBody(required = false) Map<String, String> body) {
        String presented = body != null ? body.get("refreshToken") : null;
        if (presented != null && !presented.isBlank()) {
            String familyId = jwtService.extractRefreshFamily(presented);
            if (familyId != null) {
                userRepository.findByRefreshTokenFamily(familyId).ifPresent(user -> {
                    user.setRefreshToken(null);
                    user.setRefreshTokenExpiry(null);
                    user.setRefreshTokenFamily(null);
                    userRepository.save(user);
                });
            }
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // --- helpers ---

    /**
     * Issues a fresh access + refresh token pair (new family) for the given user,
     * persists refresh state, and merges the extra response fields.
     */
    private Map<String, Object> issueTokens(User user, Map<String, Object> extra) {
        String familyId = jwtService.generateRefreshFamily();
        String accessToken = jwtService.generateAccessToken(user.getFirebaseUid(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(familyId);

        user.setRefreshToken(refreshToken);
        user.setRefreshTokenFamily(familyId);
        user.setRefreshTokenExpiry(LocalDateTime.now().plus(Duration.ofMillis(refreshExpiry)));
        userRepository.save(user);

        Map<String, Object> resp = new HashMap<>(extra);
        resp.put("accessToken", accessToken);
        resp.put("refreshToken", refreshToken);
        return resp;
    }
}
