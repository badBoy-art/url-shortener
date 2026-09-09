package com.urlshortener.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.urlshortener.common.BusinessException;
import com.urlshortener.config.AppProperties;
import com.urlshortener.entity.AccessLog;
import com.urlshortener.mapper.AccessLogMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminLogService {

    private static final DateTimeFormatter EXCEL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AccessLogMapper mapper;
    private final AppProperties props;

    public Page<AccessLog> queryLogs(String code, LocalDateTime start, LocalDateTime end, int page, int size) {
        return mapper.selectPage(new Page<>(page, size), buildWrapper(code, start, end));
    }

    public long countLogs(String code, LocalDateTime start, LocalDateTime end) {
        return mapper.selectCount(buildWrapper(code, start, end));
    }

    public long countDistinctIps(String code, LocalDateTime start, LocalDateTime end) {
        return mapper.countDistinctIp(code, start, end);
    }

    public byte[] exportToExcel(String code, LocalDateTime start, LocalDateTime end) {
        List<AccessLog> logs = mapper.selectList(buildWrapper(code, start, end)
                .last("LIMIT " + props.admin().exportMaxRows()));
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("访问日志");
            String[] titles = {"短码", "IP", "User-Agent", "Referer", "访问时间"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }
            int rowIndex = 1;
            for (AccessLog log : logs) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(log.getShortCode());
                row.createCell(1).setCellValue(log.getIp() == null ? "" : log.getIp());
                row.createCell(2).setCellValue(log.getUserAgent() == null ? "" : log.getUserAgent());
                row.createCell(3).setCellValue(log.getReferer() == null ? "" : log.getReferer());
                row.createCell(4).setCellValue(log.getVisitTime() == null ? "" : log.getVisitTime().format(EXCEL_TIME));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw BusinessException.internal("Excel 生成失败: " + e.getMessage());
        }
    }

    private LambdaQueryWrapper<AccessLog> buildWrapper(String code, LocalDateTime start, LocalDateTime end) {
        return Wrappers.<AccessLog>lambdaQuery()
                .eq(code != null && !code.isBlank(), AccessLog::getShortCode, code == null ? null : code.trim())
                .ge(start != null, AccessLog::getVisitTime, start)
                .le(end != null, AccessLog::getVisitTime, end)
                .orderByDesc(AccessLog::getVisitTime);
    }
}
