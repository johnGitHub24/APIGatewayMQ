package com.trading.engine.web;

import com.trading.application.OrderService;
import com.trading.application.ResourceNotFoundException;
import com.trading.domain.ErrorCodes;
import com.trading.dto.OrderResponse;
import com.trading.infrastructure.mapper.OrderMapper;
import com.trading.infrastructure.repository.OrderRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【職責】依 {@code clientOrderId} 查單筆訂單，供 Gateway／外部系統做冪等追蹤。
 * 【技巧】{@code @GetMapping(params = "clientOrderId")} 用 query 參數區分路由；
 *         {@code Optional.map/orElseThrow} 轉 DTO 或 404 例外。
 * 【概念】客戶端訂單 ID 是跨系統的「業務鍵」；Gateway 常先拿它問 Engine「這筆有沒有了」。
 * 【邊界】不經 {@link OrderService} 狀態流轉；只讀 Repository。不負責下單／取消。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderLookupController {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    /** 建構子注入訂單查詢依賴（供 Gateway 代理）。 */
    public OrderLookupController(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    /**
     * 【職責】依客戶端訂單 ID 查詢單筆訂單。
     * 【技巧】Repository {@code findByClientOrderId} + Mapper；找不到拋 {@link ResourceNotFoundException}。
     * 【概念】與路徑 {@code /{orderId}} 互補：一個用內部主鍵，一個用外部冪等鍵。
     * @param clientOrderId 客戶端／冪等訂單識別
     * @return 訂單回應
     */
    @GetMapping(params = "clientOrderId")
    public OrderResponse findByClientOrderId(@RequestParam String clientOrderId) {
        return orderRepository.findByClientOrderId(clientOrderId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCodes.ORDER_NOT_FOUND, "Order not found for clientOrderId=" + clientOrderId));
    }
}
