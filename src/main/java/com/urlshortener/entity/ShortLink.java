package com.urlshortener.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName("t_short_link")
public class ShortLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shortCode;

    private String destUrl;

    /** 多目标地址（label → destUrl），仅查询/缓存装载时填充，非表字段 */
    @TableField(exist = false)
    private Map<String, String> destinations;

    private String passwordHash;

    private String description;

    /** 1 启用 0 停用 */
    private Integer status;

    /** 打开方式：0 全部 1 微信 2 钉钉 3 iPhone 4 Android 5 iPad 6 Safari 7 Chrome 8 Firefox */
    private Integer openType;

    private Long clickCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** NULL 表示永不过期 */
    private LocalDateTime expiredAt;

    public boolean isExpired(LocalDateTime now) {
        return expiredAt != null && expiredAt.isBefore(now);
    }

    public boolean isEnabled() {
        return status == null || status == 1;
    }
}
