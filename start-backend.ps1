$ErrorActionPreference = "Stop"

$PROJECT_ROOT = $PSScriptRoot
$CONFIG_FILE = Join-Path $PROJECT_ROOT "config.ps1"
$BACKEND_DIR = Join-Path $PROJECT_ROOT "astralib-backend"

Write-Host ""
Write-Host "============================================"
Write-Host "       AstraLib Backend Startup"
Write-Host "============================================"
Write-Host ""

if (!(Test-Path $CONFIG_FILE)) {
    Write-Host "ERROR: config.ps1 was not found."
    Write-Host ""
    Write-Host "Create it from:"
    Write-Host "  config.example.ps1"
    Write-Host ""
    exit 1
}

Write-Host "Loading local configuration..."
. $CONFIG_FILE

if (!(Test-Path $BACKEND_DIR)) {
    Write-Host "ERROR: Backend directory not found:"
    Write-Host $BACKEND_DIR
    exit 1
}

Set-Location $BACKEND_DIR

Write-Host "Backend directory:"
Write-Host $BACKEND_DIR
Write-Host ""
Write-Host "Starting AstraLib backend..."
Write-Host "Expected port: $env:SERVER_PORT"
Write-Host ""
Write-Host "LLM model loaded from config:"
Write-Host $env:LLM_MODEL
Write-Host ""

mvn spring-boot:run
