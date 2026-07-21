package com.trading.infrastructure.mapper;

import com.trading.dto.OrderEventsResponse;
import com.trading.dto.OrderResponse;
import com.trading.dto.PositionResponse;
import com.trading.dto.TradeDetailResponse;
import com.trading.dto.TradeResponse;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.entity.OrderEventEntity;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.entity.TradeEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【職責】將訂單相關 JPA 實體投影為 API 回應 DTO，避免 Controller 直接暴露資料庫結構。
 * 【技巧】Spring {@code @Component} 手動映射（非 MapStruct）；公開方法依目標 DTO 分流。
 * 【概念】Entity 面向持久化，DTO 面向契約；Mapper 是兩者之間的防腐層，欄位更名／裁剪都集中在此。
 * 【邊界】不含查詢與商業規則；呼叫端先取 Entity 再交給本類轉換。
 */
@Component
public class OrderMapper {

    /**
     * 【職責】訂單實體 → {@link OrderResponse}（不含成交列表，由呼叫端另填）。
     * 【技巧】逐欄 set；id 對外改名為 orderId。
     * 【概念】對外不暴露 JPA 實體，才能自由調整表結構而不破壞 API。
     */
    public OrderResponse toResponse(OrderEntity entity) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(entity.getId());
        response.setClientOrderId(entity.getClientOrderId());
        response.setSymbol(entity.getSymbol());
        response.setSide(entity.getSide());
        response.setQuantity(entity.getQuantity());
        response.setPrice(entity.getPrice());
        response.setFilledQuantity(entity.getFilledQuantity());
        response.setStatus(entity.getStatus());
        response.setRejectReason(entity.getRejectReason());
        response.setRiskRuleCode(entity.getRiskRuleCode());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    /**
     * 【職責】成交實體 → 精簡 {@link TradeResponse}（嵌訂單用）。
     * 【技巧】只映射價格／數量／時間三欄。
     * 【概念】嵌套場景不需要 tradeId，減少 payload。
     */
    public TradeResponse toTradeResponse(TradeEntity entity) {
        TradeResponse response = new TradeResponse();
        response.setExecutedPrice(entity.getExecutedPrice());
        response.setExecutedQty(entity.getExecutedQty());
        response.setExecutedAt(entity.getExecutedAt());
        return response;
    }

    /**
     * 【職責】成交實體 → 完整 {@link TradeDetailResponse}（列表查詢用）。
     * 【技巧】補上 tradeId／orderId。
     * 【概念】獨立成交 API 需要可追蹤識別碼，與精簡摘要分流。
     */
    public TradeDetailResponse toTradeDetail(TradeEntity entity) {
        TradeDetailResponse response = new TradeDetailResponse();
        response.setTradeId(entity.getId());
        response.setOrderId(entity.getOrderId());
        response.setExecutedPrice(entity.getExecutedPrice());
        response.setExecutedQty(entity.getExecutedQty());
        response.setExecutedAt(entity.getExecutedAt());
        return response;
    }

    /**
     * 【職責】持倉實體 → {@link PositionResponse}。
     * 【技巧】逐欄投影，不含內部 id。
     * 【概念】對外以 symbol 為持倉識別，符合「每標的一筆」語意。
     */
    public PositionResponse toPositionResponse(PositionEntity entity) {
        PositionResponse response = new PositionResponse();
        response.setSymbol(entity.getSymbol());
        response.setQuantity(entity.getQuantity());
        response.setAvgPrice(entity.getAvgPrice());
        response.setUnrealizedPnl(entity.getUnrealizedPnl());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    /**
     * 【職責】訂單事件列表 → {@link OrderEventsResponse} 時間軸包裝。
     * 【技巧】Stream map 轉 {@link OrderEventsResponse.EventItem}。
     * 【概念】一次組好 orderId + events，前端不必自己拼裝。
     */
    public OrderEventsResponse toEventsResponse(Long orderId, List<OrderEventEntity> events) {
        OrderEventsResponse response = new OrderEventsResponse();
        response.setOrderId(orderId);
        response.setEvents(events.stream().map(this::toEventItem).toList());
        return response;
    }

    /** 單筆事件實體 → EventItem。 */
    private OrderEventsResponse.EventItem toEventItem(OrderEventEntity entity) {
        OrderEventsResponse.EventItem item = new OrderEventsResponse.EventItem();
        item.setEvent(entity.getEvent());
        item.setRiskRuleCode(entity.getRiskRuleCode());
        item.setRejectReason(entity.getRejectReason());
        item.setPayloadJson(entity.getPayloadJson());
        item.setCreatedAt(entity.getCreatedAt());
        return item;
    }
}
