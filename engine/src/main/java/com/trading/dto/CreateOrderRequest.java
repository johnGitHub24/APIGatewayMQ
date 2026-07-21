package com.trading.dto;

import com.trading.domain.OrderSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 【職責】承載建立訂單的 HTTP 請求本文（POST {@code /api/v1/orders}）。
 * 【技巧】Lombok {@code @Data} + Jakarta Validation（{@code @NotBlank}/{@code @NotNull}）；由 Controller {@code @Valid} 觸發。
 * 【概念】DTO 是 API 契約邊界：只放客戶端可送的欄位，不含系統產生的 orderId／status。
 *         驗證失敗會進 {@link com.trading.config.GlobalExceptionHandler} 回 400。
 * 【邊界】不負責風控與持久化；通過驗證後才交給 Service／Kafka 流程。
 */
@Data
public class CreateOrderRequest {

    /** 客戶端冪等識別碼，可與 HTTP 標頭 Idempotency-Key 搭配。 */
    private String clientOrderId;

    /** 交易標的代碼（必填）。 */
    @NotBlank(message = "symbol must not be blank")
    private String symbol;

    /** 買賣方向（必填）。 */
    @NotNull(message = "side must not be null")
    private OrderSide side;

    /** 委託數量（必填，業務上須 &gt; 0）。 */
    @NotNull(message = "quantity must not be null")
    private BigDecimal quantity;

    /** 委託價格（必填，業務上須 &gt; 0）。 */
    @NotNull(message = "price must not be null")
    private BigDecimal price;
}
