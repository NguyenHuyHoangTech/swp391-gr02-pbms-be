package com.pbms.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfig {
    // This configuration enables the @Retryable annotation across the project,
    // which is critical for handling OptimisticLockingFailureException during race conditions.
}

