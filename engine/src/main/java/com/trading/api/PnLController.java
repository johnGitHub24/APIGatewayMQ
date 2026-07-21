package com.trading.api;

import com.trading.application.PnLService;
import com.trading.dto.PnLResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【職責】損益（PnL）查詢 API：彙總未實現損益並分項列出。
 * 【技巧】薄 {@code @RestController}；單一 {@code GET /api/v1/pnl} 轉交 {@link PnLService#getSummary()}。
 * 【概念】未實現損益＝持倉尚未平倉時的紙上盈虧；與「已實現」不同。此端點只讀、不改倉。
 * 【邊界】不計算公式細節（在 {@link com.trading.application.PnLCalculator}／持倉服務）；不寫快照。
 */
@RestController
@RequestMapping("/api/v1/pnl")
public class PnLController {

    private final PnLService pnLService;

    /** 建構子注入 {@link PnLService}。 */
    public PnLController(PnLService pnLService) {
        this.pnLService = pnLService;
    }

    /**
     * 【職責】取得當前損益摘要（總未實現 + 各標的明細）。
     * 【技巧】直接回傳 Service 組好的 {@link PnLResponse}。
     * 【概念】Dashboard 常用「一眼看總盈虧」；明細方便鑽取到單一標的。
     * @return 損益摘要回應
     */
    @GetMapping
    public PnLResponse getPnL() {
        return pnLService.getSummary();
    }
}
