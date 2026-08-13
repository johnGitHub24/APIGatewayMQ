package com.trading.application;

import com.trading.application.risk.RiskEngine;
import com.trading.application.risk.RiskResult;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import com.trading.dto.CreateOrderRequest;
import com.trading.dto.OrderResponse;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.entity.TradeEntity;
import com.trading.infrastructure.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【職責】保護 {@link TradingService} 下單／補成交／取消的狀態機契約。
 * 【技巧】Mockito 隔離風控、撮合與持久化；Case ID 與整合層同一預期。
 * 【概念】單元層驗證編排順序與例外語意；整合層再驗 DB／HTTP。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TradingServiceTest {

    @Mock private RiskEngine riskEngine;
    @Mock private MarketService marketService;
    @Mock private ExecutionEngine executionEngine;
    @Mock private OrderService orderService;
    @Mock private TradeService tradeService;
    @Mock private PositionService positionService;
    @Mock private OrderEventService orderEventService;
    @Mock private OutcomeRecordService outcomeRecordService;
    @Mock private DisciplineService disciplineService;
    @Mock private StructuredAuditLogger auditLogger;
    @Mock private OrderMapper orderMapper;

    private TradingService tradingService;

    @BeforeEach
    void setUp() {
        tradingService = new TradingService(riskEngine, marketService, executionEngine, orderService,
                tradeService, positionService, orderEventService, outcomeRecordService,
                disciplineService, auditLogger, orderMapper);
    }

    private CreateOrderRequest buyRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setSymbol("BTCUSDT");
        request.setSide(OrderSide.BUY);
        request.setQuantity(new BigDecimal("0.5"));
        request.setPrice(new BigDecimal("65000"));
        return request;
    }

    private OrderEntity order(long id, OrderStatus status, BigDecimal qty, BigDecimal filled) {
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
        entity.setClientOrderId("coid-1");
        entity.setSymbol("BTCUSDT");
        entity.setSide(OrderSide.BUY);
        entity.setQuantity(qty);
        entity.setPrice(new BigDecimal("65000"));
        entity.setStatus(status);
        entity.setFilledQuantity(filled);
        return entity;
    }

    @Test
    @DisplayName("ORDER-001 placeOrder approved path fills and returns response")
    void placeOrder_approved_fills() {
        when(positionService.getCurrentQuantity("BTCUSDT")).thenReturn(BigDecimal.ZERO);
        when(positionService.getTotalExposure()).thenReturn(BigDecimal.ZERO);
        when(marketService.getContext("BTCUSDT")).thenReturn(MarketContext.neutral("BTCUSDT"));
        when(riskEngine.validate(any(), any())).thenReturn(RiskResult.approve());
        when(orderService.createPending(any(), any(), any(), any(), any()))
                .thenReturn(order(1L, OrderStatus.NEW, new BigDecimal("0.5"), BigDecimal.ZERO));
        when(executionEngine.planInitialFill(any()))
                .thenReturn(new ExecutionEngine.ExecutionResult(new BigDecimal("0.5"), BigDecimal.ZERO, true));
        TradeEntity trade = new TradeEntity();
        trade.setId(10L);
        trade.setExecutedPrice(new BigDecimal("65000"));
        trade.setExecutedQty(new BigDecimal("0.5"));
        when(tradeService.record(eq(1L), any(), any())).thenReturn(trade);
        when(orderService.markFilled(any())).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            o.setStatus(OrderStatus.FILLED);
            o.setFilledQuantity(o.getQuantity());
            return o;
        });
        PositionEntity position = new PositionEntity();
        position.setSymbol("BTCUSDT");
        position.setQuantity(new BigDecimal("0.5"));
        when(positionService.updateAfterFill(any(), any(), any(), any())).thenReturn(position);
        when(orderMapper.toResponse(any())).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            OrderResponse response = new OrderResponse();
            response.setOrderId(o.getId());
            response.setStatus(o.getStatus());
            response.setSymbol(o.getSymbol());
            return response;
        });

        OrderResponse response = tradingService.placeOrder(buyRequest(), "coid-1");

        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.FILLED);
        verify(orderService).createPending(eq("coid-1"), eq("BTCUSDT"), eq(OrderSide.BUY),
                eq(new BigDecimal("0.5")), eq(new BigDecimal("65000")));
        verify(tradeService).record(eq(1L), eq(new BigDecimal("65000")), eq(new BigDecimal("0.5")));
    }

    @Test
    @DisplayName("ORDER-005 placeOrder risk reject persists rejected order then throws")
    void placeOrder_riskRejected_throws() {
        when(positionService.getCurrentQuantity(anyString())).thenReturn(BigDecimal.ZERO);
        when(positionService.getTotalExposure()).thenReturn(BigDecimal.ZERO);
        when(marketService.getContext(anyString())).thenReturn(MarketContext.neutral("BTCUSDT"));
        when(riskEngine.validate(any(), any()))
                .thenReturn(RiskResult.reject(ErrorCodes.RISK_POSITION_LIMIT, "R001", "limit"));
        when(orderService.createRejected(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(order(2L, OrderStatus.REJECTED, new BigDecimal("0.5"), BigDecimal.ZERO));

        assertThatThrownBy(() -> tradingService.placeOrder(buyRequest(), "coid-risk"))
                .isInstanceOf(RiskRejectedException.class)
                .extracting(ex -> ((RiskRejectedException) ex).getErrorCode())
                .isEqualTo(ErrorCodes.RISK_POSITION_LIMIT);
        verify(orderService).createRejected(eq("coid-risk"), eq("BTCUSDT"), eq(OrderSide.BUY),
                any(), any(), eq("R001"), eq("limit"));
        verify(orderService, never()).createPending(any(), any(), any(), any(), any());
    }

    @Test
    void completeFill_partiallyFilled_fillsRemainder() {
        OrderEntity partial = order(3L, OrderStatus.PARTIALLY_FILLED, new BigDecimal("1"), new BigDecimal("0.4"));
        when(orderService.getById(3L)).thenReturn(partial);
        when(marketService.getContext("BTCUSDT")).thenReturn(MarketContext.neutral("BTCUSDT"));
        when(executionEngine.planRemainingFill(any(), eq(new BigDecimal("0.4"))))
                .thenReturn(new ExecutionEngine.ExecutionResult(new BigDecimal("0.6"), BigDecimal.ZERO, true));
        when(tradeService.record(eq(3L), any(), any())).thenReturn(new TradeEntity());
        when(orderService.markFilled(any())).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            o.setStatus(OrderStatus.FILLED);
            return o;
        });
        PositionEntity position = new PositionEntity();
        position.setSymbol("BTCUSDT");
        position.setQuantity(new BigDecimal("1"));
        when(positionService.updateAfterFill(any(), any(), any(), any())).thenReturn(position);
        when(orderMapper.toResponse(any())).thenAnswer(inv -> {
            OrderResponse response = new OrderResponse();
            response.setStatus(((OrderEntity) inv.getArgument(0)).getStatus());
            return response;
        });

        OrderResponse response = tradingService.completeFill(3L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.FILLED);
        verify(orderService).markFilled(any());
    }

    @Test
    void completeFill_notPartial_throws() {
        when(orderService.getById(4L)).thenReturn(order(4L, OrderStatus.NEW, new BigDecimal("1"), BigDecimal.ZERO));

        assertThatThrownBy(() -> tradingService.completeFill(4L))
                .isInstanceOf(InvalidOrderStateException.class)
                .extracting(ex -> ((InvalidOrderStateException) ex).getErrorCode())
                .isEqualTo(ErrorCodes.ORDER_NOT_FILLABLE);
    }

    @Test
    void cancelOrder_new_marksCancelled() {
        when(orderService.getById(5L)).thenReturn(order(5L, OrderStatus.NEW, new BigDecimal("1"), BigDecimal.ZERO));
        when(orderService.markCancelled(any())).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            o.setStatus(OrderStatus.CANCELLED);
            return o;
        });
        when(orderMapper.toResponse(any())).thenAnswer(inv -> {
            OrderResponse response = new OrderResponse();
            response.setStatus(((OrderEntity) inv.getArgument(0)).getStatus());
            return response;
        });

        OrderResponse response = tradingService.cancelOrder(5L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderEventService).log(eq(5L), any(), isNull(), isNull(), isNull());
    }

    @Test
    void cancelOrder_filled_throws() {
        when(orderService.getById(6L)).thenReturn(order(6L, OrderStatus.FILLED, new BigDecimal("1"), new BigDecimal("1")));

        assertThatThrownBy(() -> tradingService.cancelOrder(6L))
                .isInstanceOf(InvalidOrderStateException.class)
                .extracting(ex -> ((InvalidOrderStateException) ex).getErrorCode())
                .isEqualTo(ErrorCodes.ORDER_NOT_CANCELLABLE);
    }
}
