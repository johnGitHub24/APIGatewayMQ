/**
 * 【職責】Trading Engine 模組根套件：接收 Gateway／Kafka 命令，執行風控與交易流程並提供查詢 API。
 * 【技巧】依分層拆套件（api／application／infrastructure／engine.*／config／domain／dto）。
 * 【概念】Gateway 負責削峰與轉送；Engine 才是「真正下單與改狀態」的核心。讀程式時先分清哪一層在做決策。
 * 【邊界】不含 Gateway WebFlux／限流實作（見 gateway 模組）。
 */
package com.trading;
