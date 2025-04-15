package com.jaoow.helmetstore.dto.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class RefreshTokenRequest {
    private String refreshToken;
}