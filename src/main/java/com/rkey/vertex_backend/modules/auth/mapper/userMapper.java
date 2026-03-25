package com.rkey.vertex_backend.modules.auth.mapper;

import com.rkey.vertex_backend.modules.auth.entity.UserEntity;
import com.rkey.vertex_backend.modules.auth.model.dto.UserSummary;

public class userMapper {
    public UserSummary getUserSummary(UserEntity user){
        return new UserSummary
        (
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getUsername(),
            user.getAvatarUrl()
        );
    }
}
