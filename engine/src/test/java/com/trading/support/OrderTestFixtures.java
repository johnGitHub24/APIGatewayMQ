package com.trading.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.dto.CreateOrderRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class OrderTestFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private OrderTestFixtures() {}

    public static CreateOrderRequest load(String caseId) {
        Path path = Paths.get("docs", "test-data", "placeOrder", caseId + ".json");
        try {
            String json = Files.readString(path);
            return MAPPER.readValue(json, CreateOrderRequest.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load fixture: " + caseId, e);
        }
    }
}
