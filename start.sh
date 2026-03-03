#!/bin/bash
set -e

echo "=== Environment Info ==="
echo "Date: $(date)"
echo "Hostname: $(hostname)"
echo "Current directory: $(pwd)"
echo ""

echo "=== Java Version ==="
java -version
echo ""

echo "=== Port Check ==="
echo "PORT environment variable: ${PORT}"
# Правильно определяем порт
JPRO_PORT=${PORT:-8080}
echo "Using port: $JPRO_PORT"
echo "Testing port availability..."
nc -zv localhost $JPRO_PORT 2>&1 || echo "Port $JPRO_PORT is free"
echo ""

echo "=== Starting JPro on port $JPRO_PORT ==="
# Передаем порт как число, а не как переменную
mvn jpro:run -DskipTests \
    -Dhttp.port=$JPRO_PORT \
    -Djpro.port=$JPRO_PORT \
    -Djpro.http.port=$JPRO_PORT \
    -Djpro.host=0.0.0.0 \
    -X