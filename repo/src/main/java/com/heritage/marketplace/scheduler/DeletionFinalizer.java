package com.heritage.marketplace.scheduler;

import com.heritage.marketplace.audit.AuditService;
import com.heritage.marketplace.common.security.HashingUtil;
import com.heritage.marketplace.common.util.EncryptionUtil;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserStatus;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeletionFinalizer {

    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;
    private final HashingUtil hashingUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final int graceDays;

    public DeletionFinalizer(
        UserRepository userRepository,
        EncryptionUtil encryptionUtil,
        HashingUtil hashingUtil,
        PasswordEncoder passwordEncoder,
        AuditService auditService,
        @Value("${app.scheduler.deletion-finalizer.grace-days:30}") int graceDays
    ) {
        this.userRepository = userRepository;
        this.encryptionUtil = encryptionUtil;
        this.hashingUtil = hashingUtil;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.graceDays = graceDays;
    }

    @Scheduled(cron = "${app.scheduler.deletion-finalizer.cron:0 0 4 * * *}")
    @Transactional
    public void finalizePendingDeletions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusDays(graceDays);

        for (User user : userRepository.findByStatusAndDeletionRequestedAtBefore(UserStatus.PENDING_DELETION, threshold)) {
            String hash = hashingUtil.sha256Hex(user.getId() + ":" + now + ":" + user.getEmail());
            String anonymousEmail = "deleted+" + hash.substring(0, 24) + "@anonymized.invalid";

            Map<String, Object> before = Map.of(
                "status", user.getStatus().name(),
                "deletionRequestedAt", user.getDeletionRequestedAt()
            );

            user.setEmail(encryptionUtil.encryptDeterministic(anonymousEmail));
            user.setPhone(null);
            user.setDisplayName("Deleted User");
            user.setPasswordHash(passwordEncoder.encode(hash));
            user.setStatus(UserStatus.DELETED);
            user.setDeletionRequestedAt(null);
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);
            user.setUpdatedAt(now);
            userRepository.save(user);

            auditService.log(
                "USER",
                user.getId(),
                "FINALIZE_DELETION",
                null,
                before,
                Map.of("status", UserStatus.DELETED.name()),
                "system"
            );
        }
    }
}
