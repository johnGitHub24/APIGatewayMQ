package com.trading.application;

import com.trading.application.risk.RiskEngine;
import com.trading.application.risk.RiskResult;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import com.trading.domain.OrderEventType;
import com.trading.domain.OrderStatus;
import com.trading.dto.CreateOrderRequest;
import com.trading.dto.OrderResponse;
import com.trading.dto.TradeResponse;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.entity.TradeEntity;
import com.trading.infrastructure.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 【職責】交易核心編排：風控 → 建單 → 模擬成交 → 持倉 → 事件／審計。
 * 【技巧】{@code @Transactional(noRollbackFor = RiskRejectedException.class)} 保留拒單落庫；
 *         協調 {@link RiskEngine}、{@link ExecutionEngine}、各 *Service。
 * 【概念】這是「下單故事的導演」：各服務是演員，本類決定先後與例外語意。
 * 【邊界】不實作單一風控規則；不直接操作 Kafka；HTTP 由 Controller 包裝。
 */
@Service
public class TradingService {

    private final RiskEngine riskEngine;
    private final MarketService marketService;
    private final ExecutionEngine executionEngine;
    private final OrderService orderService;
    private final TradeService tradeService;
    private final PositionService positionService;
    private final OrderEventService orderEventService;
    private final OutcomeRecordService outcomeRecordService;
    private final DisciplineService disciplineService;
    private final StructuredAuditLogger auditLogger;
    private final OrderMapper orderMapper;

    /** 建構子注入下單編排所需協作者。 */
    public TradingService(RiskEngine riskEngine, MarketService marketService, ExecutionEngine executionEngine,
                          OrderService orderService, TradeService tradeService, PositionService positionService,
                          OrderEventService orderEventService, OutcomeRecordService outcomeRecordService,
                          DisciplineService disciplineService, StructuredAuditLogger auditLogger,
                          OrderMapper orderMapper) {
        this.riskEngine = riskEngine;
        this.marketService = marketService;
        this.executionEngine = executionEngine;
        this.orderService = orderService;
        this.tradeService = tradeService;
        this.positionService = positionService;
        this.orderEventService = orderEventService;
        this.outcomeRecordService = outcomeRecordService;
        this.disciplineService = disciplineService;
        this.auditLogger = auditLogger;
        this.orderMapper = orderMapper;
    }

    /**
     * 【職責】下單主流程：組風控上下文 → 驗證 → 建單（或拒單落庫）→ 嘗試成交。
     * 【技巧】冪等鍵優先當 clientOrderId；通過後可用 R010 調整數量；拒單後拋 {@link RiskRejectedException}。
     * 【概念】{@code noRollbackFor}：拒單例外仍要留下 REJECTED 列與事件，故不能整筆 rollback。
     *         重複單（DUPLICATE）通常不再建第二筆拒單。
     * @param request        下單請求
     * @param idempotencyKey 可選冪等鍵
     * @return 訂單回應（含可能的成交摘要）
     */
    @Transactional(noRollbackFor = RiskRejectedException.class)
    public OrderResponse placeOrder(CreateOrderRequest request, String idempotencyKey) {
        String clientOrderId = resolveClientOrderId(request, idempotencyKey);
        BigDecimal currentQty = positionService.getCurrentQuantity(request.getSymbol());
        BigDecimal totalExposure = positionService.getTotalExposure();
        MarketContext market = marketService.getContext(request.getSymbol());

        RiskResult.OrderRiskContext context = new RiskResult.OrderRiskContext(
                clientOrderId, request.getSymbol(), request.getSide(),
                request.getQuantity(), request.getPrice(), currentQty, totalExposure);

        RiskResult riskResult = riskEngine.validate(context, market);

        if (!riskResult.isApproved()) {
            if (!ErrorCodes.DUPLICATE_ORDER.equals(riskResult.getErrorCode())) {
                OrderEntity rejected = orderService.createRejected(
                        clientOrderId, request.getSymbol(), request.getSide(),
                        request.getQuantity(), request.getPrice(),
                        riskResult.getRuleCode(), riskResult.getDetail());
                orderEventService.log(rejected.getId(), OrderEventType.RECEIVED, null, null, null);
                orderEventService.log(rejected.getId(), OrderEventType.RISK_CHECK, null, null, null);
                orderEventService.log(rejected.getId(), OrderEventType.REJECTED,
                        riskResult.getRuleCode(), riskResult.getDetail(), null);
                auditLogger.logOrderEvent("REJECTED", rejected.getId(), request.getSymbol(), riskResult.getDetail());
            }
            throw new RiskRejectedException(riskResult.getErrorCode(), riskResult.getRuleCode(), riskResult.getDetail());
        }

        BigDecimal effectiveQty = riskResult.hasQuantityAdjustment()
                ? riskResult.getAdjustedQuantity()
                : request.getQuantity();

        OrderEntity order = orderService.createPending(
                clientOrderId, request.getSymbol(), request.getSide(),
                effectiveQty, request.getPrice());

        orderEventService.log(order.getId(), OrderEventType.RECEIVED, null, null, null);
        orderEventService.log(order.getId(), OrderEventType.RISK_CHECK, null, null, null);
        if (riskResult.hasQuantityAdjustment()) {
            orderEventService.log(order.getId(), OrderEventType.APPROVED, "R010",
                    "Quantity reduced due to volatility",
                    "{\"originalQty\":" + request.getQuantity() + ",\"adjustedQty\":" + effectiveQty + "}");
        } else {
            orderEventService.log(order.getId(), OrderEventType.APPROVED, null, null, null);
        }
        auditLogger.logOrderEvent("APPROVED", order.getId(), order.getSymbol(), "qty=" + effectiveQty);

        return executeFill(order, market);
    }

