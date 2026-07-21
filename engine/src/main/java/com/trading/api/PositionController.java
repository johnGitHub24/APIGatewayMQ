package com.trading.api;

import com.trading.application.PositionService;
import com.trading.dto.PagedResponse;
import com.trading.dto.PositionResponse;
import com.trading.infrastructure.mapper.OrderMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 【職責】持倉查詢 API：列出全部或依標的查單一淨部位。
 * 【技巧】{@code @RestController} + Mapper 轉 DTO；列表端點在記憶體做簡易分頁（{@code subList}）。
 * 【概念】持倉＝帳戶對某標的的淨部位（數量、均價、未實現損益）。買加倉、賣減倉。
 * 【邊界】不更新持倉（成交後由 {@link com.trading.application.PositionService#updateAfterFill}）；
 *         本 Controller 僅讀取與組裝回應。
 */
@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {

    private final PositionService positionService;
    private final OrderMapper orderMapper;

    /** 建構子注入持倉查詢依賴。 */
    public PositionController(PositionService positionService, OrderMapper orderMapper) {
        this.positionService = positionService;
        this.orderMapper = orderMapper;
    }

    /**
     * 【職責】分頁列出所有持倉。
     * 【技巧】先取全量再 {@code subList}；{@code Math.min/max} 保護 page/size。
     * 【概念】教學環境持倉筆數通常很少，記憶體分頁可接受；正式系統應改 DB 分頁。
     * @param page 頁碼（從 0）
     * @param size 每頁筆數（預設 20，上限 100）
     * @return 分頁持倉回應
     */
    @GetMapping
    public PagedResponse<PositionResponse> listPositions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PositionResponse> all = positionService.findAll().stream()
                .map(orderMapper::toPositionResponse)
                .toList();
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min(page * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        PagedResponse<PositionResponse> response = new PagedResponse<>();
        response.setData(all.subList(from, to));
        response.setMeta(PagedResponse.PageMeta.of(page, safeSize, all.size()));
        return response;
    }

    /**
     * 【職責】查詢單一標的的持倉明細。
     * 【技巧】路徑變數 {@code symbol}；找不到由 Service 拋 404 對應例外。
     * 【概念】用標的當自然鍵查倉，比用內部 id 更符合交易員習慣。
     * @param symbol 標的代碼（如 AAPL）
     * @return 持倉回應
     */
    @GetMapping("/{symbol}")
    public PositionResponse getPosition(@PathVariable String symbol) {
        return orderMapper.toPositionResponse(positionService.findBySymbol(symbol));
    }
}
