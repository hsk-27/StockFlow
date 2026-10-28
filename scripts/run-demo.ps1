# ==============================================================================
# StockFlow Senior Capstone Quick-Start Launcher (Windows PowerShell)
# ==============================================================================

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "  StockFlow — Senior CS Capstone Engineering Portfolio" -ForegroundColor Cyan
Write-Host "  Java 21 | Spring Boot 3.3.5 | PostgreSQL 16" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

# Check Docker
$dockerRunning = docker info 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[+] Docker is running. Starting PostgreSQL 16 container..." -ForegroundColor Green
    docker-compose up -d postgres
    Start-Sleep -Seconds 3
} else {
    Write-Host "[!] Docker is not running. Using embedded H2 profile for testing." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Compiling and packaging StockFlow..." -ForegroundColor Cyan
mvn clean package -DskipTests

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host "  BUILD SUCCESSFUL! Starting StockFlow Application..." -ForegroundColor Green
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host "  Web Showcase:   http://localhost:8080/" -ForegroundColor White
    Write-Host "  Swagger UI:     http://localhost:8080/swagger-ui.html" -ForegroundColor White
    Write-Host "  Actuator:       http://localhost:8080/actuator/health" -ForegroundColor White
    Write-Host ""
    Write-Host "  Seed Logins (password: Password123!):" -ForegroundColor White
    Write-Host "    - Admin:    admin@stockflow.com" -ForegroundColor Gray
    Write-Host "    - Seller:   seller@stockflow.com" -ForegroundColor Gray
    Write-Host "    - Customer: customer@stockflow.com" -ForegroundColor Gray
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host ""

    java -jar target/stockflow-backend-1.0.0-SNAPSHOT.jar
} else {
    Write-Host "[X] Build failed! Check Maven compiler output." -ForegroundColor Red
}
