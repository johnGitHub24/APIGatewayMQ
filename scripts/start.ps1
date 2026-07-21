param(
    # 只編譯不啟動 Docker（適合先確認 build 有沒有過）
    [switch]$BuildOnly
)

# -----------------------------------------
# start.ps1（啟動全棧）
# -----------------------------------------
# 會做兩件事：
# 1) 先用 Gradle 產出 gateway / engine 的可執行 JAR
# 2) 再用 docker compose 啟動基礎設施與服務
#
# 常見用法：
#   .\scripts\start.ps1
#   .\scripts\start.ps1 -BuildOnly

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $root "..")

# 先套用 JDK 環境，避免 gradlew 誤用舊版 Java
. (Join-Path $root "env.ps1")

Write-Host "==> Building gateway & engine JARs..."
& .\gradlew.bat :gateway:bootJar :engine:bootJar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($BuildOnly) {
    Write-Host "Build complete."
    exit 0
}

Write-Host "==> Starting infrastructure + services..."
# -d: 背景啟動
# --build: 若 Dockerfile 或程式有變更，重新建映像
docker compose up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Error "docker compose failed. Ensure Docker Desktop is running."
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Gateway:    http://localhost:8080/actuator/health"
Write-Host "Gateway UI: http://localhost:8080/swagger-ui.html"
Write-Host "Engine-1:   http://localhost:8081/actuator/health"
Write-Host "Engine UI:  http://localhost:8081/swagger-ui/index.html"
Write-Host "Engine-2:   http://localhost:8082/actuator/health"
Write-Host "Prometheus: http://localhost:9090"
Write-Host "Grafana:    http://localhost:3000 (admin/admin)"
Write-Host ""
Write-Host "Sample order:"
# 這行 curl 可以用來快速驗證 gateway API 是否可收單
Write-Host '  curl -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" -H "Idempotency-Key: demo-001" -d "{\"symbol\":\"BTCUSDT\",\"side\":\"BUY\",\"quantity\":0.5,\"price\":65000}"'
