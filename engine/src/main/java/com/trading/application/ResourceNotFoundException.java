package com.trading.application;

/**
 * 【職責】資源不存在例外：查訂單／成交／持倉找不到對應資料。
 * 【技巧】攜帶 {@code errorCode}；通常由全域處理器對應 HTTP 404。
 * 【概念】業務層用明確例外表達「沒有這個資源」，比回 Optional 一路傳到 Controller 更一致。
 * 【邊界】不組裝錯誤 JSON body。
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String errorCode;

    /** 建立例外，附帶錯誤碼與說明訊息。 */
    public ResourceNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 取得業務錯誤碼（例如 ORDER_NOT_FOUND）。 */
    public String getErrorCode() {
        return errorCode;
    }
}
