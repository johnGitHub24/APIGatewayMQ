package com.trading.application;

import com.trading.config.RiskProperties;
import com.trading.infrastructure.entity.OrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ExecutionEngineTest {

    private ExecutionEngine executionEngine;
    private RiskProperties props;

    @BeforeEach
    void setUp() {
        props = new RiskProperties();
        props.setPartialFillThreshold(new BigDecimal("10"));
        props.setPartialFillRatio(new BigDecimal("0.50"));
        executionEngine = new ExecutionEngine(props);
    }

    @Test
    void smallOrder_fullFill() {
        OrderEntity order = new OrderEntity();
        order.setQuantity(new BigDecimal("5"));

        ExecutionEngine.ExecutionResult result = executionEngine.planInitialFill(order);
        assertThat(result.complete()).isTrue();
        assertThat(result.fillQty()).isEqualByComparingTo("5");
    }

    @Test
    void largeOrder_partialFill() {
        OrderEntity order = new OrderEntity();
        order.setQuantity(new BigDecimal("20"));

        ExecutionEngine.ExecutionResult result = executionEngine.planInitialFill(order);
        assertThat(result.complete()).isFalse();
        assertThat(result.fillQty()).isEqualByComparingTo("10");
        assertThat(result.remainingQty()).isEqualByComparingTo("10");
    }

    @Test
    void remainingFill_completesOrder() {
        OrderEntity order = new OrderEntity();
        order.setQuantity(new BigDecimal("20"));

        ExecutionEngine.ExecutionResult result = executionEngine.planRemainingFill(order, new BigDecimal("10"));
        assertThat(result.complete()).isTrue();
        assertThat(result.fillQty()).isEqualByComparingTo("10");
    }
}
