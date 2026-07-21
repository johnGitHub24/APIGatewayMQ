package com.trading.domain;

/**
 * 【職責】表達買賣方向（買／賣）。
 * 【技巧】領域 {@code enum}，API 與 Entity 共用同一組合法值。
 * 【概念】方向是交易核心語意：持倉增減、曝險計算都依 BUY／SELL 分支。
 *         用 enum 比 {@code "BUY"} 字串更不易在比較時漏掉大小寫或拼寫錯誤。
 */
public enum OrderSide {
    /** 買入：成交後持倉數量增加。 */
    BUY,
    /** 賣出：成交後持倉數量減少（或平倉）。 */
    SELL
}
