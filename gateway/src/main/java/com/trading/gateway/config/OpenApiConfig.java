package com.trading.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 【職責】定義 Gateway 對外 HTTP API 的 OpenAPI（Swagger）文件元資料。
 * 【技巧】springdoc 掃描 {@code @RestController} 後，與此 {@link OpenAPI} Bean 合併成 Swagger UI／OpenAPI JSON。
 * 【概念】文件與程式同進程：改 Controller 註解即可反映到 UI，減少「規格與實作漂移」。
 *         完整合併規格另見倉庫 {@code docs/openapi.yaml}。
 * 【邊界】只描述文件標題／說明／預設 server；不實作業務 API。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 【職責】註冊 {@link OpenAPI} Bean，供 springdoc 產生 Swagger UI 與 OpenAPI JSON。
     * 【技巧】{@link Info} 設 title／description／version；{@link Server} 標示本機 Gateway {@code :8080}。
     * 【概念】OpenAPI 是「機器可讀的 API 契約」；Swagger UI 只是它的瀏覽介面。
     *
     * @return 含標題、說明與預設伺服器位址的文件物件
     */
    @Bean
    public OpenAPI gatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("APIGatewayMQ Gateway API")
                        .description("""
                                API Gateway 入口：非同步下單（202 + Kafka）、限流、查詢代理至 Engine。
                                完整合併規格見 `docs/openapi.yaml`。
                                """)
                        .version("v1"))
                .servers(List.of(new Server().url("http://localhost:8080").description("API Gateway")));
    }
}
