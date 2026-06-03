package com.homefood.user.service;

import com.homefood.user.dto.AuthResponse;
import com.homefood.user.dto.LoginRequest;
import com.homefood.user.dto.OtpRequest;
import com.homefood.user.dto.RegisterRequest;
import com.homefood.user.entity.User;
import com.homefood.user.entity.UserRole;
import com.homefood.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(request.getRole())
                .businessName(request.getBusinessName())
                .fssaiLicenseNumber(request.getFssaiLicenseNumber())
                .vehicleType(request.getVehicleType())
                .vehicleNumber(request.getVehicleNumber())
                .build();

        user = userRepository.save(user);

        // Send welcome notification
        kafkaTemplate.send("notification.send", user.getId().toString(),
                String.format("{\"type\":\"WELCOME\",\"userId\":\"%s\",\"email\":\"%s\",\"firstName\":\"%s\"}",
                        user.getId(), user.getEmail(), user.getFirstName()));

        // Send OTP if phone provided
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            sendOtp(request.getPhone());
        }

        log.info("User registered: {} with role {}", user.getEmail(), user.getRole());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account is locked due to too many failed attempts");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            userRepository.incrementFailedAttempts(user.getId());
            if (user.getFailedLoginAttempts() + 1 >= 5) {
                userRepository.lockAccount(user.getId());
                throw new LockedException("Account locked after 5 failed attempts");
            }
            throw new BadCredentialsException("Invalid password");
        }

        userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

        kafkaTemplate.send("notification.send", user.getId().toString(),
                String.format("{\"type\":\"LOGIN\",\"userId\":\"%s\",\"email\":\"%s\"}", user.getId(), user.getEmail()));

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public String sendOtp(String phone) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        String redisKey = "otp:" + phone;
        redisTemplate.opsForValue().set(redisKey, otp, 5, TimeUnit.MINUTES);

        // Send SMS via Kafka
        kafkaTemplate.send("notification.send", phone,
                String.format("{\"type\":\"OTP\",\"phone\":\"%s\",\"otp\":\"%s\"}", phone, otp));

        log.info("OTP sent to phone: {}", phone.replaceAll("(\\d{3})\\d{4}(\\d{3})", "$1****$2"));
        return "OTP sent successfully";
    }

    @Transactional
    public AuthResponse verifyOtp(OtpRequest request) {
        String redisKey = "otp:" + request.getPhone();
        String storedOtp = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null) {
            throw new IllegalArgumentException("OTP expired or not found");
        }
        if (!storedOtp.equals(request.getOtp())) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        redisTemplate.delete(redisKey);

        User user = userRepository.findByPhoneAndDeletedAtIsNull(request.getPhone())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + request.getPhone()));

        user.setPhoneVerified(true);
        userRepository.save(user);
        userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        User user = userRepository.findAll().stream()
                .filter(u -> refreshToken.equals(u.getRefreshToken()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }

    @Transactional
    public User updateUser(UUID id, User updatedUser) {
        User user = getUserById(id);
        user.setFirstName(updatedUser.getFirstName());
        user.setLastName(updatedUser.getLastName());
        user.setPhone(updatedUser.getPhone());
        user.setAddressLine1(updatedUser.getAddressLine1());
        user.setAddressLine2(updatedUser.getAddressLine2());
        user.setCity(updatedUser.getCity());
        user.setState(updatedUser.getState());
        user.setPincode(updatedUser.getPincode());
        user.setCountry(updatedUser.getCountry());
        user.setLatitude(updatedUser.getLatitude());
        user.setLongitude(updatedUser.getLongitude());
        if (user.getRole() == UserRole.SELLER) {
            user.setBusinessName(updatedUser.getBusinessName());
            user.setFssaiLicenseNumber(updatedUser.getFssaiLicenseNumber());
        }
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        userRepository.softDelete(id, LocalDateTime.now());
    }

    public List<User> getAvailableDeliveryAgents(String city) {
        return userRepository.findAvailableDeliveryAgentsInCity(city);
    }

    @Override
    public User loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiry() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }
}
