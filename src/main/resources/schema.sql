CREATE TABLE IF NOT EXISTS t_short_link (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code    VARCHAR(16)   NOT NULL COMMENT '短码',
    dest_url      VARCHAR(2048) NOT NULL COMMENT '目标地址',
    password_hash VARCHAR(128)  NULL COMMENT '访问密码 BCrypt 哈希',
    description   VARCHAR(512)  NULL COMMENT '备注',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    open_type     TINYINT       NOT NULL DEFAULT 0 COMMENT '打开方式 0全部 1微信 2钉钉 3iPhone 4Android 5iPad 6Safari 7Chrome 8Firefox',
    click_count   BIGINT        NOT NULL DEFAULT 0 COMMENT '累计点击',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expired_at    DATETIME      NULL COMMENT '过期时间，NULL 永不过期',
    UNIQUE KEY uk_short_code (short_code),
    KEY idx_dest_url (dest_url(64))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_access_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(16)   NOT NULL COMMENT '短码',
    ip         VARCHAR(64)   NULL COMMENT '访问 IP',
    user_agent VARCHAR(512)  NULL COMMENT 'User-Agent',
    referer    VARCHAR(2048) NULL COMMENT 'Referer',
    visit_time DATETIME(3)   NOT NULL COMMENT '访问时间',
    KEY idx_code_time (short_code, visit_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_admin_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    account       VARCHAR(64)  NOT NULL COMMENT '管理员账号',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码 BCrypt 哈希',
    is_enable     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account (account)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_short_link_dest (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(16)   NOT NULL COMMENT '短码',
    label      VARCHAR(64)   NOT NULL COMMENT '目标标识（访问时通过 X-Dest-Label 请求头选择）',
    dest_url   VARCHAR(2048) NOT NULL COMMENT '目标地址',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code_label (short_code, label),
    KEY idx_code (short_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
