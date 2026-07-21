-- API Gateway MQ / Engine 共用資料表（對齊 Trading System MVP 規格書 §3）
-- 適用：PostgreSQL（Docker）/ H2（測試由 JPA ddl-auto 建立）

CREATE TABLE IF NOT EXISTS orders (
    id              BIGSERIAL       PRIMARY KEY,
    client_order_id VARCHAR(64)     UNIQUE,
    symbol          VARCHAR(20)     NOT NULL,
    side            VARCHAR(4)      NOT NULL,
    quantity        DECIMAL(18,8)   NOT NULL,
    price           DECIMAL(18,8)   NOT NULL,
    filled_quantity DECIMAL(18,8)   DEFAULT 0,
    status          VARCHAR(20)     NOT NULL,
    reject_reason   VARCHAR(255),
    risk_rule_code  VARCHAR(16),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_symbol_created ON orders (symbol, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_client_order_id ON orders (client_order_id);

CREATE TABLE IF NOT EXISTS trades (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES orders(id),
    executed_price  DECIMAL(18,8)   NOT NULL,
    executed_qty    DECIMAL(18,8)   NOT NULL,
    executed_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trades_order_id ON trades (order_id);

CREATE TABLE IF NOT EXISTS positions (
    id              BIGSERIAL       PRIMARY KEY,
    symbol          VARCHAR(20)     NOT NULL UNIQUE,
    quantity        DECIMAL(18,8)   NOT NULL DEFAULT 0,
    avg_price       DECIMAL(18,8)   NOT NULL DEFAULT 0,
    unrealized_pnl  DECIMAL(18,8)   NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_events (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL,
    event           VARCHAR(32)     NOT NULL,
    risk_rule_code  VARCHAR(16),
    reject_reason   VARCHAR(255),
    payload_json    TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_events_order_created ON order_events (order_id, created_at);
CREATE INDEX IF NOT EXISTS idx_order_events_created ON order_events (created_at);

-- JOB-B：每日持倉/PnL 結算快照
CREATE TABLE IF NOT EXISTS pnl_snapshots (
    id              BIGSERIAL       PRIMARY KEY,
    snapshot_date   DATE            NOT NULL,
    symbol          VARCHAR(20)     NOT NULL,
    quantity        DECIMAL(18,8)   NOT NULL DEFAULT 0,
    avg_price       DECIMAL(18,8)   NOT NULL DEFAULT 0,
    mark_price      DECIMAL(18,8)   NOT NULL DEFAULT 0,
    unrealized_pnl  DECIMAL(18,8)   NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pnl_snapshots_date_symbol ON pnl_snapshots (snapshot_date, symbol);

-- JOB-C：失敗下單指令持久化 DLQ
CREATE TABLE IF NOT EXISTS failed_commands (
    id              BIGSERIAL       PRIMARY KEY,
    command_id      VARCHAR(64),
    client_order_id VARCHAR(64),
    symbol          VARCHAR(20)     NOT NULL,
    side            VARCHAR(4)      NOT NULL,
    quantity        DECIMAL(18,8)   NOT NULL,
    price           DECIMAL(18,8)   NOT NULL,
    failure_reason  VARCHAR(512),
    attempts        INT             NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    next_retry_at   TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_failed_commands_status_retry ON failed_commands (status, next_retry_at);
