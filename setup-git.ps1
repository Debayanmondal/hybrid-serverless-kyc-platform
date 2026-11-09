# ==============================================================================
# Helper Script to Initialize Git and Push to GitHub
# ==============================================================================

param(
    [string]$RepoUrl = "https://github.com/Debayanmondal/hybrid-serverless-kyc-platform.git"
)

Write-Host "Initializing Git Repository for Hybrid Serverless KYC Platform..." -ForegroundColor Cyan

git init
git branch -M main
git add .
git commit -m "feat: initial release of hybrid serverless microservices kyc and digital onboarding platform"

Write-Host "`nLocal repository initialized and committed!" -ForegroundColor Green
Write-Host "To link and push to your GitHub account:" -ForegroundColor Cyan
Write-Host "  1. Create a repository named 'hybrid-serverless-kyc-platform' on https://github.com/new" -ForegroundColor Yellow
Write-Host "  2. Run the following commands:" -ForegroundColor Yellow
Write-Host "     git remote add origin $RepoUrl" -ForegroundColor White
Write-Host "     git push -u origin main" -ForegroundColor White
