package com.homefood.user.dto;

import com.homefood.user.entity.UserRole;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role;
}
