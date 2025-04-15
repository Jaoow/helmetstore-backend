package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.user.*;
import com.jaoow.helmetstore.service.user.AuthService;
import com.jaoow.helmetstore.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/login")
    public UserLoginResponse login(@RequestBody UserLoginRequest userLoginRequest) {
        return authService.login(userLoginRequest);
    }

    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return userService.self(principal);
    }

    @PostMapping("/register")
    public UserResponse register(@RequestBody UserRegisterRequest userRegisterRequest) {
        return userService.register(userRegisterRequest);
    }

    @PostMapping("/refresh-token")
    public RefreshTokenResponse refreshToken(@RequestBody RefreshTokenRequest request) {
        return authService.refreshToken(request.getRefreshToken());
    }

    @PostMapping("/assign-role")
    public void assignRole(@RequestParam String email, @RequestParam String role) {
        userService.assignRoleToUser(email, role);
    }
}
