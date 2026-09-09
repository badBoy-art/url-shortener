package com.urlshortener.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.urlshortener.config.AppProperties;
import com.urlshortener.entity.AdminUser;
import com.urlshortener.mapper.AdminUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理接口鉴权判定：X-API-Key 与 Bearer JWT 任一有效即通过。
 * JWT 除验签外还核对账号存在且启用，账号被禁用后旧 token 立即失效。
 */
@Component
@RequiredArgsConstructor
public class AdminAccess {

    private final AppProperties props;
    private final JwtService jwtService;
    private final AdminUserMapper adminUserMapper;

    public boolean isAuthorized(HttpServletRequest request) {
        if (validApiKey(request.getHeader("X-API-Key"))) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String account = jwtService.parseAccount(auth.substring(7).trim());
            if (account != null) {
                AdminUser user = adminUserMapper.selectOne(
                        Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getAccount, account));
                if (user != null && (user.getIsEnable() == null || user.getIsEnable() == 1)) {
                    request.setAttribute("adminAccount", account);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean validApiKey(String provided) {
        return provided != null && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                props.apiKey().getBytes(StandardCharsets.UTF_8));
    }
}
