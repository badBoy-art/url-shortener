package com.urlshortener.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urlshortener.dto.HourlyPv;
import com.urlshortener.entity.AccessLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessLogMapper extends BaseMapper<AccessLog> {

    @Insert("<script>" +
            "INSERT INTO t_access_log (short_code, ip, user_agent, referer, visit_time) VALUES " +
            "<foreach collection='list' item='l' separator=','>" +
            "(#{l.shortCode}, #{l.ip}, #{l.userAgent}, #{l.referer}, #{l.visitTime})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<AccessLog> logs);

    @Select("SELECT COUNT(*) FROM t_access_log WHERE short_code = #{code} AND visit_time >= #{start}")
    long countSince(@Param("code") String code, @Param("start") LocalDateTime start);

    @Select("SELECT COUNT(DISTINCT ip) FROM t_access_log WHERE short_code = #{code} AND visit_time >= #{start}")
    long countDistinctIpSince(@Param("code") String code, @Param("start") LocalDateTime start);

    @Select("SELECT COUNT(*) FROM t_access_log WHERE short_code = #{code} AND visit_time >= #{start} AND visit_time < #{end}")
    long countBetween(@Param("code") String code, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(DISTINCT ip) FROM t_access_log WHERE short_code = #{code} AND visit_time >= #{start} AND visit_time < #{end}")
    long countDistinctIpBetween(@Param("code") String code, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT HOUR(visit_time) AS hour, COUNT(*) AS pv FROM t_access_log " +
            "WHERE short_code = #{code} AND visit_time >= #{start} " +
            "GROUP BY HOUR(visit_time) ORDER BY hour")
    List<HourlyPv> hourlyPv(@Param("code") String code, @Param("start") LocalDateTime start);

    @Select("SELECT COUNT(*) FROM t_access_log WHERE visit_time >= #{start}")
    long countAllSince(@Param("start") LocalDateTime start);

    @Select("SELECT COUNT(DISTINCT ip) FROM t_access_log WHERE visit_time >= #{start}")
    long countDistinctIpAllSince(@Param("start") LocalDateTime start);

    @Select("SELECT COUNT(*) FROM t_access_log WHERE visit_time >= #{start} AND visit_time < #{end}")
    long countAllBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(DISTINCT ip) FROM t_access_log WHERE visit_time >= #{start} AND visit_time < #{end}")
    long countDistinctIpAllBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("<script>" +
            "SELECT COUNT(DISTINCT ip) FROM t_access_log WHERE 1=1 " +
            "<if test='code != null and code != \"\"'>AND short_code = #{code}</if> " +
            "<if test='start != null'>AND visit_time >= #{start}</if> " +
            "<if test='end != null'>AND visit_time &lt;= #{end}</if>" +
            "</script>")
    long countDistinctIp(@Param("code") String code, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
