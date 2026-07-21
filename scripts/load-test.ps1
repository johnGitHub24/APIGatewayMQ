param(
    # 總請求數（壓測總量）
    [int]$Requests = 100,
    # 併發工作數（同時幾條 job 在送請求）
    [int]$Concurrency = 10,
    # Gateway 基底 URL（可改成遠端環境）
    [string]$GatewayUrl = "http://localhost:8080"
)

# -----------------------------------------
# load-test.ps1（簡易壓測）
# -----------------------------------------
# 目的：
# - 快速觀察 Gateway 在指定併發下的收單結果
# - 統計 202 / 429 / 錯誤數量，作為限流與穩定性參考
#
# 用法：
#   .\scripts\load-test.ps1
#   .\scripts\load-test.ps1 -Requests 1000 -Concurrency 50

$ErrorActionPreference = "Stop"

Write-Host "Load test: $Requests requests, concurrency=$Concurrency"
Write-Host "Target: $GatewayUrl/api/v1/orders"

$jobs = @()
# 每個 job 分配幾筆請求（向上取整）
$batchSize = [Math]::Ceiling($Requests / $Concurrency)
$accepted = 0
$rateLimited = 0
$errors = 0
$sw = [System.Diagnostics.Stopwatch]::StartNew()

for ($t = 0; $t -lt $Concurrency; $t++) {
    $threadId = $t
    $jobs += Start-Job -ScriptBlock {
        param($GatewayUrl, $threadId, $batchSize, $Requests)
        $localAccepted = 0
        $localRateLimited = 0
        $localErrors = 0
        for ($i = 0; $i -lt $batchSize; $i++) {
            $idx = $threadId * $batchSize + $i
            if ($idx -ge $Requests) { break }
            # 每筆請求用不同 idempotency key，避免被視為重複請求
            $key = "load-$threadId-$i-$(Get-Random)"
            $body = '{"symbol":"BTCUSDT","side":"BUY","quantity":0.1,"price":65000}'
            try {
                $response = Invoke-WebRequest -Uri "$GatewayUrl/api/v1/orders" `
                    -Method POST `
                    -Headers @{ "Idempotency-Key" = $key; "Content-Type" = "application/json" } `
                    -Body $body `
                    -UseBasicParsing
                if ($response.StatusCode -eq 202) { $localAccepted++ }
                elseif ($response.StatusCode -eq 429) { $localRateLimited++ }
            } catch {
                # 429 代表限流生效；其他當成錯誤
                $status = $_.Exception.Response.StatusCode.value__
                if ($status -eq 429) { $localRateLimited++ } else { $localErrors++ }
            }
        }
        return @{ Accepted = $localAccepted; RateLimited = $localRateLimited; Errors = $localErrors }
    } -ArgumentList $GatewayUrl, $threadId, $batchSize, $Requests
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
$sw.Stop()

# 彙總所有 job 結果
foreach ($r in $results) {
    $accepted += $r.Accepted
    $rateLimited += $r.RateLimited
    $errors += $r.Errors
}

Write-Host ""
Write-Host "Results (${Requests} target):"
Write-Host "  Accepted (202):     $accepted"
Write-Host "  Rate limited (429): $rateLimited"
Write-Host "  Errors:             $errors"
Write-Host "  Duration:           $($sw.Elapsed.TotalSeconds.ToString('F2'))s"
# 吞吐量用「成功 accepted」計算，反映實際可處理能力
Write-Host "  Throughput:         $(($accepted / $sw.Elapsed.TotalSeconds).ToString('F1')) req/s (accepted)"
