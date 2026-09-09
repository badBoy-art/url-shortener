package com.urlshortener.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urlshortener.entity.ShortLink;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ShortLinkMapper extends BaseMapper<ShortLink> {

    @Update("UPDATE t_short_link SET click_count = click_count + #{delta} WHERE short_code = #{code}")
    int incrementClick(@Param("code") String code, @Param("delta") long delta);

    @Select("SELECT COUNT(*) FROM t_short_link")
    long countAll();

    @Select("SELECT COALESCE(SUM(click_count), 0) FROM t_short_link")
    long sumClicks();

    @Select("SELECT short_code, dest_url, click_count FROM t_short_link ORDER BY click_count DESC LIMIT #{limit}")
    List<ShortLink> topByClicks(@Param("limit") int limit);
}
