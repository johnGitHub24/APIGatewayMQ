package com.trading.api;

import com.trading.application.TradeService;
import com.trading.dto.PagedResponse;
import com.trading.dto.TradeDetailResponse;
import com.trading.infrastructure.entity.TradeEntity;
import com.trading.infrastructure.mapper.OrderMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 【職責】成交（Trade）查詢 API：單筆明細與分頁列表。
 * 【技巧】薄 Controller；{@link PageRequest} + Mapper；可選 {@code orderId} 過濾。
 * 【概念】成交是訂單被撮合後的實際買賣紀錄；一筆訂單可對應多筆部分成交。
 * 【邊界】不建立成交（由 {@link com.trading.application.TradingService} 在成交流程寫入）；只讀查詢。
 */
@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private final TradeService tradeService;
    private final OrderMapper orderMapper;

    /** 建構子注入成交查詢依賴。 */
    public TradeController(TradeService tradeService, OrderMapper orderMapper) {
        this.tradeService = tradeService;
        this.orderMapper = orderMapper;
    }

    /**
     * 【職責】查詢單筆成交明細。
     * 【技巧】{@code getById} + {@code toTradeDetail}。
     * 【概念】明細含所屬訂單、成交價、數量與時間，供對帳與稽核。
     * @param tradeId 成交主鍵
     * @return 成交明細回應
     */
    @GetMapping("/{tradeId}")
    public TradeDetailResponse getTrade(@PathVariable Long tradeId) {
        return orderMapper.toTradeDetail(tradeService.getById(tradeId));
    }

    /**
     * 【職責】分頁列出成交紀錄，可依訂單過濾。
     * 【技巧】夾住 size；{@link Page#map} 轉 DTO；組 {@link PagedResponse}。
     * 【概念】「這張單成交了幾次？」用 orderId 過濾即可，不必掃全表。
     * @param orderId 可選訂單主鍵
     * @param page    頁碼（從 0）
     * @param size    每頁筆數（預設 20，上限 100）
     * @return 分頁成交回應
     */
    @GetMapping
    public PagedResponse<TradeDetailResponse> listTrades(
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<TradeDetailResponse> result = tradeService
                .list(orderId, PageRequest.of(page, safeSize))
                .map(orderMapper::toTradeDetail);
        PagedResponse<TradeDetailResponse> response = new PagedResponse<>();
        response.setData(result.getContent());
        response.setMeta(PagedResponse.PageMeta.of(page, safeSize, result.getTotalElements()));
        return response;
    }
}
