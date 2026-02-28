#!/bin/bash

if [ ! -f /app/jprocp-file ]; then
    echo "Creating jprocp-file..."
    echo "/app/app.jar" > /app/jprocp-file
fi

exec java \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \
    --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
    --add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \
    --add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED \
    -Djava.awt.headless=true \
    -Dprism.order=sw \
    -Dprism.verbose=false \
    -Duser.dir=/app \
    -Djpro.port=8080 \
    -Djpro.host=0.0.0.0 \
    -Djpro.http.port=8080 \
    -Djpro.deployment=MAVEN-Normal \
    -Djpro.mode=dev \
    -Djprocpfile=/app/jprocp-file \
    -cp /app/app.jar \
    com.jpro.boot.JProBoot