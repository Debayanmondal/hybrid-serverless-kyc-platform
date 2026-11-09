#!/usr/bin/env bash
# ==============================================================================
# Helper Script to Link and Push to GitHub (Git Bash / Linux / macOS)
# ==============================================================================

REPO_URL="${1:-https://github.com/Debayanmondal/hybrid-serverless-kyc-platform.git}"

echo "Configuring Git remote origin: $REPO_URL..."

git branch -M main

if git remote | grep -q "origin"; then
    git remote set-url origin "$REPO_URL"
else
    git remote add origin "$REPO_URL"
fi

echo "Remote configured! Pushing to main branch..."
git push -u origin main
