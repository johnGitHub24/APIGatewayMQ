package com.trading.application;

import com.trading.domain.OrderSide;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 【職責】持倉管理：查詢、曝險加總、成交後更新數量／均價／未實現損益。
 * 【技巧】{@code orElseGet} 建新倉；買加賣減；均價用加權成本；委派 {@link PnLCalculator}。
 * 【概念】持倉是成交的累積結果；風控用「當前量／總曝險」當輸入，PnL API 再讀同一份資料。
 * 【邊界】不寫訂單／成交列；找不到 symbol 的查詢拋 {@link ResourceNotFoundException}。
 */
@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final PnLCalculator pnlCalculator;

    /** 建構子注入持倉 Repository 與 PnL 計算器。 */
    public PositionService(PositionRepository positionRepository, PnLCalculator pnlCalculator) {
        this.positionRepository = positionRepository;
        this.pnlCalculator = pnlCalculator;
    }

    /**
     * 【職責】查詢所有持倉。
     * 【技巧】{@code readOnly} 交易。
     * 【概念】帳戶總覽／PnL 彙總的資料來源。
     */
    @Transactional(readOnly = true)
    public List<PositionEntity> findAll() {
        return positionRepository.findAll();
    }

    /**
     * 【職責】依商品代碼查詢持倉；不存在則拋例外。
     * 【技巧】{@code orElseThrow}。
     * 【概念】REST「查單一標的」需要明確 404，而不是回空物件。
     */
    @Transactional(readOnly = true)
    public PositionEntity findBySymbol(String symbol) {
        return positionRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("POSITION_NOT_FOUND",
                        "Position not found for symbol " + symbol));
    }

    /**
     * 【職責】取得指定商品當前持倉數量；無持倉回零。
     * 【技巧】{@code map(...).orElse(ZERO)}——風控投影用，不應因無倉而失敗。
     * 【概念】「沒倉＝數量 0」對下單前檢查更自然。
     */
    @Transactional(readOnly = true)
    public BigDecimal getCurrentQuantity(String symbol) {
        return positionRepository.findBySymbol(symbol)
                .map(PositionEntity::getQuantity)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 【職責】計算全帳戶總曝險（|數量| × 標記價，無標記則用均價）。
     * 【技巧】Stream 加總絕對曝險。
     * 【概念】曝險衡量「市場反向時可能受傷的名目規模」，供 R002 使用。
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalExposure() {
        return positionRepository.findAll().stream()
                .map(p -> p.getQuantity().abs().multiply(p.getMarkPrice() != null ? p.getMarkPrice() : p.getAvgPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 【職責】成交後更新持倉數量、均價與未實現損益。
     * 【技巧】無倉則新建；BUY 加量／SELL 減量；買入重算加權均價。
     * 【概念】賣出通常保留原均價（剩餘倉成本不變）；買入才混合新成本。
     * @param symbol 標的
     * @param side   買賣方向
     * @param qty    成交量
     * @param price  成交價（同時當 mark）
     * @return 更新後持倉
     */
    @Transactional
    public PositionEntity updateAfterFill(String symbol, OrderSide side, BigDecimal qty, BigDecimal price) {
        PositionEntity position = positionRepository.findBySymbol(symbol)
                .orElseGet(() -> {
                    PositionEntity p = new PositionEntity();
                    p.setSymbol(symbol);
                    return p;
                });

        BigDecimal oldQty = position.getQuantity();
        BigDecimal newQty = side == OrderSide.BUY ? oldQty.add(qty) : oldQty.subtract(qty);
        BigDecimal newAvg = calculateAvgPrice(position.getAvgPrice(), oldQty, side, qty, price);

        position.setQuantity(newQty);
        position.setAvgPrice(newAvg);
        position.setMarkPrice(price);
        position.setUnrealizedPnl(pnlCalculator.calculateUnrealized(newQty, newAvg, price));

        return positionRepository.save(position);
    }

    private BigDecimal calculateAvgPrice(BigDecimal oldAvg, BigDecimal oldQty, OrderSide side,
                                       BigDecimal fillQty, BigDecimal fillPrice) {
        if (side == OrderSide.SELL) {
            return oldQty.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : oldAvg;
        }
        BigDecimal totalCost = oldAvg.multiply(oldQty).add(fillPrice.multiply(fillQty));
        BigDecimal newQty = oldQty.add(fillQty);
        if (newQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.divide(newQty, 8, RoundingMode.HALF_UP);
    }
}
