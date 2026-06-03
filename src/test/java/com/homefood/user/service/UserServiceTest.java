package com.homefood.user.service;

import com.homefood.user.dto.LoginRequest;
import com.homefood.user.dto.RegisterRequest;
import com.homefood.user.entity.User;
import com.homefood.user.entity.UserRole;
import com.homefood.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .passwordHash("$2a$12$hashedpassword")
                .role(UserRole.BUYER)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPassword("Password123!");
        registerRequest.setRole(UserRole.BUYER);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("Password123!");
    }

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUser() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiry()).thenReturn(900000L);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(null);

        var response = userService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).save(any(User.class));
        verify(kafkaTemplate).send(eq("notification.send"), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowExceptionForDuplicateEmail() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        when(userRepository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiry()).thenReturn(900000L);
        when(userRepository.save(any())).thenReturn(testUser);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(null);

        var response = userService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).updateLastLogin(any(), any());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException for wrong password")
    void shouldThrowForWrongPassword() {
        when(userRepository.findByEmailAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository).incrementFailedAttempts(testUser.getId());
    }

    @Test
    @DisplayName("Should send OTP and store in Redis")
    void shouldSendOtp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(null);

        String result = userService.sendOtp("+919876543210");

        assertThat(result).isEqualTo("OTP sent successfully");
        verify(valueOperations).set(startsWith("otp:"), anyString(), eq(5L), any());
    }

    @Test
    @DisplayName("Should get user by ID")
    void shouldGetUserById() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        User result = userService.getUserById(testUser.getId());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testUser.getId());
    }
}
