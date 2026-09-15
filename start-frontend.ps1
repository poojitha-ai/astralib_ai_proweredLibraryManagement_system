$ErrorActionPreference = "Stop"

$PROJECT_ROOT = $PSScriptRoot
$FRONTEND_DIR = Join-Path $PROJECT_ROOT "astralib-frontend"
$FRONTEND_PORT = 5173

Write-Host ""
Write-Host "============================================"
Write-Host "       AstraLib Frontend Startup"
Write-Host "============================================"
Write-Host ""

if (!(Test-Path $FRONTEND_DIR)) {
    Write-Host "ERROR: Frontend directory not found:"
    Write-Host $FRONTEND_DIR
    exit 1
}

$existing = Get-NetTCPConnection `
    -LocalPort $FRONTEND_PORT `
    -State Listen `
    -ErrorAction SilentlyContinue

if ($existing) {
    Write-Host "ERROR: Port $FRONTEND_PORT is already in use."
    Write-Host ""
    Write-Host "Process using the port:"

    foreach ($connection in $existing) {
        Get-Process -Id $connection.OwningProcess |
        Select-Object Id, ProcessName
    }

    Write-Host ""
    Write-Host "Stop the existing frontend first."
    exit 1
}

Set-Location $FRONTEND_DIR

Write-Host "Frontend directory:"
Write-Host $FRONTEND_DIR
Write-Host ""
Write-Host "Starting AstraLib frontend on port $FRONTEND_PORT..."
Write-Host ""

npm run dev -- --port 5173 --strictPort
