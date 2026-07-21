package com.trading.application;

import com.trading.dto.PnLResponse;
import com.trading.infrastructure.entity.PositionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 【職責】損益彙總：讀取全部持倉，組裝總未實現與分項明細。
 * 【技巧】Stream {@code map}/{@code reduce} 加總；組 {@link PnLResponse}。
 * 【概念】API 要的是「帳戶視角摘要」，不是單一持倉列——在此聚合一次。
 * 【邊界】不重算公式（用持倉上已存的 unrealizedPnl）；不寫快照。
 */
@Service
public class PnLService {

    private final PositionService positionService;

    /** 建構子注入 PnL 彙總依賴。 */
    public PnLService(PositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * 【職責】回傳總未實現 PnL 與各商品明細。
     * 【技巧】{@code @Transactional(readOnly = true)}；{@code reduce(ZERO, BigDecimal::add)}。
     * 【概念】{@code asOf} 標示計算時點，避免報表誤以為是歷史結算值。
     * @return 損益摘要回應
     */
    @Transactional(readOnly = true)
    public PnLResponse getSummary() {
        List<PositionEntity> positions = positionService.findAll();
        BigDecimal total = positions.stream()
                .map(PositionEntity::getUnrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PnLResponse response = new PnLResponse();
        response.setTotalUnrealizedPnl(total);
        response.setAsOf(OffsetDateTime.now());
        response.setPositions(positions.stream().map(p -> {
            PnLResponse.PositionPnlItem item = new PnLResponse.PositionPnlItem();
            item.setSymbol(p.getSymbol());
            item.setUnrealizedPnl(p.getUnrealizedPnl());
            return item;
        }).toList());
        return response;
    }
}
