package com.trading.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 【職責】Gateway 模組的 Spring Boot 啟動入口，載入 WebFlux／Kafka／Redis 等自動組態與元件掃描。
 * 【技巧】{@code @SpringBootApplication} 合併 {@code @Configuration}、{@code @EnableAutoConfiguration}、
 *         {@code @ComponentScan}；{@link SpringApplication#run} 啟動內嵌 Netty（WebFlux）並建立 ApplicationContext。
 * 【概念】Gateway 是對外 HTTP 入口：非同步下單走 Kafka 削峰，查詢／取消等走 WebClient 代理 Engine。
 *         與 Engine 分開部署，讓入口層可水平擴展而不必與撮合／風控同進程。
 * 【邊界】不負責業務規則、限流演算法細節或 Kafka 消費；那些分別在 filter／service／engine。
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * 【職責】啟動內嵌伺服器並載入 Gateway Spring context。
     * 【技巧】{@code SpringApplication.run(GatewayApplication.class, args)} 依 classpath 自動選 WebFlux。
     * 【概念】main 只做「開機」；實際路由、限流、發 Kafka 由掃描到的 Bean 在請求時執行。
     *
     * @param args 命令列參數（可覆寫 Spring profile／設定）
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
