# ==============================================================================
# Helper Script to Build Hybrid Serverless KYC Platform
# ==============================================================================

Write-Host "Checking for Docker..." -ForegroundColor Cyan
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "Docker found. You can build and launch the platform with:" -ForegroundColor Green
    Write-Host "   docker compose up --build -d" -ForegroundColor Yellow
} else {
    Write-Host "Docker not found in PATH." -ForegroundColor DarkYellow
}

Write-Host "`nChecking for Maven..." -ForegroundColor Cyan
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "Maven found! Building multi-module jars..." -ForegroundColor Green
    mvn clean package -DskipTests
} else {
    Write-Host "Maven ('mvn') is not found on your system PATH." -ForegroundColor Yellow
    Write-Host "Options to build & run:" -ForegroundColor Cyan
    Write-Host " 1. Run Docker Desktop and execute: docker compose up --build" -ForegroundColor Green
    Write-Host " 2. Open this folder in IntelliJ IDEA or Eclipse / VS Code (they have built-in Maven)." -ForegroundColor Green
}
