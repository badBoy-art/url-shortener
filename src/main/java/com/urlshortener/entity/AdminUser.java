package com.urlshortener.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_admin_user")
public class AdminUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String account;

    private String passwordHash;

    /** 1 启用 0 停用 */
    private Integer isEnable;

    private LocalDateTime createdAt;
}
