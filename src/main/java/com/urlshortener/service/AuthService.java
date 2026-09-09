package com.urlshortener.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.urlshortener.common.BusinessException;
import com.urlshortener.config.AppProperties;
import com.urlshortener.config.RedisStateHolder;
import com.urlshortener.dto.AuthResponse;
import com.urlshortener.entity.AdminUser;
import com.urlshortener.mapper.AdminUserMapper;
import com.urlshortener.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 管理员登录：账号密码校验 + 失败锁定（防爆破，替代图片验证码）。
 * 同一账号或同一 IP 连续失败 loginMaxFails 次后锁定 loginLockMinutes 分钟。
 * Redis 不可用时锁定降级为不生效（fail-open），登录功能不中断。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserMapper adminUserMapper;
    private final JwtService jwtService;
    private final AppProperties props;
    private final StringRedisTemplate redis;
    private final RedisStateHolder redisState;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthResponse login(String account, String password, String ip) {
        String acc = account.trim();
        String failAccKey = "login:fail:acc:" + acc;
        String failIpKey = "login:fail:ip:" + ip;

        int fails = Math.max(getFails(failAccKey), getFails(failIpKey));
        if (fails >= props.auth().loginMaxFails()) {
            throw BusinessException.tooManyRequests(
                    "失败次数过多，账号已锁定，请 " + props.auth().loginLockMinutes() + " 分钟后再试");
        }

        AdminUser user = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getAccount, acc));
        if (user == null || user.getIsEnable() == null || user.getIsEnable() == 0
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            recordFail(failAccKey);
            recordFail(failIpKey);
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        clearFails(failAccKey);
        clearFails(failIpKey);
        return new AuthResponse(jwtService.issue(user.getAccount()), user.getAccount());
    }

    private int getFails(String key) {
        if (!redisState.isUp()) {
            return 0;
        }
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception e) {
            redisState.markDown(e);
            return 0;
        }
    }

    private void recordFail(String key) {
        if (!redisState.isUp()) {
            return;
        }
        try {
            Long count = redis.opsForValue().increment(key);
            // 失败计数窗口 = 锁定时长，窗口内达到阈值即锁定，窗口过期自动解锁
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofMinutes(props.auth().loginLockMinutes()));
            }
        } catch (Exception e) {
            redisState.markDown(e);
        }
    }

    private void clearFails(String key) {
        if (!redisState.isUp()) {
            return;
        }
        try {
            redis.delete(key);
        } catch (Exception e) {
            redisState.markDown(e);
        }
    }
}
