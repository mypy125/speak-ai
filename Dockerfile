FROM maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests

FROM azul/zulu-openjdk:17-jre-headless-latest

RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libgl1-mesa-dri \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libxrandr2 \
    libasound2 \
    libfontconfig1 \
    libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/speakAI-1.0-SNAPSHOT.jar app.jar

COPY --from=builder /app/src/main/resources /app/resources
COPY --from=builder /app/google-credentials.json /app/google-credentials.json
COPY --from=builder /app/models /app/models

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp

RUN if [ ! -d "/app/models" ] || [ -z "$(ls -A /app/models)" ]; then \
    echo "Models directory is empty, downloading basic English model..."; \
    mkdir -p /app/models/vosk-model-small-en; \
    curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip; \
    unzip /tmp/model.zip -d /tmp/; \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/; \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15; \
    fi

EXPOSE 8080

ENV JPRO_PORT=8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_WEBSOCKETS_PORT=8080
ENV JPRO_HTTP_PORT=8080
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/google-credentials.json
ENV JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED --add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED --add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED -Djava.awt.headless=true -Duser.dir=/app"

CMD java $JAVA_OPTS \
    -Djpro.port=$JPRO_PORT \
    -Djpro.host=$JPRO_HOST \
    -Djpro.websockets.port=$JPRO_WEBSOCKETS_PORT \
    -Djpro.http.port=$JPRO_HTTP_PORT \
    -jar app.jar