-- integration-setup.sql：建立邊界測試前置持倉（ORDER-002）
-- 於整合測試中透過 API 建立，此檔供手動 H2 Console 驗證參考
INSERT INTO positions (symbol, quantity, avg_price, unrealized_pnl, updated_at)
VALUES ('BTCUSDT', 0, 0, 0, CURRENT_TIMESTAMP);
