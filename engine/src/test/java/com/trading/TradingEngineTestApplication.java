package com.trading;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 測試用 @SpringBootConfiguration 錨點（主程式在 com.trading.engine，WebMvcTest 需此類別）。
 */
@SpringBootApplication(scanBasePackages = "com.trading")
public class TradingEngineTestApplication {
}
