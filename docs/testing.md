# Testing and Verification — APIGatewayMQ

> 衝突以 [APIGatewayMQ 規格書.md](../APIGatewayMQ%20規格書.md) 為準。  
> Case ID／腳本詳見 [測試與CI.md](測試與CI.md)。  
> 規範：EngineeringOS `knowledge/testing.md`

## Check command

```powershell
.\scripts\check.ps1
```

等同 `.\gradlew.bat check`（需 JDK 21；可先 `. .\scripts\env.ps1`）。與 CI 同一入口。

## Test layers

| Layer | Location | Tag / Task | 說明 |
|-------|----------|------------|------|
| 單元 | `*/src/test` | `@Tag("unit")` 或無 | common 序列化、Gateway Producer／限流、Engine Risk／Controller |
| 整合 | `*/src/integrationTest` | `@Tag("integration")` | H2 + EmbeddedKafka；Gateway→Kafka、Consumer→成交、ORDER／SCN／LOG |
| OpenAPI | export 任務 | `@Tag("openapi")` | `:engine:exportOpenApi`／`:gateway:exportOpenApi` |
| Smoke（可選） | `scripts/smoke-test.ps1` | — | 需 Docker／本機服務運行 |

## Minimum case types

| Type | Coverage |
|------|----------|
| Happy Path | GW-002／GW-003、ENGINE-MQ-001、ORDER-001 等 |
| Error Path | GW-005（429）、GW-006（驗證）、風控 R001~R010、冪等 ENGINE-MQ-002 |
| Schema | DB-001（四張核心表） |

## Key classes

| Test | 對應 |
|------|------|
| `OrderCommandMessageSerializationTest` | COMMON-001／002 |
| `OrderSubmitControllerTest`／`RateLimitWebFilterTest` | GW-002／004／005 |
| `GatewayKafkaIntegrationTest` | GW-003 |
| `OrderCommandConsumerIntegrationTest` | ENGINE-MQ-001／002 |
| `DatabaseSchemaIntegrationTest` | DB-001 |
| `PlaceOrderIntegrationTest`／`ScenarioIntegrationTest` | ORDER／SCN |

## DoD

- [ ] Unit tests green（`:common:test`、`:gateway:test`、`:engine:test`）
- [ ] Integration tests green（`:gateway:integrationTest`、`:engine:integrationTest`）
- [ ] Check command matches CI（`gradlew check`）
- [ ] 公開 Gateway／Engine 核心路徑有 Happy + 錯誤路徑

詳見 [測試與CI.md](測試與CI.md)。
