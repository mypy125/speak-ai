FROM maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

COPY google-credentials.json ./

RUN mvn clean package jpro:package -DskipTests

FROM maven:3.9-amazoncorretto-17

RUN yum update -y && \
    yum install -y \
    mesa-libGL \
    mesa-libGLU \
    libXrender \
    libXtst \
    libXi \
    libXrandr \
    alsa-lib \
    fontconfig \
    freetype \
    curl \
    unzip \
    && yum clean all

WORKDIR /app

COPY --from=builder /app /app

RUN if [ ! -f /app/google-credentials.json ]; then \
    echo "Warning: google-credentials.json not found. Creating empty file."; \
    echo '{}' > /app/google-credentials.json; \
    fi

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp /app/models

RUN if [ ! -d "/app/models/vosk-model-small-en" ]; then \
    curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip && \
    unzip /tmp/model.zip -d /tmp/ && \
    mkdir -p /app/models/vosk-model-small-en && \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/ && \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15; \
    fi

EXPOSE 8080

ENV JPRO_PORT=8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_HTTP_PORT=8080
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/google-credentials.json
ENV JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED --add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED --add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED -Djava.awt.headless=true -Dprism.order=sw -Dprism.verbose=false -Duser.dir=/app"

CMD mvn jpro:run -DskipTests \
    -Dhttp.port=$JPRO_PORT \
    -Djpro.host=$JPRO_HOST \
    -Djpro.http.port=$JPRO_HTTP_PORT