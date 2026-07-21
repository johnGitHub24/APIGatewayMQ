package com.trading.dto;

import com.trading.domain.OrderEventType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 【職責】訂單事件時間軸回應：依時間列出生命週期事件，供追蹤與除錯。
 * 【技巧】外層 DTO + 巢狀 {@link EventItem}；Lombok {@code @Data}。
 * 【概念】查單只看 status 不夠；事件軸能還原「風控何時拒、何時部分成交」等過程。
 * 【邊界】不負責寫入事件；寫入在下單／成交流程，此處僅查詢投影。
 */
@Data
public class OrderEventsResponse {

    /** 所屬訂單主鍵。 */
    private Long orderId;
    /** 事件列表（通常由早到晚）。 */
    private List<EventItem> events;

    /**
     * 【職責】單一事件項目：類型、風控碼、拒絕原因與載荷。
     * 【技巧】靜態巢狀類，與外層共用序列化邊界。
     * 【概念】把「一筆事件」從 Entity 投影成 API 友善結構，避免直接暴露 JPA 實體。
     */
    @Data
    public static class EventItem {
        /** 事件類型。 */
        private OrderEventType event;
        /** 風控相關事件時的規則代碼。 */
        private String riskRuleCode;
        /** 拒絕類事件的原因說明。 */
        private String rejectReason;
        /** 事件原始 JSON 載荷（稽核／除錯）。 */
        private String payloadJson;
        /** 事件發生時間。 */
        private OffsetDateTime createdAt;
    }
}
