package com.trading.application;

import com.trading.config.JobProperties;
import com.trading.domain.OrderEventType;
import com.trading.domain.OrderStatus;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 【職責】JOB-A：將逾時仍為 NEW／PARTIALLY_FILLED 的訂單自動取消。
 * 【技巧】依 timeout 算 cutoff；批次查詢後改 CANCELLED 並寫事件。
 * 【概念】掛太久的單會佔用風險與注意力；逾時取消是營運自動化，不是使用者主動撤單。
 * 【邊界】不處理已 FILLED／REJECTED；批次大小與秒數來自 {@link JobProperties}。
 */
@Service
@Slf4j
public class StaleOrderCancellationService {

    private static final List<OrderStatus> CANCELLABLE =
            List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED);

    private final OrderRepository orderRepository;
    private final OrderEventService orderEventService;
    private final JobProperties jobProperties;

    /** 建構子注入逾時取消所需服務。 */
    public StaleOrderCancellationService(OrderRepository orderRepository,
                                         OrderEventService orderEventService,
                                         JobProperties jobProperties) {
        this.orderRepository = orderRepository;
        this.orderEventService = orderEventService;
        this.jobProperties = jobProperties;
    }

    /**
     * 【職責】掃描並取消逾時訂單。
     * 【技巧】{@code findByStatusInAndCreatedAtBefore} + {@link PageRequest} 限批；寫 CANCELLED 事件。
     * 【概念】只動「可取消」狀態，避免誤傷已完成單。
     * @return 本次取消的訂單筆數
     */
    @Transactional
    public int cancelStaleOrders() {
        JobProperties.StaleOrder config = jobProperties.getStaleOrder();
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(config.getTimeoutSeconds());

        List<OrderEntity> staleOrders = orderRepository.findByStatusInAndCreatedAtBefore(
                CANCELLABLE, cutoff, PageRequest.of(0, config.getBatchSize()));

        for (OrderEntity order : staleOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setRejectReason("Auto-cancelled: order timed out after " + config.getTimeoutSeconds() + "s");
            orderRepository.save(order);
            orderEventService.log(order.getId(), OrderEventType.CANCELLED, null,
                    "Stale order timeout", null);
        }

        if (!staleOrders.isEmpty()) {
            log.info("JOB-A cancelled {} stale orders (cutoff={})", staleOrders.size(), cutoff);
        }
        return staleOrders.size();
    }
}
