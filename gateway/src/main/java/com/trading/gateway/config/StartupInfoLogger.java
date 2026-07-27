package com.trading.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 【職責】應用就緒後於 Console 印出常用 URL（health／Swagger／下單入口），方便 IntelliJ 本機啟動。
 * 【技巧】聽 {@link ApplicationReadyEvent}；開關全來自 {@code startup.info.*}（見 application.yml）。
 * 【概念】Gateway 無 JPA／H2；本機仍需 Kafka（寫命令）與 Redis（限流，故障 fail-open）。
 * 【邊界】不負責啟動 Engine／Docker；不驗證下游是否可連。
 */
@Component
public class StartupInfoLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        if (!env.getProperty("startup.info.enabled", Boolean.class, true)) {
            return;
        }

        String project = env.getProperty("startup.info.project-name", "APIGatewayMQ Gateway");
        String port = env.getProperty("server.port", "8080");
        String base = "http://localhost:" + port;
        boolean apiDocs = env.getProperty("startup.info.api-docs", Boolean.class, true);

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║  %-70s║%n", project + " 已啟動 — 使用連結");
        System.out.println("╠════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ 【後端 API / 工具】                                                      ║");
        System.out.printf("║   健康檢查     %s%n", base + "/actuator/health");
        System.out.printf("║   應用資訊     %s%n", base + "/actuator/info");
        if (apiDocs) {
            System.out.printf("║   Swagger UI   %s%n", base + "/swagger-ui.html");
            System.out.printf("║   OpenAPI JSON %s%n", base + "/v3/api-docs");
        }
        System.out.println("╠════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ 【依賴提示】                                                              ║");
        System.out.printf("║   Kafka        %s%n",
                env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        System.out.printf("║   Redis        %s:%s%n",
                env.getProperty("spring.data.redis.host", "localhost"),
                env.getProperty("spring.data.redis.port", "6379"));
        System.out.printf("║   Engine URIs  %s%n",
                env.getProperty("gateway.engine-uris[0]", "http://localhost:8081"));
        System.out.println("║   圖解入口     docs/codeGraphic.html                                     ║");
        System.out.println("║   初學者說明   docs/初學者學習說明書.md                                   ║");
        for (String path : extraPaths(env)) {
            System.out.printf("║   API 範例     %s%n", base + path);
        }
        System.out.println("╚════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        log.info("{} ready — {}", project, base + "/actuator/health");
    }

    private static List<String> extraPaths(Environment env) {
        String first = env.getProperty("startup.info.extra-paths[0]");
        if (first != null && !first.isBlank()) {
            List<String> paths = new ArrayList<>();
            for (int i = 0; ; i++) {
                String p = env.getProperty("startup.info.extra-paths[" + i + "]");
                if (p == null || p.isBlank()) {
                    break;
                }
                paths.add(p.startsWith("/") ? p : "/" + p);
            }
            return paths;
        }
        String raw = env.getProperty("startup.info.extra-paths");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("/") ? s : "/" + s)
                .toList();
    }
}
