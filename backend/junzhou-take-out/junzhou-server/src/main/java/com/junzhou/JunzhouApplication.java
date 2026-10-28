package com.junzhou;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement //开启注解方式的事务管理
@Slf4j
@EnableCaching //开启注解方式的缓存管理
@EnableScheduling //定时任务
public class JunzhouApplication {
    public static void main(String[] args) {
        SpringApplication.run(JunzhouApplication.class, args);
        log.info("server started");
    }
}
