package com.heritage.marketplace.auth;

import com.heritage.marketplace.user.UserRole;
import java.util.UUID;

public interface AuthenticatedUser {

    UUID userId();

    UserRole role();
}
