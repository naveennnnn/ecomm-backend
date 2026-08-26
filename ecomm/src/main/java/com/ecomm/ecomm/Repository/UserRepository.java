package com.ecomm.ecomm.Repository;

import com.ecomm.ecomm.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByFirebaseUid(String firebaseUid);
    Optional<User> findByRefreshToken(String refreshToken);
}
