package com.jaoow.helmetstore.service.user;


import com.jaoow.helmetstore.dto.user.RefreshTokenResponse;
import com.jaoow.helmetstore.dto.user.UserLoginRequest;
import com.jaoow.helmetstore.dto.user.UserLoginResponse;
import com.jaoow.helmetstore.model.user.RefreshToken;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.repository.user.RefreshTokenRepository;
import com.jaoow.helmetstore.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final UserService userService;

    public UserLoginResponse login(UserLoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(), loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails principal = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateToken(principal);
        String refreshToken = tokenService.createRefreshToken(principal).getToken();

        return new UserLoginResponse(accessToken, refreshToken);
    }

    public RefreshTokenResponse refreshToken(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .map(tokenService::verifyExpiration)
                .orElseThrow(() -> new JwtException("Invalid refresh token"));

        User user = token.getUser();
        
        // Revoga o refresh token antigo (security best practice)
        refreshTokenRepository.delete(token);
        
        UserDetails userDetails = userService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtService.generateToken(userDetails);
        
        // Gera um NOVO refresh token (token rotation)
        String newRefreshToken = tokenService.createRefreshToken(userDetails).getToken();

        return new RefreshTokenResponse(newAccessToken, newRefreshToken);
    }

}
