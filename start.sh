#!/bin/bash
set -e

echo "=== Starting JPro with JavaFX modules ==="
JPRO_PORT=${PORT:-8080}
echo "Using port: $JPRO_PORT"

JAVAFX_PATH="/app/javafx-libs"
MODULE_PARAMS=""

if [ -d "$JAVAFX_PATH" ]; then
    echo "JavaFX modules found at: $JAVAFX_PATH"
    # Формируем параметры правильно
    MODULE_PARAMS="--module-path $JAVAFX_PATH --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.media"
else
    echo "WARNING: JavaFX modules not found, trying without module-path"
fi

echo "=== Java command ==="
echo java $MODULE_PARAMS \
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

# Исполняем команду
exec java $MODULE_PARAMS \
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