package com.homefood.user.repository;

import com.homefood.user.entity.User;
import com.homefood.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<User> findByRoleAndEnabledTrueAndDeletedAtIsNull(UserRole role);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :time, u.failedLoginAttempts = 0 WHERE u.id = :id")
    void updateLastLogin(@Param("id") UUID id, @Param("time") LocalDateTime time);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    void incrementFailedAttempts(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE User u SET u.accountNonLocked = false WHERE u.failedLoginAttempts >= 5 AND u.id = :id")
    void lockAccount(@Param("id") UUID id);

    @Query("SELECT u FROM User u WHERE u.role = 'DELIVERY_AGENT' AND u.availableForDelivery = true AND u.city = :city AND u.deletedAt IS NULL")
    List<User> findAvailableDeliveryAgentsInCity(@Param("city") String city);

    @Modifying
    @Query("UPDATE User u SET u.deletedAt = :time, u.enabled = false WHERE u.id = :id")
    void softDelete(@Param("id") UUID id, @Param("time") LocalDateTime time);
}
