package com.trading.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 【職責】Trading Engine 模組的 Spring Boot 啟動入口。
 * 【技巧】{@code @SpringBootApplication(scanBasePackages = "com.trading")} 掃描 application／engine／infrastructure。
 * 【概念】一個 main 啟動內嵌 Tomcat、Kafka Listener、排程與 REST；Gateway 是另一個進程。
 * 【邊界】不含業務規則；只負責組裝 Spring 容器並啟動。
 */
@SpringBootApplication(scanBasePackages = "com.trading")
public class TradingEngineApplication {

    /**
     * 【職責】啟動 Spring 應用與內嵌伺服器。
     * 【技巧】{@link SpringApplication#run(Class, String...)}。
     * 【概念】args 可帶 {@code --spring.profiles.active=...} 切換設定檔。
     * @param args 命令列參數
     */
    public static void main(String[] args) {
        SpringApplication.run(TradingEngineApplication.class, args);
    }
}
