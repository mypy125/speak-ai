#!/bin/bash
set -e

echo "=== Starting JPro with JavaFX 21 modules ==="
JPRO_PORT=${PORT:-8080}
echo "Using port: $JPRO_PORT"

echo "=== JavaFX 21 Modules Available ==="
ls -la /app/javafx-libs/ | grep javafx-.*21

JAVAFX_PATH="/app/javafx-libs"
if [ -d "$JAVAFX_PATH" ] && [ "$(ls -A $JAVAFX_PATH)" ]; then
    echo "JavaFX 21 modules found at: $JAVAFX_PATH"
    # Добавляем параметры для headless режима
    MODULE_PARAMS="--module-path $JAVAFX_PATH --add-modules javafx.controls,javafx.fxml,javafx.media,javafx.web"
else
    echo "WARNING: JavaFX 21 modules not found"
    MODULE_PARAMS=""
fi

echo "=== Java command ==="
echo java $MODULE_PARAMS \
    -Djava.awt.headless=true \
    -Dprism.order=sw \
    -Dprism.verbose=false \
    -Djavafx.platform=monocle \
    -Dglass.platform=Monocle \
    -Dmonocle.platform=Headless \
    -Djavafx.verbose=true \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \
    --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
    -Duser.dir=/app \
    -Djpro.port=$JPRO_PORT \
    -Djpro.host=0.0.0.0 \
    -Djpro.http.port=$JPRO_PORT \
    -Djpro.applications.default=com.mygitgor.JProWebApp \
    -jar app.jar

exec java $MODULE_PARAMS \
    -Djava.awt.headless=true \
    -Dprism.order=sw \
    -Dprism.verbose=false \
    -Djavafx.platform=monocle \
    -Dglass.platform=Monocle \
    -Dmonocle.platform=Headless \
    -Djavafx.verbose=true \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \
    --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
    -Duser.dir=/app \
    -Djpro.port=$JPRO_PORT \
    -Djpro.host=0.0.0.0 \
    -Djpro.http.port=$JPRO_PORT \
    -Djpro.applications.default=com.mygitgor.JProWebApp \
    -jar app.jar