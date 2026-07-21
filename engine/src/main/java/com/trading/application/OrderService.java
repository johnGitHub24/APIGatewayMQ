package com.trading.application;

import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 【職責】訂單持久化：建立、狀態標記與查詢（不含風控／成交編排）。
 * 【技巧】{@code @Transactional}／{@code readOnly}；狀態方法集中改 {@link OrderStatus} 後 {@code save}。
 * 【概念】把「資料怎麼存」與「流程怎麼走」分開：本類管實體欄位，{@link TradingService} 管順序。
 * 【邊界】不呼叫風控、不寫成交／持倉；找不到資源拋 {@link ResourceNotFoundException}。
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    /** 建構子注入訂單 Repository。 */
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * 【職責】建立風控拒絕的訂單（REJECTED，附規則與原因）。
     * 【技巧】一次寫入終態，供稽核保留「被拒的意圖」。
     * 【概念】拒單仍落庫，才能事後統計哪條規則擋最多。
     */
    @Transactional
    public OrderEntity createRejected(String clientOrderId, String symbol, OrderSide side,
                                      BigDecimal quantity, BigDecimal price,
                                      String ruleCode, String reason) {
        OrderEntity order = new OrderEntity();
        order.setClientOrderId(clientOrderId);
        order.setSymbol(symbol);
        order.setSide(side);
        order.setQuantity(quantity);
        order.setPrice(price);
        order.setStatus(OrderStatus.REJECTED);
        order.setRiskRuleCode(ruleCode);
        order.setRejectReason(reason);
        return orderRepository.save(order);
    }

    /**
     * 【職責】建立待成交新訂單（狀態 NEW）。
     * 【技巧】設定基本欄位後 {@code save}。
     * 【概念】NEW＝已通過風控、等待（模擬）撮合。
     */
    @Transactional
    public OrderEntity createPending(String clientOrderId, String symbol, OrderSide side,
                                     BigDecimal quantity, BigDecimal price) {
        OrderEntity order = new OrderEntity();
        order.setClientOrderId(clientOrderId);
        order.setSymbol(symbol);
        order.setSide(side);
        order.setQuantity(quantity);
        order.setPrice(price);
        order.setStatus(OrderStatus.NEW);
        return orderRepository.save(order);
    }

    /**
     * 【職責】將既有訂單標記為拒絕。
     * 【技巧】就地改狀態與原因後儲存。
     * 【概念】用於流程中途改拒（若有）；與 {@link #createRejected} 的「直接建拒單」互補。
     */
    @Transactional
    public OrderEntity markRejected(OrderEntity order, String ruleCode, String reason) {
        order.setStatus(OrderStatus.REJECTED);
        order.setRiskRuleCode(ruleCode);
        order.setRejectReason(reason);
        return orderRepository.save(order);
    }

    /**
     * 【職責】將訂單標記為完全成交（FILLED）。
     * 【技巧】{@code filledQuantity = quantity}。
     * 【概念】終態之一；之後不可再 cancel／fill。
     */
    @Transactional
    public OrderEntity markFilled(OrderEntity order) {
        order.setStatus(OrderStatus.FILLED);
        order.setFilledQuantity(order.getQuantity());
        return orderRepository.save(order);
    }

    /**
     * 【職責】將訂單標記為部分成交（PARTIALLY_FILLED）。
     * 【技巧】寫入目前累計成交量。
     * 【概念】中間態：還可 completeFill 或 cancel。
     */
    @Transactional
    public OrderEntity markPartiallyFilled(OrderEntity order, BigDecimal filledQuantity) {
        order.setStatus(OrderStatus.PARTIALLY_FILLED);
        order.setFilledQuantity(filledQuantity);
        return orderRepository.save(order);
    }

    /**
     * 【職責】將訂單標記為已取消（CANCELLED）。
     * 【技巧】只改狀態後儲存。
     * 【概念】終態；未成交部分不再有效。
     */
    @Transactional
    public OrderEntity markCancelled(OrderEntity order) {
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    /**
     * 【職責】依主鍵查詢訂單；不存在則拋資源不存在例外。
     * 【技巧】{@code Optional.orElseThrow}。
     * 【概念】查不到用例外比回 null 更安全，避免 NPE 一路傳下去。
     */
    @Transactional(readOnly = true)
    public OrderEntity getById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND",
                        "Order with id " + orderId + " not found"));
    }

    /**
     * 【職責】分頁查詢訂單，可依商品與狀態篩選。
     * 【技巧】依參數組合呼叫不同 Repository 方法。
     * 【概念】動態條件用分支選查詢，比字串拼 SQL 安全。
     */
    @Transactional(readOnly = true)
    public Page<OrderEntity> list(String symbol, OrderStatus status, Pageable pageable) {
        if (symbol != null && status != null) {
            return orderRepository.findBySymbolAndStatus(symbol, status, pageable);
        }
        if (symbol != null) {
            return orderRepository.findBySymbol(symbol, pageable);
        }
        if (status != null) {
            return orderRepository.findByStatus(status, pageable);
        }
        return orderRepository.findAll(pageable);
    }
}
