package com.trading.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 【職責】提供共用的 {@link WebClient.Builder}，供 Engine 反向代理建立 client。
 * 【技巧】{@code @Bean} 讓 Spring 管理 Builder 生命週期；各 Engine baseUrl 在
 *         {@link com.trading.gateway.service.EngineProxyService} 再 {@code .baseUrl(...).build()}。
 * 【概念】WebClient 是 WebFlux 的非阻塞 HTTP 客戶端（對照 RestTemplate／阻塞式）。
 *         同步查詢路徑：Controller → EngineProxyService → WebClient → Engine。
 * 【邊界】只提供 Builder；不設定逾時／負載均衡策略（可日後擴充）。正式環境 Engine URI 請用環境變數覆寫
 *         {@code gateway.engine-uris}。
 */
@Configuration
public class WebClientConfig {

    /**
     * 【職責】註冊預設 {@link WebClient.Builder}，可依不同 baseUrl 建立多個 client。
     * 【技巧】共用 Builder 可之後統一加 filter／codec；目前為預設設定。
     * 【概念】Builder 模式：先拿到「半成品工廠」，再依目標 Engine 補 baseUrl，避免硬編碼單一 client。
     *
     * @return 預設設定的 {@link WebClient.Builder}
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
