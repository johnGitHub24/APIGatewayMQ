# -----------------------------------------
# verify-docker.ps1（Docker 全棧一鍵驗證）
# -----------------------------------------
# 目的：
# - 先檢查 Docker 是否可用
# - 再啟動整套服務（start.ps1）
# - 最後跑冒煙測試（smoke-test.ps1）
#
# 用法：
#   .\scripts\verify-docker.ps1
#
# 成功標準：
# - start.ps1 成功
# - smoke-test.ps1 顯示 Smoke test PASSED

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# 這段暫時把 ErrorAction 改成 Continue，
# 讓我們可以用 docker info 的 exit code 自行判斷是否安裝/啟動。
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
cmd /c "docker info >nul 2>&1"
$dockerOk = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prevEap

if (-not $dockerOk) {
    Write-Error "Docker is not running. Start Docker Desktop, then run this script again."
    exit 1
}

# 第一步：啟動與建置
& (Join-Path $root "start.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 第二步：冒煙測試
& (Join-Path $root "smoke-test.ps1")
exit $LASTEXITCODE
