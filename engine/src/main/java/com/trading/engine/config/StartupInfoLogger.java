package com.trading.engine.config;

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
 * 【職責】應用就緒後於 Console 印出常用 URL（health／Swagger／H2／主要 API），方便 IntelliJ 本機啟動。
 * 【技巧】聽 {@link ApplicationReadyEvent}；開關全來自 {@code startup.info.*}（見 application*.yml）。
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

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║  %-70s║%n", project + " 已啟動 — 使用連結");
        System.out.println("╠════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  profiles      %s%n", String.join(",", profiles));
        System.out.println("║ 【後端 API / 工具】                                                      ║");
        System.out.printf("║   健康檢查     %s%n", base + "/actuator/health");
        System.out.printf("║   應用資訊     %s%n", base + "/actuator/info");
        if (apiDocs) {
            System.out.printf("║   Swagger UI   %s%n", base + "/swagger-ui.html");
            System.out.printf("║   OpenAPI JSON %s%n", base + "/v3/api-docs");
        }
        if (h2) {
            System.out.printf("║   H2 Console   %s%n", base + "/h2-console");
            String jdbc = env.getProperty("spring.datasource.url", "jdbc:h2:mem:tradingengine");
            System.out.printf("║   H2 JDBC URL  %s  帳號 sa  密碼 (空白)%n", jdbc);
        } else {
            String jdbc = env.getProperty("spring.datasource.url", "(未設定)");
            System.out.printf("║   JDBC URL     %s%n", jdbc);
        }

        System.out.println("╠════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ 【本機學習提示】                                                          ║");
        if (!kafkaListener) {
            System.out.println("║   Kafka Listener 已關閉（local）— 請用 POST /api/v1/orders 同步下單練習     ║");
        } else {
            System.out.printf("║   Kafka        %s%n",
                    env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        }
        System.out.println("║   圖解入口     docs/codeGraphic.html                                     ║");
        System.out.println("║   初學者說明   docs/初學者學習說明書.md                                   ║");
        for (String path : extraPaths(env)) {
            System.out.printf("║   API 範例     %s%n", base + path);
        }

        if (!"none".equalsIgnoreCase(frontend)) {
            System.out.println("╠════════════════════════════════════════════════════════════════════════╣");
            if ("static".equalsIgnoreCase(frontend)) {
                System.out.println("║ 【前台】同埠靜態資源                                                      ║");
                System.out.printf("║   首頁         %s%n", base + env.getProperty("startup.info.home-path", "/"));
            } else if ("vite".equalsIgnoreCase(frontend)) {
                String feBase = "http://localhost:" + env.getProperty("startup.info.frontend-port", "5173");
                System.out.println("║ 【前台 Vue】需另執行 Frontend (Vite)                                      ║");
                System.out.printf("║   主頁         %s%n", feBase + env.getProperty("startup.info.home-path", "/"));
            }
            if (auth) {
                System.out.printf("║   預設帳號     %s / %s%n",
                        env.getProperty("startup.info.default-user", "admin"),
                        env.getProperty("startup.info.default-pass", "admin123"));
            }
        }

        System.out.println("╚════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        log.info("{} ready — profiles={} kafkaListener={} | {}",
                project, String.join(",", profiles), kafkaListener, base + "/actuator/health");
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
