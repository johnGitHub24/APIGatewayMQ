package com.trading.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.common.OrderCommandMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * 【職責】Kafka 消費者 Spring 設定：下單指令的反序列化、並行度與錯誤重試。
 * 【技巧】{@code @Configuration} + {@code @Bean}；{@link JsonDeserializer}；{@link ConcurrentKafkaListenerContainerFactory}；
 *         {@link DefaultErrorHandler} + {@link FixedBackOff}。
 * 【概念】Listener 要能把 JSON 還原成 {@link OrderCommandMessage}；concurrency 決定同 group 內並行消費數。
 * 【邊界】不處理業務；業務失敗分流在 {@link com.trading.engine.messaging.OrderCommandConsumer}。
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:trading-engine}")
    private String groupId;

    /**
     * 【概念】必須手動套到自訂 {@link ConcurrentKafkaListenerContainerFactory}：
     * Spring Boot 的 {@code spring.kafka.listener.auto-startup} 只會設定預設
     * {@code kafkaListenerContainerFactory}，本專案 Listener 用的是自訂 bean，不套就會一直連 Kafka。
     */
    @Value("${spring.kafka.listener.auto-startup:true}")
    private boolean listenerAutoStartup;

    /**
     * 【職責】建立下單指令的 {@link ConsumerFactory}（連線、反序列化、offset 策略）。
     * 【技巧】{@link DefaultKafkaConsumerFactory}；信任套件 {@code com.trading.common}；{@code AUTO_OFFSET_RESET=earliest}。
     * 【概念】group-id 決定「同一消費者群組只處理一次」；earliest 方便本機重跑時從頭讀。
     * @param kafkaObjectMapper 與 Producer 共用的 JSON ObjectMapper
     * @return 消費者工廠
     */
    @Bean
    ConsumerFactory<String, OrderCommandMessage> orderCommandConsumerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        JsonDeserializer<OrderCommandMessage> deserializer = new JsonDeserializer<>(OrderCommandMessage.class, kafkaObjectMapper);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("com.trading.common");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    /**
     * 【職責】建立 {@code @KafkaListener} 使用的容器工廠（並行度與框架層重試）。
     * 【技巧】{@code setConcurrency(3)}；{@code setAutoStartup} 綁定 {@code spring.kafka.listener.auto-startup}；
     *         {@link DefaultErrorHandler} + {@link FixedBackOff}(1s, 3 次)。
     * 【概念】此處重試是「反序列化／listener 拋錯」的短重試；持久化 DLQ 是另一層（JOB-C）。
     *         自訂 factory 不會自動吃 Boot 的 auto-startup，必須 {@code setAutoStartup}，否則 local 也會連 Kafka。
     * @param orderCommandConsumerFactory 消費者工廠
     * @return Listener 容器工廠（bean 名供 {@code containerFactory} 引用）
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCommandMessage> orderCommandKafkaListenerContainerFactory(
            ConsumerFactory<String, OrderCommandMessage> orderCommandConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCommandMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCommandConsumerFactory);
        factory.setConcurrency(3);
        // local profile 設 false → 本機無 Kafka 也不會刷 Bootstrap broker disconnected
        factory.setAutoStartup(listenerAutoStartup);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3)));
        return factory;
    }
}
