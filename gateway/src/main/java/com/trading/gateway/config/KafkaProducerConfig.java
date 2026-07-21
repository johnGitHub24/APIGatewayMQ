package com.trading.gateway.config;

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
 * 【職責】註冊 Gateway 作為 Kafka Producer 所需的 {@link ObjectMapper}、{@link ProducerFactory}、{@link KafkaTemplate}。
 * 【技巧】{@code @Configuration} + {@code @Bean}；{@code acks=all} 與 {@code enable.idempotence=true} 提高送件可靠度；
 *         value 用 {@link JsonSerializer}，並關閉 type headers（{@code setAddTypeInfo(false)}）以免跨服務反序列化綁死類名。
 * 【概念】非同步下單路徑：HTTP → Producer → {@code order.commands} → Engine Consumer。
 *         與 WebClient 同步代理互補——寫入走 MQ 削峰，讀取／取消仍可走 HTTP。
 * 【邊界】只組態 Producer；不發送訊息（由 {@link com.trading.gateway.service.OrderCommandProducer} 負責）。
 *         Broker 位址來自 {@code spring.kafka.bootstrap-servers}，正式環境請用環境變數覆寫。
 */
@Configuration
public class KafkaProducerConfig {

    /** Kafka broker 位址，預設 {@code localhost:9092}。 */
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * 【職責】提供可序列化 {@link java.time.Instant} 等 Java Time 型別的 {@link ObjectMapper}。
     * 【技巧】註冊 {@link JavaTimeModule}，否則 Instant 預設會變成 timestamp 數字或序列化失敗。
     * 【概念】Jackson 預設不懂 JSR-310；模組是「教 ObjectMapper 認識新型別」的擴充點。
     *
     * @return 已註冊 JavaTimeModule 的 mapper，供 JsonSerializer 使用
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * 【職責】建立 Kafka Producer 工廠：broker、字串 key、JSON value、acks／冪等。
     * 【技巧】{@link DefaultKafkaProducerFactory} 搭配自訂 {@link JsonSerializer}；key 用 {@link StringSerializer}。
     * 【概念】ProducerFactory 是「如何建立 Producer」的藍圖；業務碼通常只碰 KafkaTemplate，不必直接管連線細節。
     *
     * @param objectMapper 用於 value 的 JSON 序列化
     * @return 可注入容器的 {@link ProducerFactory}
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory(ObjectMapper objectMapper) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        JsonSerializer<Object> jsonSerializer = new JsonSerializer<>(objectMapper);
        jsonSerializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), jsonSerializer);
    }

    /**
     * 【職責】建立 {@link KafkaTemplate}，供 {@link com.trading.gateway.service.OrderCommandProducer} 非同步送件。
     * 【技巧】Spring Kafka 的高階 API：{@code send(topic, key, value)} 回傳 {@link java.util.concurrent.CompletableFuture}。
     * 【概念】Template 模式把「重複的 Producer 樣板碼」收進框架，業務只關心 topic／key／payload。
     *
     * @param producerFactory 由 {@link #producerFactory} 建立
     * @return 可非同步送件的 Kafka 範本
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
