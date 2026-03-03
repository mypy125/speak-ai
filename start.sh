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
JPRO_PORT=${PORT:-8080}
echo "Using port: $JPRO_PORT"
echo "Testing port availability..."
nc -zv localhost $JPRO_PORT 2>&1 || echo "Port $JPRO_PORT is free"
echo ""

echo "=== Starting JPro on port $JPRO_PORT ==="
mvn jpro:run -DskipTests \
    -Dhttp.port=$JPRO_PORT \
    -Djpro.port=$JPRO_PORT \
    -Djpro.http.port=$JPRO_PORT \
    -Djpro.host=0.0.0.0 \
    -X