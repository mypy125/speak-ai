#!/bin/bash
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Starting SpeakAI JPro Application${NC}"
echo -e "${BLUE}========================================${NC}"

echo -e "\n${YELLOW}=== Environment Info ===${NC}"
echo "Date: $(date)"
echo "Hostname: $(hostname)"
echo "Current directory: $(pwd)"
echo "User: $(whoami)"
echo "PID: $$"

echo -e "\n${YELLOW}=== Java Version ===${NC}"
java -version 2>&1

echo -e "\n${YELLOW}=== Memory Info ===${NC}"
free -h 2>/dev/null || echo "free command not available"
echo "Max memory: $(java -XX:+PrintFlagsFinal -version 2>/dev/null | grep MaxHeapSize || echo "N/A")"

echo -e "\n${YELLOW}=== Port Configuration ===${NC}"
echo "PORT environment variable: ${PORT:-not set}"
JPRO_PORT=${PORT:-8080}
echo "Using port: $JPRO_PORT"

if command -v nc &> /dev/null; then
    echo "Checking if port $JPRO_PORT is available..."
    if nc -z localhost $JPRO_PORT 2>/dev/null; then
        echo -e "${RED}WARNING: Port $JPRO_PORT is already in use!${NC}"
    else
        echo -e "${GREEN}Port $JPRO_PORT is available${NC}"
    fi
else
    echo "nc command not available, skipping port check"
fi

echo -e "\n${YELLOW}=== Classpath Check ===${NC}"
if [ ! -f /app/jprocp-file ]; then
    echo -e "${YELLOW}Creating jprocp-file...${NC}"
    echo "/app/app.jar" > /app/jprocp-file
else
    echo -e "${GREEN}jprocp-file exists${NC}"
fi

echo "Classpath contains:"
cat /app/jprocp-file | while read line; do
    if [ -f "$line" ]; then
        echo -e "  ${GREEN}✓${NC} $line ($(du -h "$line" | cut -f1))"
    else
        echo -e "  ${RED}✗${NC} $line (FILE NOT FOUND!)"
    fi
done

echo -e "\n${YELLOW}=== Google Credentials ===${NC}"
if [ -f /app/google-credentials.json ]; then
    echo -e "${GREEN}✓${NC} google-credentials.json found"
    echo "Size: $(du -h /app/google-credentials.json | cut -f1)"
else
    echo -e "${RED}✗${NC} google-credentials.json not found"
fi

echo -e "\n${YELLOW}=== Vosk Model ===${NC}"
if [ -d /app/models/vosk-model-small-en ]; then
    echo -e "${GREEN}✓${NC} Vosk model found"
    echo "Model files: $(find /app/models/vosk-model-small-en -type f | wc -l) files"
    echo "Model size: $(du -sh /app/models/vosk-model-small-en | cut -f1)"
else
    echo -e "${RED}✗${NC} Vosk model not found"
fi

echo -e "\n${YELLOW}=== Application JAR ===${NC}"
if [ -f /app/app.jar ]; then
    echo -e "${GREEN}✓${NC} app.jar found"
    echo "Size: $(du -h /app/app.jar | cut -f1)"
    echo "Last modified: $(stat -c %y /app/app.jar 2>/dev/null || echo "N/A")"
else
    echo -e "${RED}✗${NC} app.jar not found!"
    exit 1
fi

echo -e "\n${YELLOW}=== Data Directories ===${NC}"
for dir in data recordings logs exports tmp; do
    if [ -d "/app/$dir" ]; then
        echo -e "${GREEN}✓${NC} /app/$dir exists"
    else
        echo -e "${YELLOW}⚠${NC} /app/$dir does not exist, creating..."
        mkdir -p "/app/$dir"
    fi
done

echo -e "\n${BLUE}========================================${NC}"
echo -e "${GREEN}Starting JPro with application: com.mygitgor.JProWebApp${NC}"
echo -e "${BLUE}========================================${NC}"

exec java \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.io=ALL-UNNAMED \
    --add-opens=java.base/java.nio=ALL-UNNAMED \
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \
    --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
    --add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \
    --add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED \
    --add-opens=javafx.base/com.sun.javafx=ALL-UNNAMED \
    --add-opens=javafx.base/com.sun.javafx.collections=ALL-UNNAMED \
    --add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
    -Djava.awt.headless=true \
    -Dprism.order=sw \
    -Dprism.verbose=true \
    -Dprism.debug=true \
    -Djavafx.verbose=true \
    -Duser.dir=/app \
    -Djpro.port=$JPRO_PORT \
    -Djpro.host=0.0.0.0 \
    -Djpro.http.port=$JPRO_PORT \
    -Djpro.deployment=MAVEN-Normal \
    -Djpro.mode=dev \
    -Djpro.applications.default=com.mygitgor.JProWebApp \
    -Djpro.rootPath=/ \
    -Djprocpfile=/app/jprocp-file \
    -cp /app/app.jar \
    com.jpro.boot.JProBoot 2>&1 | tee /app/jpro.log

if [ $? -ne 0 ]; then
    echo -e "\n${RED}=== JPro exited with error! Last 50 lines of log: ===${NC}"
    tail -50 /app/jpro.log
    echo -e "\n${RED}Full log available at: /app/jpro.log${NC}"
fi