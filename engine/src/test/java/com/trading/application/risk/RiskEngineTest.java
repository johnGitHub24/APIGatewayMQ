package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import com.trading.domain.OrderSide;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RiskEngineTest {

    @Mock
    private OrderRepository orderRepository;

    private RiskEngine riskEngine;
    private RiskProperties props;

    @BeforeEach
    void setUp() {
        props = new RiskProperties();
        props.setMaxPositionPerSymbol(new BigDecimal("100"));
        props.setMaxTotalExposure(new BigDecimal("1000000"));
        props.setMaxVolatilityIndex(new BigDecimal("0.80"));
        props.setMaxOrdersPerWindow(200);
        props.setMinTrendStrength(new BigDecimal("0.40"));
        props.setMaxSignalNoise(new BigDecimal("0.70"));
        props.setReduceVolatilityThreshold(new BigDecimal("0.65"));
        props.setVolatilityReduceRatio(new BigDecimal("0.50"));
        List<RiskRule> rules = List.of(
                new SizeValidationRule(),
                new DuplicateCheckRule(props, orderRepository),
                new MarketSuitabilityRule(),
                new TrendConfirmationRule(props),
                new SignalValidationRule(props),
                new VolatilityAdjustRule(props),
                new PositionLimitRule(props),
                new ExposureLimitRule(props),
                new VolatilityRule(props),
                new OvertradingRule(props)
        );
        riskEngine = new RiskEngineImpl(rules);
    }

    @Test
    @DisplayName("ORDER-001 valid order is approved")
    void ORDER_001_validOrder_approves() {
        RiskResult result = riskEngine.validate(context("key-1", "BTCUSDT", OrderSide.BUY,
                new BigDecimal("0.5"), new BigDecimal("65000"), BigDecimal.ZERO, BigDecimal.ZERO),
                MarketContext.neutral("BTCUSDT"));
        assertThat(result.isApproved()).isTrue();
    }

    @Test
    @DisplayName("ORDER-002 at max position quantity is still approved")
    void ORDER_002_atPositionLimit_approves() {
        RiskResult result = riskEngine.validate(context(null, "BTCUSDT", OrderSide.BUY,
                new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO),
                MarketContext.neutral("BTCUSDT"));
        assertThat(result.isApproved()).isTrue();
    }

    @Test
    @DisplayName("ORDER-004 zero quantity is rejected as R003")
    void ORDER_004_zeroQuantity_rejectsR003() {
        RiskResult result = riskEngine.validate(context(null, "BTCUSDT", OrderSide.BUY,
                BigDecimal.ZERO, new BigDecimal("65000"), BigDecimal.ZERO, BigDecimal.ZERO),
                MarketContext.neutral("BTCUSDT"));
        assertThat(result.isApproved()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCodes.RISK_INVALID_ORDER);
    }

    @Test
    @DisplayName("ORDER-005 projected position over limit is rejected as R001")
    void ORDER_005_positionLimit_rejectsR001() {
        RiskResult result = riskEngine.validate(context(null, "BTCUSDT", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("1000"), new BigDecimal("60"),
                BigDecimal.ZERO), MarketContext.neutral("BTCUSDT"));
        assertThat(result.isApproved()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCodes.RISK_POSITION_LIMIT);
    }

    @Test
    void ORDER_005_volatilityLimit_rejectsR005() {
        MarketContext extreme = new MarketContext("BTCVOL",
                new BigDecimal("0.95"), true, new BigDecimal("0.70"),
                false, new BigDecimal("0.20"), 0);
        RiskResult result = riskEngine.validate(context(null, "BTCVOL", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO),
                extreme);
        assertThat(result.isApproved()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCodes.RISK_VOLATILITY_LIMIT);
    }

    @Test
    void R007_choppyMarket_rejects() {
        MarketContext choppy = new MarketContext("ETHCHOP",
                new BigDecimal("0.30"), false, new BigDecimal("0.45"),
                true, new BigDecimal("0.20"), 0);
        RiskResult result = riskEngine.validate(context(null, "ETHCHOP", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO),
                choppy);
        assertThat(result.getErrorCode()).isEqualTo(ErrorCodes.RISK_MARKET_CHOPPY);
    }

    @Test
    void R008_weakTrend_rejects() {
        MarketContext weak = new MarketContext("BTCNOTREND",
                new BigDecimal("0.30"), false, new BigDecimal("0.25"),
                false, new BigDecimal("0.20"), 0);
        RiskResult result = riskEngine.validate(context(null, "BTCNOTREND", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO),
                weak);
        assertThat(result.getErrorCode()).isEqualTo(ErrorCodes.RISK_TREND_WEAK);
    }

    @Test
    void R010_highVolatility_reducesQuantity() {
        MarketContext highVol = new MarketContext("ETHHIGHVOL",
                new BigDecimal("0.72"), true, new BigDecimal("0.70"),
                false, new BigDecimal("0.20"), 0);
        RiskResult result = riskEngine.validate(context(null, "ETHHIGHVOL", OrderSide.BUY,
                new BigDecimal("10"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO),
                highVol);
        assertThat(result.isApproved()).isTrue();
        assertThat(result.getAdjustedQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void ORDER_006_duplicateKey_rejectsR004() {
        when(orderRepository.findByClientOrderId(anyString())).thenReturn(Optional.of(new OrderEntity()));
        RiskResult result = riskEngine.validate(context("dup-key", "BTCUSDT", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO),
                MarketContext.neutral("BTCUSDT"));
        assertThat(result.getErrorCode()).isEqualTo(ErrorCodes.DUPLICATE_ORDER);
    }

    private RiskResult.OrderRiskContext context(String clientOrderId, String symbol, OrderSide side,
                                                BigDecimal qty, BigDecimal price,
                                                BigDecimal currentQty, BigDecimal exposure) {
        return new RiskResult.OrderRiskContext(clientOrderId, symbol, side, qty, price, currentQty, exposure);
    }
}
