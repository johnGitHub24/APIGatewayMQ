package com.trading.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PnLCalculatorTest {

    private final PnLCalculator calculator = new PnLCalculator();

    @Test
    void calculatesUnrealizedPnlForLongPosition() {
        BigDecimal pnl = calculator.calculateUnrealized(
                new BigDecimal("2"), new BigDecimal("100"), new BigDecimal("110"));
        assertThat(pnl).isEqualByComparingTo("20");
    }

    @Test
    void zeroQuantity_returnsZero() {
        BigDecimal pnl = calculator.calculateUnrealized(
                BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("110"));
        assertThat(pnl).isEqualByComparingTo("0");
    }
}
