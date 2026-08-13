# Architecture — APIGatewayMQ

> 衝突以 [APIGatewayMQ 規格書.md](../APIGatewayMQ%20規格書.md) 為準。  
> 中文深度說明：[APIGatewayMQ 架構（Spring Boot）.md](../APIGatewayMQ%20架構（Spring%20Boot）.md)、[架構學習導引.md](架構學習導引.md)。  
> 規範：EngineeringOS `knowledge/documentation.md`

## Layers

| Layer | Module / Package | Responsibility |
|-------|------------------|----------------|
| Gateway API | `gateway/.../web` | WebFlux HTTP：非同步下單、讀 API 代理 |
| Gateway Filter | `gateway/.../filter` | Redis 固定窗口限流（`RateLimitWebFilter`） |
| Gateway Service | `gateway/.../service` | Kafka Producer、Engine Round-Robin 代理 |
| Common | `common` | Kafka 訊息契約 `OrderCommandMessage` |
| Engine API | `engine/.../api`、`engine/.../web` | 同步下單／查詢／持倉／PnL／Job（除錯與代理目標） |
| Engine Messaging | `engine/.../messaging` | Kafka Consumer → 交易入口 |
| Application | `engine/.../application` | `TradingService`、`RiskEngine`、成交／狀態機 |
| Domain | `engine/.../domain` | `OrderStatus`、`ErrorCodes` |
| Infrastructure | `engine/.../infrastructure` | JPA Entity／Repository → PostgreSQL／H2 |

## Module map

| Module | Port | Notes |
|--------|------|-------|
| `common` | — | Kafka DTO 契約 |
| `gateway` | 8080 | WebFlux 入口、限流、Producer、讀代理 |
| `engine` | 8081／8082 | Consumer Group `trading-engine`；多副本分攤 partition |

## Runtime

```text
【非同步下單】
Client → Gateway POST /api/v1/orders
       → 202 + pollUrl
       → Kafka topic order.commands (key=symbol)
       → Engine OrderCommandConsumer
       → TradingService.placeOrder()
       → PostgreSQL

【查詢／補成交／取消】
Client → Gateway GET|PATCH|POST /api/v1/**
       → EngineProxyService (Round-Robin)
       → Engine REST → PostgreSQL
```

一致性：冪等 `Idempotency-Key` → `client_order_id` UNIQUE；同標的有序（partition key=`symbol`）；限流超限 `429`；Redis 故障 fail-open。

監控：Actuator → Prometheus (:9090) → Grafana (:3000)。

## Visual maps

| 文件 | 用途 |
|------|------|
| [codeGraphic.html](codeGraphic.html) | Tab：非同步／限流／Engine／模組（圖為主） |
| [專案引導教學.html](專案引導教學.html) | 長文引導＋流程圖 |
