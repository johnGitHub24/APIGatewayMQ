package com.trading.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.common.OrderCommandMessage;
import com.trading.dto.CreateOrderRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class GatewayMqTestFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private GatewayMqTestFixtures() {}

    public static CreateOrderRequest loadOrder(String caseId) {
        return loadJson("placeOrder", caseId, CreateOrderRequest.class);
    }

    public static OrderCommandMessage loadCommand(String caseId) {
        return loadJson("gateway", caseId, OrderCommandMessage.class);
    }

    public static Map<String, Object> loadGatewayJson(String caseId) {
        return loadJson("gateway", caseId, Map.class);
    }

    private static <T> T loadJson(String folder, String caseId, Class<T> type) {
        Path path = Paths.get("docs", "test-data", folder, caseId + ".json");
        try {
            String json = Files.readString(path);
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load fixture: " + caseId, e);
        }
    }
}
