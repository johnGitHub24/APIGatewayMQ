package com.trading.application;

import com.trading.infrastructure.entity.TradeEntity;
import com.trading.infrastructure.repository.TradeRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【職責】保護 {@link TradeService} 成交寫入與查詢。
 * 【技巧】Mockito 驗證 save／分頁分支與 404 例外。
 * 【概念】成交列是對帳事實來源，單測鎖定 API 依賴的查詢契約。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private TradeService tradeService;

    @Test
    void record_persistsPriceAndQty() {
        when(tradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TradeEntity saved = tradeService.record(7L, new BigDecimal("65000"), new BigDecimal("0.5"));

        assertThat(saved.getOrderId()).isEqualTo(7L);
        assertThat(saved.getExecutedPrice()).isEqualByComparingTo("65000");
        assertThat(saved.getExecutedQty()).isEqualByComparingTo("0.5");
    }

    @Test
    void getById_missing_throws() {
        when(tradeRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.getById(9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_found_returnsEntity() {
        TradeEntity trade = new TradeEntity();
        trade.setId(8L);
        when(tradeRepository.findById(8L)).thenReturn(Optional.of(trade));

        assertThat(tradeService.getById(8L)).isSameAs(trade);
    }

    @Test
    void findByOrderId_delegates() {
        when(tradeRepository.findByOrderId(1L)).thenReturn(List.of(new TradeEntity()));

        assertThat(tradeService.findByOrderId(1L)).hasSize(1);
    }

    @Test
    void list_withOrderId_usesFilteredQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(tradeRepository.findByOrderId(1L, pageable)).thenReturn(new PageImpl<>(List.of()));

        tradeService.list(1L, pageable);

        verify(tradeRepository).findByOrderId(1L, pageable);
    }

    @Test
    void list_withoutOrderId_usesFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        when(tradeRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        tradeService.list(null, pageable);

        verify(tradeRepository).findAll(pageable);
    }
}
