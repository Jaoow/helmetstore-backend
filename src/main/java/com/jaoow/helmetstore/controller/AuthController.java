package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.config.CookieProperties;
import com.jaoow.helmetstore.dto.user.UserLoginRequest;
import com.jaoow.helmetstore.dto.user.UserRegisterRequest;
import com.jaoow.helmetstore.dto.user.UserResponse;
import com.jaoow.helmetstore.service.user.AuthService;
import com.jaoow.helmetstore.service.user.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final CookieProperties cookieProps;

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody UserLoginRequest userLoginRequest,
                                      HttpServletResponse response) {
        var tokens = authService.login(userLoginRequest);

        ResponseCookie accessCookie = ResponseCookie.from(cookieProps.getAccessName(), tokens.getAccessToken())
                .httpOnly(cookieProps.isHttpOnly())
                .secure(cookieProps.isSecure())
                .path("/")
                .maxAge(cookieProps.getAccessMaxAge())
                .sameSite(cookieProps.getSameSite())
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from(cookieProps.getRefreshName(), tokens.getRefreshToken())
                .httpOnly(cookieProps.isHttpOnly())
                .secure(cookieProps.isSecure())
                .path("/")
                .maxAge(cookieProps.getRefreshMaxAge())
                .sameSite(cookieProps.getSameSite())
                .build();

        response.addHeader("Set-Cookie", refreshCookie.toString());
        response.addHeader("Set-Cookie", accessCookie.toString());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Void> refreshToken(@CookieValue(name = "${app.auth.cookie.refresh-name}") String refreshToken,
                                             HttpServletResponse response) {
        var token = authService.refreshToken(refreshToken);

        ResponseCookie accessCookie = ResponseCookie.from(cookieProps.getAccessName(), token.getAccessToken())
                .httpOnly(cookieProps.isHttpOnly())
                .secure(cookieProps.isSecure())
                .path("/")
                .maxAge(cookieProps.getAccessMaxAge())
                .sameSite(cookieProps.getSameSite())
                .build();

        response.addHeader("Set-Cookie", accessCookie.toString());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        // Clear cookies by setting them to expire immediately
        ResponseCookie deleteAccess = ResponseCookie.from(cookieProps.getAccessName(), "")
                .path("/").maxAge(0).build();

        ResponseCookie deleteRefresh = ResponseCookie.from(cookieProps.getRefreshName(), "")
                .path("/").maxAge(0).build();

        response.addHeader("Set-Cookie", deleteAccess.toString());
        response.addHeader("Set-Cookie", deleteRefresh.toString());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return userService.self(principal);
    }

    @PostMapping("/register")
    public UserResponse register(@RequestBody UserRegisterRequest userRegisterRequest) {
        return userService.register(userRegisterRequest);
    }

    @PostMapping("/assign-role")
    public void assignRole(@RequestParam String email, @RequestParam String role) {
        userService.assignRoleToUser(email, role);
    }
}
