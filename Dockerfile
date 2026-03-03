FROM maven:3.9-amazoncorretto-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY google-credentials.json ./

RUN mvn clean package -DskipTests

FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    openjdk-21-jre-headless \
    maven \
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
    netcat-openbsd \
    lsof \
    procps \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app /app

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp /app/models

RUN if [ ! -d "/app/models/vosk-model-small-en" ]; then \
    curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip && \
    unzip /tmp/model.zip -d /tmp/ && \
    mkdir -p /app/models/vosk-model-small-en && \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/ && \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15; \
    fi

# ИСПРАВЛЕННЫЙ start.sh - без экранирования переменных
RUN echo '#!/bin/bash' > /app/start.sh && \
    echo 'set -e' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo 'echo "=== Environment Info ==="' >> /app/start.sh && \
    echo 'echo "Date: $(date)"' >> /app/start.sh && \
    echo 'echo "Hostname: $(hostname)"' >> /app/start.sh && \
    echo 'echo "Current directory: $(pwd)"' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo 'echo "=== Java Version ==="' >> /app/start.sh && \
    echo 'java -version' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo 'echo "=== Port Check ==="' >> /app/start.sh && \
    echo 'echo "PORT environment variable: ${PORT}"' >> /app/start.sh && \
    echo 'JPRO_PORT=${PORT:-8080}' >> /app/start.sh && \
    echo 'echo "Using port: $JPRO_PORT"' >> /app/start.sh && \
    echo 'echo "Testing port availability..."' >> /app/start.sh && \
    echo 'nc -zv localhost $JPRO_PORT 2>&1 || echo "Port $JPRO_PORT is free"' >> /app/start.sh && \
    echo '' >> /app/start.sh && \
    echo 'echo "=== Starting JPro on port $JPRO_PORT ==="' >> /app/start.sh && \
    echo 'mvn jpro:run -DskipTests \\' >> /app/start.sh && \
    echo '    -Dhttp.port=$JPRO_PORT \\' >> /app/start.sh && \
    echo '    -Djpro.port=$JPRO_PORT \\' >> /app/start.sh && \
    echo '    -Djpro.http.port=$JPRO_PORT \\' >> /app/start.sh && \
    echo '    -Djpro.host=0.0.0.0 \\' >> /app/start.sh && \
    echo '    -X' >> /app/start.sh && \
    chmod +x /app/start.sh

EXPOSE 8080

CMD ["/app/start.sh"]