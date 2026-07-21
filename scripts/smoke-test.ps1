# -----------------------------------------
# smoke-test.ps1（冒煙測試）
# -----------------------------------------
# 目的：
# - 快速確認「服務有起來 + 主要功能可用」
# - 驗證最小主流程：健康檢查 → 下單 → 查單 → Swagger 可訪問
#
# 前置：
# - 先執行 .\scripts\start.ps1
#
# 用法：
#   .\scripts\smoke-test.ps1
#   .\scripts\smoke-test.ps1 -MaxWaitSeconds 240

param(
    # 最多等待服務健康的秒數（預設 180 秒）
    [int] $MaxWaitSeconds = 180
)

$ErrorActionPreference = "Stop"

function Wait-Healthy($url, $label) {
    # 設定等待截止時間（避免無限等待）
    $deadline = (Get-Date).AddSeconds($MaxWaitSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            # 呼叫健康檢查端點；TimeoutSec 防止單次卡太久
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200 -and $r.Content -match '"status"\s*:\s*"UP"') {
                Write-Host "[OK] $label"
                return $true
            }
        } catch {}
        # 服務尚未 ready 時每 5 秒重試一次
        Start-Sleep -Seconds 5
    }
    Write-Error "[FAIL] $label not healthy: $url"
    return $false
}

Write-Host "==> Waiting for services..."
# 依序確認 Gateway / Engine-1 / Engine-2 都是 UP
Wait-Healthy "http://localhost:8080/actuator/health" "Gateway" | Out-Null
Wait-Healthy "http://localhost:8081/actuator/health" "Engine-1" | Out-Null
Wait-Healthy "http://localhost:8082/actuator/health" "Engine-2" | Out-Null

Write-Host "==> POST async order via Gateway..."
$headers = @{
    "Content-Type"     = "application/json"
    # 用時間戳當冪等鍵，避免與前一次 smoke test 衝突
    "Idempotency-Key"  = "smoke-$(Get-Date -Format 'yyyyMMddHHmmss')"
}
$body = '{"symbol":"BTCUSDT","side":"BUY","quantity":0.5,"price":65000}'
$order = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/orders" -Headers $headers -Body $body
# 預期是非同步收單，所以狀態應為 ACCEPTED
if ($order.status -ne "ACCEPTED") { throw "Expected ACCEPTED, got $($order.status)" }
Write-Host "[OK] Order accepted: $($order.clientOrderId)"

Write-Host "==> Poll order by clientOrderId..."
$deadline = (Get-Date).AddSeconds(60)
$found = $false
while ((Get-Date) -lt $deadline) {
    # 非同步流程需要時間處理，輪詢查詢結果
    Start-Sleep -Seconds 3
    try {
        $q = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders?clientOrderId=$($order.clientOrderId)"
        if ($q.status) {
            Write-Host "[OK] Order status: $($q.status)"
            $found = $true
            break
        }
    } catch {}
}
if (-not $found) { throw "Order not found after polling" }

Write-Host "==> Swagger UI endpoints..."
# 驗證 Swagger UI 頁面可打開，確保 API 文件服務正常
foreach ($url in @(
    "http://localhost:8080/swagger-ui.html",
    "http://localhost:8081/swagger-ui/index.html"
)) {
    $r = Invoke-WebRequest -Uri $url -UseBasicParsing -MaximumRedirection 5
    if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 400) {
        Write-Host "[OK] $url"
    } else {
        throw "Swagger not reachable: $url"
    }
}

Write-Host ""
Write-Host "Smoke test PASSED."
