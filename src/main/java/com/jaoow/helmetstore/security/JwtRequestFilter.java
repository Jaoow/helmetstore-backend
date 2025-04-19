package com.jaoow.helmetstore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtRequestFilter(JwtService jwtService, HandlerExceptionResolver handlerExceptionResolver) {
        this.jwtService = jwtService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);
            if (jwt != null) {
                String userEmail = jwtService.extractUsername(jwt);
                if (userEmail != null && isAuthenticationAbsent()) {
                    processAuthentication(request, jwt, userEmail);
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }

    private void processAuthentication(HttpServletRequest request, String jwt, String userEmail) {
        List<SimpleGrantedAuthority> authorities = jwtService.extractAuthorities(jwt);
        var authToken = new UsernamePasswordAuthenticationToken(userEmail, null, authorities);

        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private boolean isAuthenticationAbsent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null;
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
