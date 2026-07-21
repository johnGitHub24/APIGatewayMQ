package com.trading.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 【職責】結構化審計日誌：以固定鍵值格式輸出到 {@code TRADING_AUDIT} logger。
 * 【技巧】獨立 Logger 名稱；{@code event= orderId= symbol= detail=} 方便 ELK 擷取。
 * 【概念】應用日誌與審計日誌分離：前者給開發除錯，後者給合規／營運搜尋關鍵業務事件。
 * 【邊界】不寫 DB 事件表（那是 {@link OrderEventService}）；只打 log。
 */
@Component
public class StructuredAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("TRADING_AUDIT");

    /**
     * 【職責】輸出一筆結構化訂單審計紀錄。
     * 【技巧】單一 {@code info} 模板，欄位順序固定。
     * 【概念】欄位穩定比「自由文字」重要——機器才能可靠 parse。
     * @param event   事件名（如 APPROVED／REJECTED）
     * @param orderId 訂單主鍵
     * @param symbol  標的
     * @param detail  補充說明（可 null）
     */
    public void logOrderEvent(String event, Long orderId, String symbol, String detail) {
        log.info("event={} orderId={} symbol={} detail={}", event, orderId, symbol, detail);
    }
}
