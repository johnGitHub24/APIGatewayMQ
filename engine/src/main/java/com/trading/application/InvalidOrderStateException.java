package com.trading.application;

import lombok.Getter;

/**
 * 【職責】訂單狀態不允許目前操作時拋出的業務例外（例如非部分成交卻 completeFill）。
 * 【技巧】繼承 {@link RuntimeException}；攜帶 {@code errorCode} 供全域例外處理對應 HTTP。
 * 【概念】用例外表達「狀態機不允許」比回傳 null／布林更明確，呼叫端必須處理或往上轉。
 * 【邊界】不負責組 HTTP body；由例外處理器轉 4xx。
 */
@Getter
public class InvalidOrderStateException extends RuntimeException {

    private final String errorCode;

    /** 建立例外，附帶錯誤碼與說明訊息。 */
    public InvalidOrderStateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
