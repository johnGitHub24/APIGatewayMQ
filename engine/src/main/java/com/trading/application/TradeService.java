package com.trading.application;

import com.trading.application.ResourceNotFoundException;
import com.trading.infrastructure.entity.TradeEntity;
import com.trading.infrastructure.repository.TradeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 【職責】成交紀錄服務：寫入與查詢實際成交明細。
 * 【技巧】{@code @Transactional}；與 {@link OrderService} 分工（訂單狀態 vs 成交列）。
 * 【概念】一筆訂單可有多筆 trade（部分成交）；查單時再聚合。
 * 【邊界】不更新持倉／訂單狀態（由 {@link TradingService} 編排）。
 */
@Service
public class TradeService {

    private final TradeRepository tradeRepository;

    /** 建構子注入成交 Repository。 */
    public TradeService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    /**
     * 【職責】寫入一筆成交紀錄並回傳持久化實體。
     * 【技巧】設定 orderId／價／量後 {@code save}。
     * 【概念】成交列是不可變事實；之後對帳以它為準。
     */
    @Transactional
    public TradeEntity record(Long orderId, BigDecimal executedPrice, BigDecimal executedQty) {
        TradeEntity trade = new TradeEntity();
        trade.setOrderId(orderId);
        trade.setExecutedPrice(executedPrice);
        trade.setExecutedQty(executedQty);
        return tradeRepository.save(trade);
    }

    /**
     * 【職責】依主鍵查詢成交；不存在則拋例外。
     * 【技巧】{@code orElseThrow} → {@link ResourceNotFoundException}。
     * 【概念】單筆查詢 API 的資料來源。
     */
    @Transactional(readOnly = true)
    public TradeEntity getById(Long tradeId) {
        return tradeRepository.findById(tradeId)
                .orElseThrow(() -> new ResourceNotFoundException("TRADE_NOT_FOUND",
                        "Trade with id " + tradeId + " not found"));
    }

    /**
     * 【職責】查詢指定訂單的所有成交紀錄。
     * 【技巧】{@code findByOrderId}。
     * 【概念】訂單詳情頁常要附帶 trades 列表。
     */
    @Transactional(readOnly = true)
    public List<TradeEntity> findByOrderId(Long orderId) {
        return tradeRepository.findByOrderId(orderId);
    }

    /**
     * 【職責】分頁查詢成交，可依訂單 ID 篩選。
     * 【技巧】有 orderId 走專用查詢，否則 {@code findAll(pageable)}。
     * 【概念】列表 API 永遠帶分頁，避免全表。
     */
    @Transactional(readOnly = true)
    public Page<TradeEntity> list(Long orderId, Pageable pageable) {
        if (orderId != null) {
            return tradeRepository.findByOrderId(orderId, pageable);
        }
        return tradeRepository.findAll(pageable);
    }
}
