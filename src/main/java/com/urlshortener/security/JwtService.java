package com.urlshortener.security;

import com.urlshortener.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）。密钥来自 app.auth.jwt-secret，生产环境必须用环境变量覆盖。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final int expireHours;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.auth().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expireHours = props.auth().jwtExpireHours();
    }

    public String issue(String account) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(account)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** 校验签名与有效期，返回 token 中的账号；无效/过期返回 null */
    public String parseAccount(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
