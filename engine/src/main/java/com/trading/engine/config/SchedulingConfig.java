package com.trading.engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 【職責】開啟 Spring 排程支援，讓 {@code com.trading.engine.job} 下的 {@code @Scheduled} 生效。
 * 【技巧】{@code @Configuration} + {@code @EnableScheduling}。
 * 【概念】沒有這行開關，Job 類上的 cron 不會被註冊——常見「Job 寫了卻不跑」原因。
 * 【邊界】不定義個別 cron（在各 Job 的 property）；不實作業務。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
