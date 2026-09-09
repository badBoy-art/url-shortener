package com.urlshortener.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链的多目标地址：同一短码可绑定多个 destUrl，每个目标有一个 label 标识，
 * 访问时通过 X-Dest-Label 请求头选择对应目标。
 */
@Data
@TableName("t_short_link_dest")
public class ShortLinkDest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shortCode;

    private String label;

    private String destUrl;

    private LocalDateTime createdAt;
}
