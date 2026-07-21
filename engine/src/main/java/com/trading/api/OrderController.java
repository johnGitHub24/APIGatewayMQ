package com.trading.api;

import com.trading.application.OrderEventService;
import com.trading.application.OrderService;
import com.trading.application.TradeService;
import com.trading.application.TradingService;
import com.trading.domain.OrderStatus;
import com.trading.dto.*;
import com.trading.infrastructure.entity.OrderEventEntity;
import com.trading.infrastructure.mapper.OrderMapper;
import com.trading.infrastructure.repository.OrderEventRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 【職責】訂單 REST API 入口：下單、查詢、取消、事件時間軸與教學用手動成交。
 * 【技巧】{@code @RestController} + {@code /api/v1/orders}；{@code @Valid} 驗證 body；
 *         {@code Idempotency-Key} 標頭；{@link ServletUriComponentsBuilder} 組 201 Location。
 * 【概念】Controller 只做「收參數 → 轉交 Service → 組 HTTP」；風控、狀態機、持倉更新都在
 *         {@link TradingService}。冪等鍵避免網路重送造成重複下單。
 * 【邊界】不直接 {@code save}/{@code delete} Domain、不寫風控規則；查事件目前讀 Repository（薄查詢）。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final TradingService tradingService;
    private final OrderService orderService;
    private final TradeService tradeService;
    private final OrderMapper orderMapper;
    private final OrderEventRepository orderEventRepository;

    /** 建構子注入下單編排與查詢依賴。 */
    public OrderController(TradingService tradingService, OrderService orderService, TradeService tradeService,
                           OrderMapper orderMapper, OrderEventRepository orderEventRepository) {
        this.tradingService = tradingService;
        this.orderService = orderService;
        this.tradeService = tradeService;
        this.orderMapper = orderMapper;
        this.orderEventRepository = orderEventRepository;
    }

    /**
     * 【職責】建立新訂單（同步下單路徑）。
     * 【技巧】{@code @RequestBody @Valid}；可選 {@code Idempotency-Key}；{@code ResponseEntity.created(location)}。
     * 【概念】201 + Location 是 REST 慣例：告訴客戶端「資源已建立，去這個 URI 查」。
     *         相同冪等鍵重送應回既有訂單，而非再開一筆。
     * @param request        下單請求（symbol、side、quantity、price 等）
     * @param idempotencyKey 可選冪等鍵；相同鍵重送時回傳既有訂單
     * @return HTTP 201，body 為訂單回應
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestBody @Valid CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        OrderResponse response = tradingService.placeOrder(request, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getOrderId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * 【職責】查詢單筆訂單，並附帶該訂單的成交明細。
     * 【技巧】{@code orderMapper.toResponse} + Stream 映射 trades。
     * 【概念】訂單是「意圖」，成交是「實際發生」；一筆訂單可對應多筆部分成交。
     * @param orderId 訂單主鍵
     * @return 含 trades 的訂單回應
     */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        OrderResponse response = orderMapper.toResponse(orderService.getById(orderId));
        response.setTrades(tradeService.findByOrderId(orderId).stream()
                .map(orderMapper::toTradeResponse)
                .toList());
        return response;
    }

    /**
     * 【職責】分頁列出訂單，可依標的與狀態過濾。
     * 【技巧】{@link PageRequest}；{@code Math.min/max} 夾住 size；組裝 {@link PagedResponse}。
     * 【概念】分頁避免一次回傳全表；size 上限 100 是防濫用的常見保護。
     * @param symbol 可選標的
     * @param status 可選狀態
     * @param page   頁碼（從 0）
     * @param size   每頁筆數（預設 20，上限 100）
     * @return 分頁訂單回應
     */
    @GetMapping
    public PagedResponse<OrderResponse> listOrders(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<OrderResponse> result = orderService
                .list(symbol, status, PageRequest.of(page, safeSize))
                .map(orderMapper::toResponse);
        PagedResponse<OrderResponse> response = new PagedResponse<>();
        response.setData(result.getContent());
        response.setMeta(PagedResponse.PageMeta.of(page, safeSize, result.getTotalElements()));
        return response;
    }

    /**
     * 【職責】查詢訂單事件時間軸（建立、風控、成交、取消等）。
     * 【技巧】先 {@code getById} 確認存在；再依 {@code createdAt} 升序取事件。
     * 【概念】事件表是審計軌跡：事後可回答「這張單何時被拒、被誰規則擋」。
     * @param orderId 訂單主鍵
     * @return 事件列表回應
     */
    @GetMapping("/{orderId}/events")
    public OrderEventsResponse getOrderEvents(@PathVariable Long orderId) {
        orderService.getById(orderId);
        List<OrderEventEntity> events = orderEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        return orderMapper.toEventsResponse(orderId, events);
    }

    /**
     * 【職責】手動觸發剩餘成交流程（模擬撮合），供教學與整合測試。
     * 【技巧】委派 {@link TradingService#completeFill(Long)}。
     * 【概念】真實交易所由撮合引擎推送；此處用 API 模擬「補足部分成交」。
     * 【邊界】僅適用 PARTIALLY_FILLED；其他狀態由 Service 拋狀態例外。
     * @param orderId 待補成交的訂單主鍵
     * @return 更新後訂單回應
     */
    @PostMapping("/{orderId}/fill")
    public OrderResponse completeFill(@PathVariable Long orderId) {
        return tradingService.completeFill(orderId);
    }

    /**
     * 【職責】取消尚未完全成交的訂單。
     * 【技巧】{@code @PatchMapping} 表示部分更新狀態；委派 {@link TradingService#cancelOrder}。
     * 【概念】已 FILLED／REJECTED 等終態不可取消——狀態機由 Service 守護。
     * @param orderId 要取消的訂單主鍵
     * @return 取消後訂單回應
     */
    @PatchMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long orderId) {
        return tradingService.cancelOrder(orderId);
    }
}
