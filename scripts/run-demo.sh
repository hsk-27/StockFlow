#!/usr/bin/env bash
# ==============================================================================
# StockFlow Senior Capstone Quick-Start Launcher (Linux / macOS Bash)
# ==============================================================================

set -e

echo "======================================================================"
echo "  StockFlow — Senior CS Capstone Engineering Portfolio"
echo "  Java 21 | Spring Boot 3.3.5 | PostgreSQL 16"
echo "======================================================================"
echo ""

# Check Docker
if docker info > /dev/null 2>&1; then
    echo "[+] Docker is running. Starting PostgreSQL 16 container..."
    docker compose up -d postgres
    sleep 3
else
    echo "[!] Docker not detected or not running. Using local PostgreSQL or test profile."
fi

echo ""
echo "Compiling and packaging StockFlow..."
mvn clean package -DskipTests

echo ""
echo "======================================================================"
echo "  BUILD SUCCESSFUL! Starting StockFlow Application..."
echo "======================================================================"
echo "  Web Showcase:   http://localhost:8080/"
echo "  Swagger UI:     http://localhost:8080/swagger-ui.html"
echo "  Actuator:       http://localhost:8080/actuator/health"
echo ""
echo "  Seed Logins (password: Password123!):"
echo "    - Admin:    admin@stockflow.com"
echo "    - Seller:   seller@stockflow.com"
echo "    - Customer: customer@stockflow.com"
echo "======================================================================"
echo ""

java -jar target/stockflow-backend-1.0.0-SNAPSHOT.jar
