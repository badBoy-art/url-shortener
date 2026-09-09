package com.urlshortener.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.urlshortener.common.BusinessException;
import com.urlshortener.dto.CreateUserRequest;
import com.urlshortener.dto.ResetPasswordRequest;
import com.urlshortener.dto.UserResponse;
import com.urlshortener.entity.AdminUser;
import com.urlshortener.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public List<UserResponse> listUsers(int page, int size) {
        Page<AdminUser> result = adminUserMapper.selectPage(new Page<>(page, size),
                Wrappers.<AdminUser>lambdaQuery().orderByAsc(AdminUser::getId));
        return result.getRecords().stream().map(UserResponse::from).toList();
    }

    public long countUsers() {
        return adminUserMapper.selectCount(null);
    }

    public UserResponse createUser(CreateUserRequest request) {
        String account = request.account().trim();
        AdminUser existing = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getAccount, account));
        if (existing != null) {
            throw BusinessException.conflict("用户名 " + account + " 已存在");
        }
        AdminUser user = new AdminUser();
        user.setAccount(account);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setIsEnable(1);
        user.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(user);
        return UserResponse.from(user);
    }

    public void resetPassword(String account, ResetPasswordRequest request) {
        AdminUser user = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getAccount, account.trim()));
        if (user == null) {
            throw BusinessException.notFound("用户 " + account + " 不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        adminUserMapper.updateById(user);
    }
}
