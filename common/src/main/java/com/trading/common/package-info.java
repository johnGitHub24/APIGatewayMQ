/**
 * 【職責】跨服務共用模型：Kafka 訊息、受理回應、Topic 常數。
 * 【技巧】獨立 Gradle 模組，gateway／engine 皆依賴，避免重複定義 DTO。
 * 【概念】契約集中在 common，才能支撐削峰管線（Gateway 發、Engine 收）而不漂移；
 *         改欄位時兩邊編譯期即可發現不相容。
 * 【邊界】不含業務邏輯、Spring Bean 或 DB 實體；純資料與常數。
 *
 * <ul>
 *   <li>{@link com.trading.common.OrderCommandMessage}：Gateway → Kafka 的下單指令</li>
 *   <li>{@link com.trading.common.OrderAcceptedResponse}：Gateway 回客戶端的 202 受理模型</li>
 *   <li>{@link com.trading.common.Topics}：Kafka topic 名稱</li>
 * </ul>
 */
package com.trading.common;
