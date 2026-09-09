package com.urlshortener.config;

import com.urlshortener.entity.AdminUser;
import com.urlshortener.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动时的 Schema 兜底：
 * 1. 老库（CREATE TABLE IF NOT EXISTS 不生效的已有表）补齐 open_type 列；
 * 2. 管理员表为空时初始化默认账号（密码 BCrypt，账号密码见 app.admin.init-* 配置）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AdminUserMapper adminUserMapper;
    private final AppProperties props;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        ensureOpenTypeColumn();
        seedDefaultAdmin();
    }

    private void ensureOpenTypeColumn() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_short_link' AND COLUMN_NAME = 'open_type'",
                Integer.class);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE t_short_link ADD COLUMN open_type TINYINT NOT NULL DEFAULT 0 "
                    + "COMMENT '打开方式 0全部 1微信 2钉钉 3iPhone 4Android 5iPad 6Safari 7Chrome 8Firefox'");
            log.info("Schema: added column t_short_link.open_type");
        }
    }

    private void seedDefaultAdmin() {
        Long count = adminUserMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setAccount(props.admin().initAccount());
        admin.setPasswordHash(passwordEncoder.encode(props.admin().initPassword()));
        admin.setIsEnable(1);
        admin.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(admin);
        log.warn("Seeded default admin account '{}' (configurable via APP_ADMIN_ACCOUNT/APP_ADMIN_PASSWORD)",
                props.admin().initAccount());
    }
}
