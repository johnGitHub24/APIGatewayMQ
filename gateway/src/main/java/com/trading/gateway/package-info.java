/**
 * 【職責】Gateway 模組根套件：對外 HTTP 入口、限流、Kafka 下單與 Engine 代理。
 * 【技巧】Spring Boot WebFlux 應用；子套件 web／filter／service／config 分層。
 * 【概念】Gateway 負責「接流量、保護、分流」：寫入削峰走 Kafka，讀取／取消走 WebClient 代理 Engine，
 *         與 Engine 進程解耦以便水平擴展。
 * 【邊界】不含 Engine 風控／狀態機／DB；那些在 engine 模組。
 *
 * <p>閱讀順序建議：{@code GatewayApplication} → {@code web} → {@code filter} → {@code service} → {@code config}。</p>
 */
package com.trading.gateway;
