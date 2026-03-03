FROM maven:3.9-amazoncorretto-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY google-credentials.json ./

RUN mvn clean package -DskipTests

# Создаем папку для JPro
RUN mkdir -p /app/target/jpro

FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    openjdk-21-jre-headless \
    libgl1-mesa-glx \
    libgl1-mesa-dri \
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

# Копируем файлы
COPY --from=builder /app/target/speakAI-*.jar app.jar
COPY --from=builder /app/src/main/resources /app/resources
COPY --from=builder /app/target/jpro /app/jpro
COPY --from=builder /app/google-credentials.json /app/

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp /app/models

# Скачиваем Vosk модель
RUN if [ ! -d "/app/models/vosk-model-small-en" ]; then \
    curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip && \
    unzip /tmp/model.zip -d /tmp/ && \
    mkdir -p /app/models/vosk-model-small-en && \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/ && \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15; \
    fi

# ИСПРАВЛЕННЫЙ start.sh с правильным экранированием
RUN echo "#!/bin/bash" > /app/start.sh && \
    echo "set -e" >> /app/start.sh && \
    echo "" >> /app/start.sh && \
    echo "echo \"=== Starting JPro with JAR ===\"" >> /app/start.sh && \
    echo "JPRO_PORT=\${PORT:-8080}" >> /app/start.sh && \
    echo "echo \"Using port: \$JPRO_PORT\"" >> /app/start.sh && \
    echo "" >> /app/start.sh && \
    echo "exec java \\" >> /app/start.sh && \
    echo "    --add-opens=java.base/java.lang=ALL-UNNAMED \\" >> /app/start.sh && \
    echo "    --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \\" >> /app/start.sh && \
    echo "    --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \\" >> /app/start.sh && \
    echo "    -Djava.awt.headless=true \\" >> /app/start.sh && \
    echo "    -Dprism.order=sw \\" >> /app/start.sh && \
    echo "    -Dprism.verbose=false \\" >> /app/start.sh && \
    echo "    -Duser.dir=/app \\" >> /app/start.sh && \
    echo "    -Djpro.port=\$JPRO_PORT \\" >> /app/start.sh && \
    echo "    -Djpro.host=0.0.0.0 \\" >> /app/start.sh && \
    echo "    -Djpro.http.port=\$JPRO_PORT \\" >> /app/start.sh && \
    echo "    -Djpro.applications.default=com.mygitgor.JProWebApp \\" >> /app/start.sh && \
    echo "    -jar app.jar" >> /app/start.sh && \
    chmod +x /app/start.sh

EXPOSE 8080

CMD ["/app/start.sh"]