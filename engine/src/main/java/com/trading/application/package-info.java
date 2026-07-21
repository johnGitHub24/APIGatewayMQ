/**
 * 【職責】Engine 應用層（Use Case／業務編排）：下單流程、風控串接、結果與查詢服務。
 * 【技巧】套件級 {@code package-info}；子套件 {@code risk} 放規則策略。
 * 【概念】Controller 不放商業邏輯；Repository 不放流程判斷——「為什麼、何時、先後」集中在這層。
 * 【邊界】不含 HTTP／Kafka 基礎設施細節（在 {@code engine} 套件）；不含 JPA Entity 定義（在 infrastructure）。
 */
package com.trading.application;
