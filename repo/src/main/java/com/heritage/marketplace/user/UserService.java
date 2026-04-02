package com.heritage.marketplace.user;

import com.heritage.marketplace.auth.dto.MeResponse;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.util.EncryptionUtil;
import com.heritage.marketplace.common.util.PhoneMaskingUtil;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;
    private final PhoneMaskingUtil phoneMaskingUtil;

    public UserService(UserRepository userRepository, EncryptionUtil encryptionUtil, PhoneMaskingUtil phoneMaskingUtil) {
        this.userRepository = userRepository;
        this.encryptionUtil = encryptionUtil;
        this.phoneMaskingUtil = phoneMaskingUtil;
    }

    public User getRequiredById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
    }

    public MeResponse toMeResponse(User user, UserRole callerRole) {
        String decryptedEmail = encryptionUtil.decrypt(user.getEmail());
        String decryptedPhone = encryptionUtil.decrypt(user.getPhone());

        String phone = callerRole == UserRole.ADMIN ? decryptedPhone : phoneMaskingUtil.mask(decryptedPhone);

        return new MeResponse(
            user.getId(),
            decryptedEmail,
            user.getDisplayName(),
            phone,
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt()
        );
    }
}
