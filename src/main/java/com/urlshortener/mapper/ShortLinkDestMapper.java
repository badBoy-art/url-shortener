package com.urlshortener.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urlshortener.entity.ShortLinkDest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShortLinkDestMapper extends BaseMapper<ShortLinkDest> {
}
