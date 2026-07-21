# CodeGraphic image export

Source: docs/codeGraphic.html
Tool: @mermaid-js/mermaid-cli@11 (dark)
Script: EngineeringOS/eos-minimal/hooks/export-codeGraphic-images.ps1

| File | Tab |
|------|-----|
| `01-async.svg` / `.png` | 非同步下單 |
| `02-ratelimit.svg` / `.png` | 限流 |
| `03-engine.svg` / `.png` | Engine |
| `04-modules.svg` / `.png` | 模組 |

Re-run from project root:

    & "d:\ClaudeCode\EngineeringOS\eos-minimal\hooks\export-codeGraphic-images.ps1" -ProjectRoot .
