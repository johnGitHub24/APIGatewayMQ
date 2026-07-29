package com.trading.engine.config;

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
 * 【概念】開發便利輸出，不是業務邏輯；{@code local} profile 會開 H2 Console，{@code docker} 則連 PostgreSQL。
 * 【邊界】不負責前端啟動、不驗證 URL 是否可連、不啟動 Docker 基礎設施。
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

        String project = env.getProperty("startup.info.project-name", "APIGatewayMQ Engine");
        String port = env.getProperty("server.port", "8081");
        String base = "http://localhost:" + port;
        String frontend = env.getProperty("startup.info.frontend", "none");
        boolean auth = env.getProperty("startup.info.auth", Boolean.class, false);
        boolean h2 = env.getProperty("startup.info.h2", Boolean.class, false);
        boolean apiDocs = env.getProperty("startup.info.api-docs", Boolean.class, true);
        boolean kafkaListener = env.getProperty("spring.kafka.listener.auto-startup", Boolean.class, true);
        String[] profiles = env.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = env.getDefaultProfiles();
        }

        PrintStream out = utf8Out();
        out.println();
        out.println("╔════════════════════════════════════════════════════════════════════════╗");
        out.printf("║  %-70s║%n", project + " 已啟動 — 使用連結");
        out.println("╠════════════════════════════════════════════════════════════════════════╣");
        out.printf("║  profiles      %s%n", String.join(",", profiles));
        boolean localMode = Arrays.asList(profiles).contains("local") || !kafkaListener;
        printStackLinks(out, env, localMode, base);
        out.println("╠════════════════════════════════════════════════════════════════════════╣");
        out.println("║ 【本服務】                                                                ║");
        out.printf("║   應用資訊     %s%n", base + "/actuator/info");
        if (apiDocs) {
            out.printf("║   OpenAPI JSON %s%n", base + "/v3/api-docs");
        }
        if (h2) {
            out.printf("║   H2 Console   %s%n", base + "/h2-console");
            String jdbc = env.getProperty("spring.datasource.url", "jdbc:h2:mem:tradingengine");
            out.printf("║   H2 JDBC URL  %s  帳號 sa  密碼 (空白)%n", jdbc);
        } else {
            String jdbc = env.getProperty("spring.datasource.url", "(未設定)");
            out.printf("║   JDBC URL     %s%n", jdbc);
        }

        out.println("╠════════════════════════════════════════════════════════════════════════╣");
        out.println("║ 【本機學習提示】                                                          ║");
        if (!kafkaListener) {
            out.println("║   Kafka Listener 已關閉（local）— 請用 POST /api/v1/orders 同步下單練習     ║");
        } else {
            out.printf("║   Kafka        %s%n",
                    env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        }
        out.println("║   圖解入口     docs/codeGraphic.html                                     ║");
        out.println("║   初學者說明   docs/初學者學習說明書.md                                   ║");
        for (String path : extraPaths(env)) {
            out.printf("║   API 範例     %s%n", base + path);
        }

        if (!"none".equalsIgnoreCase(frontend)) {
            out.println("╠════════════════════════════════════════════════════════════════════════╣");
            if ("static".equalsIgnoreCase(frontend)) {
                out.println("║ 【前台】同埠靜態資源                                                      ║");
                out.printf("║   首頁         %s%n", base + env.getProperty("startup.info.home-path", "/"));
            } else if ("vite".equalsIgnoreCase(frontend)) {
                String feBase = "http://localhost:" + env.getProperty("startup.info.frontend-port", "5173");
                out.println("║ 【前台 Vue】需另執行 Frontend (Vite)                                      ║");
                out.printf("║   主頁         %s%n", feBase + env.getProperty("startup.info.home-path", "/"));
            }
            if (auth) {
                out.printf("║   預設帳號     %s / %s%n",
                        env.getProperty("startup.info.default-user", "admin"),
                        env.getProperty("startup.info.default-pass", "admin123"));
            }
        }

        out.println("╚════════════════════════════════════════════════════════════════════════╝");
        out.println();
        log.info("{} ready — profiles={} kafkaListener={} | {}",
                project, String.join(",", profiles), kafkaListener, base + "/actuator/health");
    }

    /**
     * 【職責】印出規格書全棧入口；{@code local} 時區分「本機已可用」與「需 Docker」。
     * 【技巧】位址可由 {@code startup.info.*-url} 覆寫；預設對齊 docker-compose 教學埠。
     * 【概念】local/H2 只起 Engine，印 Gateway／Prometheus／Grafana 若不標註會誤導為「都能開」。
     */
    private static void printStackLinks(PrintStream out, Environment env, boolean localMode, String thisBase) {
        String gateway = env.getProperty("startup.info.gateway-url", "http://localhost:8080");
        String engine = env.getProperty("startup.info.engine-url", "http://localhost:8081");
        // 本行程實際埠優先（IntelliJ 改 port 時仍正確）
        if (thisBase != null && !thisBase.isBlank()) {
            engine = thisBase;
        }
        String prometheus = env.getProperty("startup.info.prometheus-url", "http://localhost:9090");
        String grafana = env.getProperty("startup.info.grafana-url", "http://localhost:3000");

        if (localMode) {
            out.println("║ 【本機已可用 — local/H2】                                                   ║");
            out.printf("║   Engine Health       %s%n", engine + "/actuator/health");
            out.printf("║   Swagger (Engine)    %s%n", engine + "/swagger-ui/index.html");
            out.println("╠════════════════════════════════════════════════════════════════════════╣");
            out.println("║ 【需 Docker 全棧後才可連】  請執行: .\\scripts\\start.ps1                     ║");
            out.printf("║   Gateway Health      %s%n", gateway + "/actuator/health");
            out.printf("║   Gateway Metrics     %s%n", gateway + "/actuator/prometheus");
            out.printf("║   Prometheus          %s%n", prometheus);
            out.printf("║   Grafana             %s%n", grafana);
            out.printf("║   Swagger (Gateway)   %s%n", gateway + "/swagger-ui.html");
            out.println("║   （啟動全棧前請先停掉本機 Engine，避免 8081 埠衝突）                        ║");
        } else {
            out.println("║ 【全棧服務連結】                                                          ║");
            out.printf("║   Gateway Health      %s%n", gateway + "/actuator/health");
            out.printf("║   Gateway Metrics     %s%n", gateway + "/actuator/prometheus");
            out.printf("║   Engine Health       %s%n", engine + "/actuator/health");
            out.printf("║   Prometheus          %s%n", prometheus);
            out.printf("║   Grafana             %s%n", grafana);
            out.printf("║   Swagger (Gateway)   %s%n", gateway + "/swagger-ui.html");
            out.printf("║   Swagger (Engine)    %s%n", engine + "/swagger-ui/index.html");
        }
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
