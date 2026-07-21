package com.trading.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 【職責】Engine Web MVC 跨切面設定：CORS 規則與靜態資源映射。
 * 【技巧】實作 {@link WebMvcConfigurer}；{@code @Value} 讀取 {@code trading.cors.allowed-origins}。
 * 【概念】瀏覽器同源政策會擋跨域 AJAX；CORS 在伺服器宣告允許來源／方法。靜態資源則服務 Demo 前端頁。
 * 【邊界】不負責業務授權；正式環境應收斂 allowed-origins，避免 {@code *}。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${trading.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 【職責】為 {@code /api/**} 註冊 CORS，允許設定來源與常用 HTTP 方法。
     * 【技巧】{@link CorsRegistry#addMapping} + {@code allowedOriginPatterns}（支援逗號分隔多來源）。
     * 【概念】OPTIONS 預檢由框架處理；這裡宣告「誰可以從瀏覽器打哪些動詞」。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    /**
     * 【職責】將非 API 路徑對應到 {@code classpath:/static/} 靜態資源。
     * 【技巧】{@link ResourceHandlerRegistry#addResourceHandler}。
     * 【概念】同一進程可同時提供 REST 與簡單前端頁，方便本機 Demo；正式環境常改由 CDN／Gateway 提供靜態檔。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
