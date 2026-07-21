# -----------------------------------------
# env.ps1（環境準備腳本）
# -----------------------------------------
# 目的：
# - 在 Windows 本機開發時，盡量自動找到 JDK 21
# - 設定 JAVA_HOME 與 PATH，讓 gradlew 能正常執行
#
# 用法（注意前面的點 + 空白）：
#   . .\scripts\env.ps1
# 這是「dot-source」語法，代表把環境變數留在目前 shell 會話中。
# 若少了前面那個點，腳本結束後 JAVA_HOME 可能不會保留。

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    # 候選路徑：依序嘗試常見 JDK 21 安裝位置
    $candidates = @(
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*"
    )
    foreach ($pattern in $candidates) {
        # 以萬用字元解析實際資料夾，取第一個符合項
        $resolved = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved -and (Test-Path "$($resolved.FullName)\bin\java.exe")) {
            $env:JAVA_HOME = $resolved.FullName
            break
        }
    }
}

if (-not $env:JAVA_HOME) {
    # 若還是找不到，就提醒使用者手動安裝/設定
    Write-Warning "JAVA_HOME not set. Install JDK 21 or set JAVA_HOME before running Gradle."
} else {
    # 把 JDK bin 放到 PATH 最前面，確保優先使用這個 java
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
