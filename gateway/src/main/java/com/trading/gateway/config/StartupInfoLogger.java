package com.trading.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 【職責】應用就緒後於 Console 印出全棧常用 URL（Gateway／Engine／Prometheus／Grafana／Swagger）。
 * 【技巧】聽 {@link ApplicationReadyEvent}；開關全來自 {@code startup.info.*}；以 UTF-8 {@link PrintStream} 寫出；需 JVM {@code -Dstdout.encoding=UTF-8} 與 IDE Console=UTF-8（見 EOS knowledge）。
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

        PrintStream out = utf8Out();
        out.println();
        out.println("╔════════════════════════════════════════════════════════════════════════╗");
        out.printf("║  %-70s║%n", project + " 已啟動 — 使用連結");
        out.println("╠════════════════════════════════════════════════════════════════════════╣");
        printStackLinks(out, env);
        out.println("╠════════════════════════════════════════════════════════════════════════╣");
        out.println("║ 【本服務】                                                                ║");
        out.printf("║   應用資訊     %s%n", base + "/actuator/info");
        if (apiDocs) {
            out.printf("║   OpenAPI JSON %s%n", base + "/v3/api-docs");
        }
        out.println("╠════════════════════════════════════════════════════════════════════════╣");
        out.println("║ 【依賴提示】                                                              ║");
        out.printf("║   Kafka        %s%n",
                env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        out.printf("║   Redis        %s:%s%n",
                env.getProperty("spring.data.redis.host", "localhost"),
                env.getProperty("spring.data.redis.port", "6379"));
        out.printf("║   Engine URIs  %s%n",
                env.getProperty("gateway.engine-uris[0]", "http://localhost:8081"));
        out.println("║   圖解入口     docs/codeGraphic.html                                     ║");
        out.println("║   初學者說明   docs/初學者學習說明書.md                                   ║");
        for (String path : extraPaths(env)) {
            out.printf("║   API 範例     %s%n", base + path);
        }
        out.println("╚════════════════════════════════════════════════════════════════════════╝");
        out.println();
        log.info("{} ready — {}", project, base + "/actuator/health");
    }

    /**
     * 【職責】印出規格書全棧入口；Gateway 啟動通常代表全棧／本機已具備依賴。
     * 【技巧】位址可由 {@code startup.info.*-url} 覆寫；預設對齊 docker-compose 教學埠。
     */
    private static void printStackLinks(PrintStream out, Environment env) {
        String gateway = env.getProperty("startup.info.gateway-url", "http://localhost:8080");
        String engine = env.getProperty("startup.info.engine-url", "http://localhost:8081");
        String prometheus = env.getProperty("startup.info.prometheus-url", "http://localhost:9090");
        String grafana = env.getProperty("startup.info.grafana-url", "http://localhost:3000");

        out.println("║ 【全棧服務連結】                                                          ║");
        out.printf("║   Gateway Health      %s%n", gateway + "/actuator/health");
        out.printf("║   Gateway Metrics     %s%n", gateway + "/actuator/prometheus");
        out.printf("║   Engine Health       %s%n", engine + "/actuator/health");
        out.printf("║   Prometheus          %s%n", prometheus);
        out.printf("║   Grafana             %s%n", grafana);
        out.printf("║   Swagger (Gateway)   %s%n", gateway + "/swagger-ui.html");
        out.printf("║   Swagger (Engine)    %s%n", engine + "/swagger-ui/index.html");
        out.println("║   Grafana 帳密 admin / admin（教學環境）                                   ║");
    }


    /**
     * 【職責】以 UTF-8 寫出 banner（與 JVM stdout.encoding=UTF-8、IDE Console UTF-8 對齊）。
     * 【技巧】勿依賴系統預設 MS950；端到端 UTF-8 才能 run-anywhere。
     */
    private static PrintStream utf8Out() {
        return new PrintStream(System.out, true, StandardCharsets.UTF_8);
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
