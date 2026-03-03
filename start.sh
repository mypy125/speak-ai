#!/bin/bash
set -e

echo "=== Starting JPro with JAR ==="
JPRO_PORT=${PORT:-8080}
echo "Using port: $JPRO_PORT"

# Определяем путь к JavaFX модулям
JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
JAVAFX_PATH="/app/jpro-libs"

# Создаем папку для JavaFX модулей если её нет
mkdir -p $JAVAFX_PATH

# Копируем JavaFX модули из Maven кэша (если есть)
if [ -d "/root/.m2/repository/org/openjfx" ]; then
    echo "Copying JavaFX modules from Maven cache..."
    cp -r /root/.m2/repository/org/openjfx/* $JAVAFX_PATH/ 2>/dev/null || true
fi

echo "JavaFX path: $JAVAFX_PATH"
echo "Java home: $JAVA_HOME"

exec java \
    --module-path $JAVAFX_PATH \
    --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.media \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \
    --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
    -Djava.awt.headless=true \
    -Dprism.order=sw \
    -Dprism.verbose=false \
    -Duser.dir=/app \
    -Djpro.port=$JPRO_PORT \
    -Djpro.host=0.0.0.0 \
    -Djpro.http.port=$JPRO_PORT \
    -Djpro.applications.default=com.mygitgor.JProWebApp \
    -jar app.jar