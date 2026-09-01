# -----------------------------------------
# check.ps1（測試入口）
# -----------------------------------------
# 目的：
# - 一鍵執行 Gradle check（單元 + 整合 + 其他驗證）
# - 自動先載入 env.ps1，避免 Java 版本不對
#
# 用法：
#   .\scripts\check.ps1
#   .\scripts\check.ps1 --info
#   .\scripts\check.ps1 --tests "*Order*"

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $root "..")
. (Join-Path $root "env.ps1")

# @args 會把你傳進腳本的額外參數原封不動轉給 gradlew
& .\gradlew.bat check @args
exit $LASTEXITCODE
