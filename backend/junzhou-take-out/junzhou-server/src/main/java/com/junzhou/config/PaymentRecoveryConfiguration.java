package com.junzhou.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/** Isolate provider I/O from the scheduler that expires and completes orders. */
@Configuration
@EnableAsync(proxyTargetClass = true)
public class PaymentRecoveryConfiguration {
    @Bean("paymentRecoveryExecutor")
    public ThreadPoolTaskExecutor paymentRecoveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("payment-recovery-");
        // A busy worker retains its cursor; skip overlapping ticks instead of queuing them.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
