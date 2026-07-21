-- integration-cleanup-before.sql：整合測試前清理（參考用，JUnit 以 @BeforeEach 程式清理）
DELETE FROM order_events;
DELETE FROM trades;
DELETE FROM orders;
DELETE FROM positions;
