# 參考資料 — houseHub 測試方法論

> **本文件僅供參考，不是 APIGatewayMQ 的規格。**  
> 正式開發請以 `APIGatewayMQ 規格書.md` 為準。

---

## 用途說明

| 項目 | 說明 |
|------|------|
| **來源** | houseHub 專案測試規格書（Grails + Spock） |
| **採用** | 測試方法論：fixture、三層測試、Case ID 命名、整合測試 SOP、DoD 清單 |
| **不採用** | API 路由、`000000` 錯誤碼、hs100 資料表、Grails Controller 實作 |

---

## 可遷移的方法論

### 1. fixture 載入流程

```text
GW-ORDER-001-SUCCESS.json
        ↓
GatewayTestFixtures.loadGatewayOrderJson("GW-ORDER-001-SUCCESS")
        ↓
POST /api/v1/orders (Gateway)
        ↓
202 Accepted → Kafka → Engine
```

### 2. 三層測試分工

| 層 | houseHub | APIGatewayMQ |
|----|----------|--------------|
| 驗證層 | ValidatorSpec | DTO `@Valid` 單元測試 |
| HTTP 層 | ControllerSpec + stub | WebTestClient / MockMvc + MockBean |
| 真流程 | IntegrationSpec + DB | SpringBootTest + H2 + EmbeddedKafka |

### 3. Case ID 命名慣例

```text
{模組}-{序號}-{語意}

GW-001-PUBLISH
ENGINE-MQ-001-CONSUME
ORDER-001-SUCCESS
```

### 4. 每支 API 最低案例類型

| 後綴 | 用途 |
|------|------|
| 001-SUCCESS | 正向 |
| 003-MISSING_REQUIRED | 缺欄位 |
| 005-* | 業務拒絕 / 限流 |
| 006-DUPLICATE | 冪等重複 |
| 007-ROLLBACK | 失敗不殘留 |

### 5. 整合測試前置

```text
integration-setup.sql → integration-cleanup-before.sql → gradlew check
```

---

## 對照：houseHub vs APIGatewayMQ

| 項目 | houseHub | APIGatewayMQ |
|------|----------|--------------|
| 框架 | Grails 5 | Spring Boot 3 多模組 |
| 測試 | Spock | JUnit 5 |
| 下單路由 | `/v1/hs/createCaseInfo` | Gateway `POST /api/v1/orders` → 202 |
| 訊息佇列 | 無 | Kafka `order.commands` |
| 規格權威 | houseHub 測試規格書 | **APIGatewayMQ 規格書.md** |

---

*此文件保留 houseHub 方法論摘要，供測試設計參考。*
