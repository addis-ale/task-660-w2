package com.heritage.marketplace.user;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findFirstByRole(UserRole role);

    List<User> findByStatusAndDeletionRequestedAtBefore(UserStatus status, LocalDateTime threshold);
}
