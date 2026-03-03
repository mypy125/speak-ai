FROM maven:3.9-amazoncorretto-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY google-credentials.json ./

RUN mvn clean package -DskipTests

# Копируем JavaFX модули из кэша Maven
RUN mkdir -p /app/javafx-libs
RUN cp -r /root/.m2/repository/org/openjfx/javafx-* /app/javafx-libs/ 2>/dev/null || true

FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    openjdk-21-jre-headless \
    libgl1-mesa-glx \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libxrandr2 \
    libasound2 \
    libfontconfig1 \
    libfreetype6 \
    curl \
    unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/speakAI-*.jar app.jar
COPY --from=builder /app/javafx-libs /app/javafx-libs
COPY --from=builder /app/src/main/resources /app/resources
COPY --from=builder /app/google-credentials.json /app/

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp /app/models

RUN if [ ! -d "/app/models/vosk-model-small-en" ]; then \
    curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip && \
    unzip /tmp/model.zip -d /tmp/ && \
    mkdir -p /app/models/vosk-model-small-en && \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/ && \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15; \
    fi

# Создаем скрипт запуска с module-path
RUN echo '#!/bin/bash' > /app/start.sh && \
    echo 'set -e' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo 'echo "=== Starting JPro with JavaFX modules ==="' >> /app/start.sh && \
    echo 'JPRO_PORT=${PORT:-8080}' >> /app/start.sh && \
    echo 'echo "Using port: $JPRO_PORT"' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo 'JAVAFX_PATH="/app/javafx-libs"' >> /app/start.sh && \
    echo 'if [ -d "$JAVAFX_PATH" ]; then' >> /app/start.sh && \
    echo '    echo "JavaFX modules found at: $JAVAFX_PATH"' >> /app/start.sh && \
    echo '    MODULE_PATH="--module-path $JAVAFX_PATH --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.media"' >> /app/start.sh && \
    echo 'else' >> /app/start.sh && \
    echo '    echo "WARNING: JavaFX modules not found, trying without module-path"' >> /app/start.sh && \
    echo '    MODULE_PATH=""' >> /app/start.sh && \
    echo 'fi' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo 'exec java \$MODULE_PATH \' >> /app/start.sh && \
    echo '    --add-opens=java.base/java.lang=ALL-UNNAMED \' >> /app/start.sh && \
    echo '    --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \' >> /app/start.sh && \
    echo '    --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \' >> /app/start.sh && \
    echo '    -Djava.awt.headless=true \' >> /app/start.sh && \
    echo '    -Dprism.order=sw \' >> /app/start.sh && \
    echo '    -Dprism.verbose=false \' >> /app/start.sh && \
    echo '    -Duser.dir=/app \' >> /app/start.sh && \
    echo '    -Djpro.port=\$JPRO_PORT \' >> /app/start.sh && \
    echo '    -Djpro.host=0.0.0.0 \' >> /app/start.sh && \
    echo '    -Djpro.http.port=\$JPRO_PORT \' >> /app/start.sh && \
    echo '    -Djpro.applications.default=com.mygitgor.JProWebApp \' >> /app/start.sh && \
    echo '    -jar app.jar' >> /app/start.sh && \
    chmod +x /app/start.sh

EXPOSE 8080

CMD ["/app/start.sh"]