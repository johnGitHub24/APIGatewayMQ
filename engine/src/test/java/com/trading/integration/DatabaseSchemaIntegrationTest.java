package com.trading.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class DatabaseSchemaIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void DB_001_allFourTablesExist() {
        assertThat(tableExists("orders")).isTrue();
        assertThat(tableExists("trades")).isTrue();
        assertThat(tableExists("positions")).isTrue();
        assertThat(tableExists("order_events")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Object result = entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = :name")
                .setParameter("name", tableName.toUpperCase())
                .getSingleResult();
        return ((Number) result).longValue() > 0;
    }
}
