package com.trading.common;

/**
 * 【職責】集中管理 Kafka Topic 名稱常數，供 Gateway 發送與 Engine 消費共用。
 * 【技巧】{@code public static final String}；私有建構子防止實例化（工具類慣例）。
 * 【概念】削峰管線的「通道名稱」：字串散落各處易打錯或改名遺漏；集中一處後，
 *         Gateway Producer 與 Engine Consumer 保證訂閱同一 topic。
 * 【邊界】只定義名稱，不建立 topic、不負責 ACL／partition 數（那是基礎設施／運維）。
 */
public final class Topics {

    /** 下單命令主 Topic（Gateway 發、Engine 收）。 */
    public static final String ORDER_COMMANDS = "order.commands";

    /** 工具類不應被實例化。 */
    private Topics() {
    }
}
