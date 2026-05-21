@echo off
cd /d "%~dp0"
git add .
git commit -m "Update"
git push
echo.
echo Done! Check GitHub Actions for build status.
pause
