package com.jaoow.helmetstore.service.user;

import com.jaoow.helmetstore.exception.InvalidTokenException;
import com.jaoow.helmetstore.model.user.RefreshToken;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.repository.user.RefreshTokenRepository;
import com.jaoow.helmetstore.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${security.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Transactional
    public RefreshToken createRefreshToken(UserDetails details) {
        User user = userRepository.findByEmail(details.getUsername()).orElseThrow(
                () -> new UsernameNotFoundException("User not found with email: " + details.getUsername())
        );

        RefreshToken refreshToken = refreshTokenRepository.findByUser(user)
                .orElse(new RefreshToken());

        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMs));
        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new InvalidTokenException("Refresh token expired. Please login again.");
        }
        return token;
    }
}
