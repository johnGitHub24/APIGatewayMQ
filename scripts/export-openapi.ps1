# -----------------------------------------
# export-openapi.ps1（匯出 OpenAPI 文件）
# -----------------------------------------
# 目的：
# - 透過 Gradle 任務匯出 Gateway/Engine 的即時 OpenAPI YAML
# - 不需要先啟動 Docker（使用測試情境匯出）
#
# 用法：
#   .\scripts\export-openapi.ps1
#
# 產出：
# - docs\openapi-engine-live.yaml
# - docs\openapi-gateway-live.yaml

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $root "..")

# 載入 JDK 21，避免 Gradle 因 Java 版本不符而失敗
. (Join-Path $root "env.ps1")

Write-Host "==> Exporting OpenAPI via Gradle (EmbeddedKafka test context)..."
# --no-daemon：避免背景 Gradle daemon 殘留，匯出流程更可預期
& .\gradlew.bat exportOpenApi --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Generated:"
Write-Host "  docs\openapi-engine-live.yaml"
Write-Host "  docs\openapi-gateway-live.yaml"
Write-Host ""
Write-Host "Static specs: docs\openapi.yaml, docs\openapi-gateway.yaml, docs\openapi-engine.yaml"
Write-Host "Runtime Swagger UI (after start.ps1):"
Write-Host "  Gateway http://localhost:8080/swagger-ui.html"
Write-Host "  Engine  http://localhost:8081/swagger-ui/index.html"
