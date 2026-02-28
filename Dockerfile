FROM maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY google-credentials.json ./

RUN mvn clean package -DskipTests

RUN mkdir -p /app/target/jpro-libs
RUN cp /root/.m2/repository/one/jpro/jpro-*/**/*.jar /app/target/jpro-libs/ 2>/dev/null || true
RUN cp /root/.m2/repository/one/jpro/platform/jpro-*/**/*.jar /app/target/jpro-libs/ 2>/dev/null || true

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
COPY --from=builder /app/target/jpro-libs /app/jpro-libs

COPY --from=builder /app/src/main/resources /app/resources
COPY --from=builder /app/google-credentials.json /app/

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp /app/models

RUN curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip && \
    unzip /tmp/model.zip -d /tmp/ && \
    mkdir -p /app/models/vosk-model-small-en && \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/ && \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15

RUN echo '#!/bin/bash\n\
CP="/app/app.jar"\n\
for jar in /app/jpro-libs/*.jar; do\n\
  CP="$CP:$jar"\n\
done\n\
java $JAVA_OPTS \
    -Djpro.port=$JPRO_PORT \
    -Djpro.host=$JPRO_HOST \
    -Djpro.http.port=$JPRO_HTTP_PORT \
    -Djpro.deployment=MAVEN-Normal \
    -Djpro.mode=dev \
    -cp "$CP" \
    com.jpro.boot.JProBoot' > /app/start.sh && chmod +x /app/start.sh

EXPOSE 8080

ENV JPRO_PORT=8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_HTTP_PORT=8080
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/google-credentials.json
ENV JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED --add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED --add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED -Djava.awt.headless=true -Dprism.order=sw -Dprism.verbose=false -Duser.dir=/app"

CMD /app/start.sh