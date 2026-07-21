package com.trading.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 【職責】綁定 {@code gateway.*} 設定：Engine 位址、限流閾值、本實例識別。
 * 【技巧】{@code @ConfigurationProperties(prefix = "gateway")} 將 YAML／環境變數映射到欄位；
 *         Lombok {@code @Data} 產生 getter／setter 供 Spring 綁定與業務讀取。
 * 【概念】集中設定比散落 {@code @Value} 好維護：改 prefix 下的鍵即可，正式環境用環境變數覆寫敏感或部署相關值
 *         （例如 {@code GATEWAY_ENGINE_URIS}、{@code GATEWAY_RATE_LIMIT_PER_SECOND}）。
 * 【邊界】只持有設定值，不執行限流或轉發；消費方為 Filter／Service／Controller。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** Engine 節點基底 URL；{@link com.trading.gateway.service.EngineProxyService} 輪詢轉發用。 */
    private List<String> engineUris = new ArrayList<>(List.of("http://localhost:8081"));

    /** 每客戶端鍵在 1 秒固定視窗內允許的最大請求數（搭配 Redis 限流）。 */
    private int rateLimitPerSecond = 50;

    /** 本 Gateway 實例 ID，寫入 {@link com.trading.common.OrderCommandMessage#getSourceGateway()}。 */
    private String instanceId = "gateway-1";
}
