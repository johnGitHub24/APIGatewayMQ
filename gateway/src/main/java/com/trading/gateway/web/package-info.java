/**
 * 【職責】Gateway Web 層：薄 Controller，處理路由、參數、狀態碼與回應格式。
 * 【技巧】Spring WebFlux {@code @RestController}；回傳 {@code Mono}／{@code ResponseEntity}。
 * 【概念】Controller 只做 HTTP 適配，商業／基礎設施協調委派 service（Kafka 發送、Engine 轉發），
 *         不直接操作 Repository 或散落業務規則。
 * 【邊界】不含限流（見 filter）與 Kafka／WebClient 組態（見 config）。
 */
package com.trading.gateway.web;
