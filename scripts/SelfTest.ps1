param([string]$Node = 'node', [string]$Python = 'python')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & $Python -B -c "import cryptography"
    if ($LASTEXITCODE -ne 0) { throw "Install updates/requirements.txt into the selected Python environment first" }
    & $Python -B scripts/check_versions.py
    if ($LASTEXITCODE -ne 0) { throw 'Release notes or generated histories are inconsistent' }
    & $Node --test
    if ($LASTEXITCODE -ne 0) { throw 'Node tests failed' }
    & $Python -B -m unittest discover -s test -p 'test_*.py' -v
    if ($LASTEXITCODE -ne 0) { throw 'Connector tests failed' }
    & $Python -B -m unittest discover -s relay -p 'test_*.py' -v
    if ($LASTEXITCODE -ne 0) { throw 'Relay tests failed' }
    Write-Output 'Core self-tests passed. This is not real-phone or deployment acceptance.'
} finally { Pop-Location }
