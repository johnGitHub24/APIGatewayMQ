package com.trading.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * 【職責】Kafka 生產者 Spring 設定：ObjectMapper、序列化與 {@link KafkaTemplate}。
 * 【技巧】{@link JavaTimeModule}；{@link JsonSerializer}（關閉 type info）；{@link DefaultKafkaProducerFactory}。
 * 【概念】Engine 也可發事件／回覆；與 Consumer 共用同一套 JSON 時間格式，避免反序列化失敗。
 * 【邊界】不決定 topic／業務 payload；發送由呼叫端注入 {@link KafkaTemplate} 完成。
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * 【職責】建立 Kafka 專用 {@link ObjectMapper}，支援 Java 8 時間型別。
     * 【技巧】註冊 {@link JavaTimeModule}。
     * 【概念】OffsetDateTime 等若不註冊模組，Jackson 預設可能序列化失敗或格式不一致。
     * @return Kafka 用 ObjectMapper Bean
     */
    @Bean
    ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * 【職責】建立 Producer 工廠（broker、key／value 序列化器）。
     * 【技巧】{@code setAddTypeInfo(false)} 避免在 JSON 塞 {@code @class}，跨服務更乾淨。
     * 【概念】Key 用 String、Value 用 JSON，是最常見的業務訊息組合。
     * @param kafkaObjectMapper 共用 ObjectMapper
     * @return Producer 工廠
     */
    @Bean
    ProducerFactory<String, Object> producerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        JsonSerializer<Object> jsonSerializer = new JsonSerializer<>(kafkaObjectMapper);
        jsonSerializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), jsonSerializer);
    }

    /**
     * 【職責】提供可注入的 {@link KafkaTemplate} 供業務層發送訊息。
     * 【技巧】包裝 {@link ProducerFactory}。
     * 【概念】業務程式碼通常只依賴 Template，不必碰底層 Producer API。
     * @param producerFactory Producer 工廠
     * @return KafkaTemplate Bean
     */
    @Bean
    KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
