package com.jaoow.helmetstore.repository.user;

import com.jaoow.helmetstore.model.user.RefreshToken;
import com.jaoow.helmetstore.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUser(User user);
}
