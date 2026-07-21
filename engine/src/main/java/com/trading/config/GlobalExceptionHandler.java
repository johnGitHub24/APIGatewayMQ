package com.trading.config;

import com.trading.application.InvalidOrderStateException;
import com.trading.application.ResourceNotFoundException;
import com.trading.application.RiskRejectedException;
import com.trading.domain.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【職責】Engine 全域例外處理：把應用／框架例外轉成統一的 Problem JSON，供前端與整合方依 {@code errorCode} 分流。
 * 【技巧】{@code @RestControllerAdvice} + {@code @ExceptionHandler}；手動組 {@code application/problem+json}（RFC 7807 風格）。
 * 【概念】Controller 不應各自 try/catch 組錯誤格式；集中在 Advice 可保證狀態碼、欄位與錯誤碼契約一致。
 * 【邊界】不負責業務規則本身；只做「例外 → HTTP 回應」映射。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 【職責】處理不支援的 HTTP 方法，回 405。
     * 【技巧】攔截 {@link HttpRequestMethodNotSupportedException}，寫入 {@link ErrorCodes#METHOD_NOT_ALLOWED}。
     * 【概念】405 表示「資源存在但動詞不對」；與 404（資源不存在）分開，方便客戶端修正呼叫方式。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                        HttpServletRequest request) {
        Map<String, Object> body = problemBody(
                HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ex.getMessage(), request.getRequestURI());
        body.put("errorCode", ErrorCodes.METHOD_NOT_ALLOWED);
        return problemResponse(HttpStatus.METHOD_NOT_ALLOWED, body);
    }

    /**
     * 【職責】處理 {@code @Valid} 驗證失敗，回 400 並附欄位錯誤清單。
     * 【技巧】從 {@link MethodArgumentNotValidException#getBindingResult()} 取出 {@link FieldError} 轉成 {@code errors[]}。
     * 【概念】Bean Validation 在進入 Service 前擋掉非法輸入；回傳欄位級訊息讓前端可對應表單標紅。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        Map<String, Object> body = problemBody(
                HttpStatus.BAD_REQUEST, "Validation Failed", "Request validation failed", request.getRequestURI());
        body.put("errorCode", ErrorCodes.VALIDATION_FAILED);
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        body.put("errors", errors);
        return problemResponse(HttpStatus.BAD_REQUEST, body);
    }

    /**
     * 【職責】處理風控拒單：一般規則回 422，重複訂單回 409。
     * 【技巧】依 {@link RiskRejectedException#getErrorCode()} 分支 HTTP 狀態；可選附 {@code ruleCode}。
     * 【概念】422 表示語意上無法處理（風控不通過）；409 表示與既有資源衝突（冪等／內容重複），兩者分流利於重試策略。
     */
    @ExceptionHandler(RiskRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleRiskRejected(RiskRejectedException ex,
                                                                  HttpServletRequest request) {
        HttpStatus status = ErrorCodes.DUPLICATE_ORDER.equals(ex.getErrorCode())
                ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        Map<String, Object> body = problemBody(status, titleFor(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
        body.put("errorCode", ex.getErrorCode());
        if (ex.getRuleCode() != null) {
            body.put("ruleCode", ex.getRuleCode());
        }
        return problemResponse(status, body);
    }

    /**
     * 【職責】處理合法流程下的狀態錯誤（不可取消／不可補成交等），回 422。
     * 【技巧】攔截 {@link InvalidOrderStateException}，帶上領域 {@code errorCode}。
     * 【概念】狀態機拒絕與風控拒絕都可能是 422，但 errorCode 不同；客戶端應依碼而非只看狀態碼判斷。
     */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOrderState(InvalidOrderStateException ex,
                                                                       HttpServletRequest request) {
        Map<String, Object> body = problemBody(
                HttpStatus.UNPROCESSABLE_ENTITY, titleFor(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
        body.put("errorCode", ex.getErrorCode());
        return problemResponse(HttpStatus.UNPROCESSABLE_ENTITY, body);
    }

    /**
     * 【職責】處理資源不存在，回 404。
     * 【技巧】攔截 {@link ResourceNotFoundException}，映射對應 {@code errorCode}（訂單／持倉／成交等）。
     * 【概念】404 是「查無此資源」的穩定契約；細節放在 detail／errorCode，避免洩漏內部查詢路徑。
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex,
                                                              HttpServletRequest request) {
        Map<String, Object> body = problemBody(
                HttpStatus.NOT_FOUND, titleFor(ex.getErrorCode()), ex.getMessage(), request.getRequestURI());
        body.put("errorCode", ex.getErrorCode());
        return problemResponse(HttpStatus.NOT_FOUND, body);
    }

    /**
     * 【職責】兜底未預期例外，回 500 且不外洩堆疊細節。
     * 【技巧】{@code @ExceptionHandler(Exception.class)} 放最後；固定 detail 為通用訊息。
     * 【概念】未捕捉例外若直接回傳 message，可能洩漏 SQL／路徑；對外只給穩定 errorCode，細節留在伺服器日誌。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, HttpServletRequest request) {
        Map<String, Object> body = problemBody(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Unexpected error", request.getRequestURI());
        body.put("errorCode", ErrorCodes.INTERNAL_ERROR);
        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, body);
    }

    /** 建立統一 Problem JSON 回應（含 Content-Type）。 */
    private ResponseEntity<Map<String, Object>> problemResponse(HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status)
                .header("Content-Type", "application/problem+json")
                .body(body);
    }

    /** 建立 Problem JSON 主體骨架（type／title／status／detail／instance）。 */
    private Map<String, Object> problemBody(HttpStatus status, String title, String detail, String instance) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", instance);
        return body;
    }

    /** 將 Spring FieldError 轉成前端友善的 field／message 對。 */
    private Map<String, String> toFieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", error.getDefaultMessage());
    }

    /** 依 errorCode 對應可讀 title，讓 Problem JSON 更易掃讀。 */
    private String titleFor(String errorCode) {
        return switch (errorCode) {
            case ErrorCodes.ORDER_NOT_FOUND -> "Order Not Found";
            case ErrorCodes.POSITION_NOT_FOUND -> "Position Not Found";
            case ErrorCodes.RISK_POSITION_LIMIT -> "Position Limit Exceeded";
            case ErrorCodes.RISK_EXPOSURE_LIMIT -> "Exposure Limit Exceeded";
            case ErrorCodes.RISK_INVALID_ORDER -> "Invalid Order";
            case ErrorCodes.DUPLICATE_ORDER -> "Duplicate Order";
            case ErrorCodes.RISK_VOLATILITY_LIMIT -> "Volatility Limit Exceeded";
            case ErrorCodes.RISK_OVERTRADING -> "Overtrading Limit Exceeded";
            case ErrorCodes.RISK_MARKET_CHOPPY -> "Market Not Suitable";
            case ErrorCodes.RISK_TREND_WEAK -> "Trend Not Confirmed";
            case ErrorCodes.RISK_SIGNAL_NOISY -> "Signal Too Noisy";
            case ErrorCodes.TRADE_NOT_FOUND -> "Trade Not Found";
            case ErrorCodes.ORDER_NOT_CANCELLABLE -> "Order Not Cancellable";
            case ErrorCodes.ORDER_NOT_FILLABLE -> "Order Not Fillable";
            default -> "Error";
        };
    }
}
