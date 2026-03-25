package com.rkey.vertex_backend.modules.auth.mapper;

import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserSummary getUserSummary(UserEntity user) {
        return new UserSummary(
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getUsername(),
            user.getAvatarUrl()
        );
    }
}