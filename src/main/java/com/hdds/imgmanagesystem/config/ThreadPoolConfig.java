package com.hdds.imgmanagesystem.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ExecutorService uploadExecutorService() {
        return new ThreadPoolExecutor(
                5,                       // 核心线程数
                10,                      // 最大线程数
                60L,                     // 空闲线程存活时间
                TimeUnit.SECONDS,        // 时间单位
                new LinkedBlockingQueue<>(100),  // 工作队列
                new ThreadFactoryBuilder()
                        .setNameFormat("upload-pool-%d")
                        .build(),            // 线程工厂
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
    }

}
