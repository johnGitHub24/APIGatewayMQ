package com.trading.application;

import com.trading.domain.OrderEventType;
import com.trading.infrastructure.entity.OrderEventEntity;
import com.trading.infrastructure.repository.OrderEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 【職責】訂單事件審計：把生命週期節點寫入 {@code order_events}。
 * 【技巧】{@code @Transactional}；組裝 {@link OrderEventEntity} 後 {@code save}。
 * 【概念】事件流是「發生了什麼」的時間軸，與訂單當前狀態互補——狀態是結果，事件是過程。
 * 【邊界】不改訂單狀態；不負責查詢時間軸組裝（Controller／Mapper 可讀 Repository）。
 */
@Service
public class OrderEventService {

    private final OrderEventRepository orderEventRepository;

    /** 建構子注入訂單事件 Repository。 */
    public OrderEventService(OrderEventRepository orderEventRepository) {
        this.orderEventRepository = orderEventRepository;
    }

    /**
     * 【職責】寫入一筆訂單事件（規則碼、拒絕原因、JSON payload 可選）。
     * 【技巧】單一 {@code log} 方法統一入口，避免各處自行 new Entity。
     * 【概念】payloadJson 放結構化細節（如縮量前後數量），方便事後分析。
     * @param orderId      訂單主鍵
     * @param event        事件類型
     * @param ruleCode     相關風控規則碼（可 null）
     * @param rejectReason 拒絕／說明文字（可 null）
     * @param payloadJson  附加 JSON（可 null）
     */
    @Transactional
    public void log(Long orderId, OrderEventType event, String ruleCode, String rejectReason, String payloadJson) {
        OrderEventEntity entity = new OrderEventEntity();
        entity.setOrderId(orderId);
        entity.setEvent(event);
        entity.setRiskRuleCode(ruleCode);
        entity.setRejectReason(rejectReason);
        entity.setPayloadJson(payloadJson);
        orderEventRepository.save(entity);
    }
}
