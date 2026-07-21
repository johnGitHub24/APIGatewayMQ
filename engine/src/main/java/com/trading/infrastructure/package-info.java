/**
 * 【職責】Engine 基礎設施層：Entity／Repository／Mapper，提供「怎麼存、怎麼查、怎麼投影」。
 * 【技巧】依套件拆分 entity、repository、mapper，與 application／api 解耦。
 * 【概念】這層不應承擔風控或狀態機決策；業務規則留在 application，此處只做技術細節。
 * 【邊界】不含 REST Controller、Kafka 消費編排或排程 Job 本體。
 */
package com.trading.infrastructure;
