FROM maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY google-credentials.json ./

RUN mvn clean package -DskipTests

RUN mvn jpro:package -DskipTests

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
    curl \
    unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/speakAI-*.jar app.jar
COPY --from=builder /app/target/jpro /app/jpro

RUN ln -s /app/jpro/jprocp-file /app/jprocp-file

COPY --from=builder /app/src/main/resources /app/resources

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp /app/models

RUN curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip && \
    unzip /tmp/model.zip -d /tmp/ && \
    mkdir -p /app/models/vosk-model-small-en && \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/ && \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15

RUN if [ ! -f /app/google-credentials.json ]; then \
    echo "Warning: google-credentials.json not found. Creating empty file."; \
    echo '{}' > /app/google-credentials.json; \
    fi

EXPOSE 8080

ENV JPRO_PORT=8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_HTTP_PORT=8080
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/google-credentials.json
ENV JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED --add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED --add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED -Djava.awt.headless=true -Dprism.order=sw -Dprism.verbose=false -Duser.dir=/app"

CMD java $JAVA_OPTS \
    -Djpro.port=$JPRO_PORT \
    -Djpro.host=$JPRO_HOST \
    -Djpro.http.port=$JPRO_HTTP_PORT \
    -Djpro.deployment=MAVEN-Normal \
    -Djpro.mode=dev \
    -Djprocpfile=/app/jprocp-file \
    -cp app.jar \
    com.jpro.boot.JProBoot