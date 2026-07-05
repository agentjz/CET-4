$ErrorActionPreference = "Stop"

. "$PSScriptRoot\docker.ps1"

function Invoke-CheckedCommand {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Command,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Arguments
  )

  & $Command @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
  }
}

Push-Location $PSScriptRoot\..
try {
  $env:DEBUG = "false"

  Write-Host "== Python startup script =="
  Invoke-CheckedCommand python -m py_compile start_dev.py
  Invoke-CheckedCommand python -m py_compile stop_dev.py
  Invoke-CheckedCommand python -m py_compile start_test.py
  Invoke-CheckedCommand python -m py_compile start_browser_test.py
  Invoke-CheckedCommand python -m py_compile stop_test.py

  Write-Host "== Backend tests =="
  Push-Location backend
  try {
    Invoke-CheckedCommand mvn clean test
  } finally {
    Pop-Location
  }

  Write-Host "== Frontend install =="
  Push-Location frontend
  try {
    if (-not (Test-Path node_modules)) {
      Invoke-CheckedCommand npm.cmd install
    }
    Write-Host "== Frontend typecheck =="
    Invoke-CheckedCommand npm.cmd run typecheck
    Write-Host "== Frontend unit tests =="
    Invoke-CheckedCommand npm.cmd run test:unit
    Write-Host "== Frontend build =="
    Invoke-CheckedCommand npm.cmd run build
  } finally {
    Pop-Location
  }

  Write-Host "== Docker Compose config =="
  Invoke-KaoshiDocker compose config | Out-Null

  Write-Host "== Documentation scan =="
  rg -n "kaoshi-studio|listen-to-this" . --glob "!scripts/verify.ps1" --glob "!ref/**"
  if ($LASTEXITCODE -eq 0) { throw "Found stale or unimplemented wording." }
  if ($LASTEXITCODE -ne 1) { throw "rg scan failed." }

  Write-Host "Verification passed."
} finally {
  Pop-Location
}

