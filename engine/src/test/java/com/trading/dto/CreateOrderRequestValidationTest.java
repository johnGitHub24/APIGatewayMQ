package com.trading.dto;

import com.trading.domain.OrderSide;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CreateOrderRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("ORDER-003 missing required symbol fails bean validation")
    void ORDER_003_missingSymbol_failsValidation() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setSide(OrderSide.BUY);
        request.setQuantity(new BigDecimal("1"));
        request.setPrice(new BigDecimal("100"));

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> "symbol".equals(v.getPropertyPath().toString()));
    }

    @Test
    void ORDER_003_missingSide_failsValidation() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setSymbol("BTCUSDT");
        request.setQuantity(new BigDecimal("1"));
        request.setPrice(new BigDecimal("100"));

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> "side".equals(v.getPropertyPath().toString()));
    }

    @Test
    void validRequest_passesValidation() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setSymbol("BTCUSDT");
        request.setSide(OrderSide.BUY);
        request.setQuantity(new BigDecimal("1"));
        request.setPrice(new BigDecimal("100"));

        assertThat(validator.validate(request)).isEmpty();
    }
}
