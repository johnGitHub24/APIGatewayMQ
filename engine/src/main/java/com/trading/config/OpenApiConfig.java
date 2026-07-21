package com.trading.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 【職責】註冊 Engine 的 OpenAPI（Swagger）文件描述，供 Swagger UI 瀏覽與試呼 REST 端點。
 * 【技巧】SpringDoc：宣告 {@link OpenAPI} {@code @Bean}，自動掃描 {@code @RestController} 產生規格。
 * 【概念】契約文件與程式同進程，減少「規格書與實作脫節」；可經 Gateway 代理或直連 Engine port。
 * 【邊界】只設定文件 metadata；不負責安全／授權攔截。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 【職責】建立 OpenAPI 文件的標題、說明與版本。
     * 【技巧】{@code new OpenAPI().info(...)} 交給 SpringDoc 合併路徑與 schema。
     * 【概念】Info 是文件首頁摘要；實際端點列表由註解掃描產生，不必手寫 YAML。
     */
    @Bean
    public OpenAPI tradingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("APIGatewayMQ Engine API")
                        .description("交易引擎：下單、風控、持倉、PnL、成交查詢（經 Gateway 代理或直連）")
                        .version("v1"));
    }
}
