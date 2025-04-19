package com.jaoow.helmetstore.dto.user;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

@Data
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<String> roles;

}
