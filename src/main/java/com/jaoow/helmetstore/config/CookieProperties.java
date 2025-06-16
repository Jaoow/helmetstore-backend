package com.jaoow.helmetstore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.auth.cookie")
public class CookieProperties {
    private String accessName = "access_token";
    private String refreshName = "refresh_token";
    private int accessMaxAge = 60 * 15;      // 15 min
    private int refreshMaxAge = 60 * 60 * 24 * 7; // 7 dias
    private boolean httpOnly = true;
    private boolean secure = true;
    private String sameSite = "Lax";         // "Strict", "None" ou "Lax"
}