    /**
     * 【職責】對 PARTIALLY_FILLED 訂單補足剩餘成交。
     * 【技巧】狀態檢查後再委派私有 {@code executeFill}。
     * 【概念】模擬「第二次撮合回報」；非部分成交則拋 {@link InvalidOrderStateException}。
     * @param orderId 訂單主鍵
     * @return 更新後訂單回應
     */
    @Transactional
    public OrderResponse completeFill(Long orderId) {
        OrderEntity order = orderService.getById(orderId);
        if (order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new InvalidOrderStateException(ErrorCodes.ORDER_NOT_FILLABLE,
                    "Order " + orderId + " is not partially filled");
        }
        MarketContext market = marketService.getContext(order.getSymbol());
        return executeFill(order, market);
    }

    /**
     * 【職責】取消尚未完全成交的訂單（NEW 或 PARTIALLY_FILLED）。
     * 【技巧】狀態守衛 → {@code markCancelled} → 事件與審計。
     * 【概念】終態不可取消；狀態機規則集中在此，避免 Controller 散落 if。
     * @param orderId 訂單主鍵
     * @return 取消後訂單回應
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        OrderEntity order = orderService.getById(orderId);
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new InvalidOrderStateException(ErrorCodes.ORDER_NOT_CANCELLABLE,
                    "Order " + orderId + " cannot be cancelled in status " + order.getStatus());
        }
        order = orderService.markCancelled(order);
        orderEventService.log(order.getId(), OrderEventType.CANCELLED, null, null, null);
        auditLogger.logOrderEvent("CANCELLED", order.getId(), order.getSymbol(), null);
        return orderMapper.toResponse(order);
    }

    private OrderResponse executeFill(OrderEntity order, MarketContext market) {
        BigDecimal alreadyFilled = order.getFilledQuantity() != null ? order.getFilledQuantity() : BigDecimal.ZERO;
        ExecutionEngine.ExecutionResult plan = alreadyFilled.compareTo(BigDecimal.ZERO) > 0
                ? executionEngine.planRemainingFill(order, alreadyFilled)
                : executionEngine.planInitialFill(order);

        if (plan.fillQty().compareTo(BigDecimal.ZERO) <= 0) {
            return buildResponse(order, null);
        }

        TradeEntity trade = tradeService.record(order.getId(), order.getPrice(), plan.fillQty());
        BigDecimal newFilled = alreadyFilled.add(plan.fillQty());
        order.setFilledQuantity(newFilled);

        if (plan.complete()) {
            order = orderService.markFilled(order);
            orderEventService.log(order.getId(), OrderEventType.FILLED, null, null, null);
        } else {
            order = orderService.markPartiallyFilled(order, newFilled);
            orderEventService.log(order.getId(), OrderEventType.PARTIALLY_FILLED, null, null,
                    "{\"filledQty\":" + newFilled + "}");
        }

        PositionEntity position = positionService.updateAfterFill(
                order.getSymbol(), order.getSide(), plan.fillQty(), order.getPrice());
        orderEventService.log(order.getId(), OrderEventType.POSITION_UPDATED, null, null,
                "{\"symbol\":\"" + position.getSymbol() + "\",\"quantity\":" + position.getQuantity() + "}");

        outcomeRecordService.recordOutcome(order.getId(), order.getSymbol(), plan.fillQty(), order.getPrice(), position);
        disciplineService.evaluate(order.getId(), market);
        auditLogger.logOrderEvent(order.getStatus().name(), order.getId(), order.getSymbol(),
                "fillQty=" + plan.fillQty());

        return buildResponse(order, trade);
    }

    private OrderResponse buildResponse(OrderEntity order, TradeEntity trade) {
        OrderResponse response = orderMapper.toResponse(order);
        if (trade != null) {
            response.setTrade(new TradeResponse(
                    trade.getExecutedPrice(), trade.getExecutedQty(), trade.getExecutedAt()));
        }
        return response;
    }

    private String resolveClientOrderId(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return request.getClientOrderId();
    }
}
