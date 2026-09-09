package com.urlshortener.service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.entity.AccessLog;
import com.urlshortener.mapper.AccessLogMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 访问日志异步批量写入：有界队列承接请求线程，定时批量落库。
 * 队列满时丢弃日志（主链路优先），但点击计数不受影响。
 */
@Slf4j
@Service
public class AccessLogService {

    private final AccessLogMapper mapper;
    private final AppProperties props;
    private final BlockingQueue<AccessLog> queue;

    public AccessLogService(AccessLogMapper mapper, AppProperties props) {
        this.mapper = mapper;
        this.props = props;
        this.queue = new ArrayBlockingQueue<>(props.accessLog().queueCapacity());
    }

    public void offer(AccessLog accessLog) {
        if (!queue.offer(accessLog)) {
            log.warn("访问日志队列已满，丢弃日志（主链路不受影响）");
        }
    }

    @Scheduled(fixedDelayString = "${app.access-log.flush-interval-ms:1000}")
    public void flush() {
        List<AccessLog> batch = new ArrayList<>(props.accessLog().batchSize());
        queue.drainTo(batch, props.accessLog().batchSize());
        if (batch.isEmpty()) {
            return;
        }
        try {
            mapper.batchInsert(batch);
        } catch (Exception e) {
            log.error("批量写入访问日志失败，丢弃 {} 条", batch.size(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        List<AccessLog> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try {
                mapper.batchInsert(remaining);
            } catch (Exception e) {
                log.error("关闭时冲刷访问日志失败，丢弃 {} 条", remaining.size(), e);
            }
        }
    }
}
