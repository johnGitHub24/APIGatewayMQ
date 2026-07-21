package com.trading.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 【職責】綁定風控規則閾值（持倉／曝險／重複單／波動／過度交易／部分成交等）。
 * 【技巧】{@code @ConfigurationProperties(prefix = "trading.risk")}；{@link BigDecimal} 避免浮點誤差。
 * 【概念】規則邏輯在 {@link com.trading.application.RiskService}，數值外置後可依環境調參而不改碼。
 * 【邊界】只提供設定；不執行風控判斷。正式環境請用設定檔／環境變數覆寫預設值。
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "trading.risk")
public class RiskProperties {

    /** 單一標的允許的最大持倉量（絕對值）。 */
    private BigDecimal maxPositionPerSymbol = new BigDecimal("100");
    /** 全帳戶所有持倉的總曝險上限（數量 × 價格 的加總）。 */
    private BigDecimal maxTotalExposure = new BigDecimal("1000000");
    /** 重複下單偵測的時間窗口（秒）；同內容在此窗口內視為重複。 */
    private int duplicateWindowSeconds = 60;

    /** 市場行情缺值時使用的預設波動率指數（0~1）。 */
    private BigDecimal defaultVolatilityIndex = new BigDecimal("0.30");
    /** 極端波動門檻；超過此值可觸發停單或降級策略。 */
    private BigDecimal extremeVolatilityIndex = new BigDecimal("0.95");
    /** 可接受的最大波動率；超過則可能縮減下單數量。 */
    private BigDecimal maxVolatilityIndex = new BigDecimal("0.80");

    /** 過度交易偵測的統計時間窗口（秒）。 */
    private int overtradingWindowSeconds = 60;
    /** 在 overtradingWindowSeconds 內允許的最大下單筆數。 */
    private int maxOrdersPerWindow = 200;

    /** 委託數量超過此門檻時，觸發部分成交邏輯。 */
    private BigDecimal partialFillThreshold = new BigDecimal("10");
    /** 部分成交時，實際成交數量 = 委託數量 × 此比例。 */
    private BigDecimal partialFillRatio = new BigDecimal("0.50");

    /** 趨勢跟隨策略的最小強度門檻（0~1）。 */
    private BigDecimal minTrendStrength = new BigDecimal("0.40");
    /** 訊號噪音上限（0~1）；超過則視為不可靠訊號。 */
    private BigDecimal maxSignalNoise = new BigDecimal("0.70");
    /** 波動率超過此門檻時，觸發下單數量縮減。 */
    private BigDecimal reduceVolatilityThreshold = new BigDecimal("0.65");
    /** 波動偏高時，下單數量乘以此縮減比例。 */
    private BigDecimal volatilityReduceRatio = new BigDecimal("0.50");
    /** 紀律提醒門檻：時間窗口內下單筆數超過此值時發出警告。 */
    private int disciplineOrderThreshold = 30;
}
