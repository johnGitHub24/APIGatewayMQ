package com.trading.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class GatewayTestFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GatewayTestFixtures() {}

    public static String loadGatewayOrderJson(String caseId) {
        Path path = Paths.get("docs", "test-data", "gateway", caseId + ".json");
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load fixture: " + caseId, e);
        }
    }
}
