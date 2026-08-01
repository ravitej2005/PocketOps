package com.pocketops.backend.auth;

import com.pocketops.backend.user.UserEntity;

public record UserResponse(
        String id,
        String email
) {
    static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}
