package com.theclimbingnight.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 테스트에서는 {@code app.scheduling.enabled=false}로 꺼서 작업이 공유 H2를 건드리지 않게 한다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
