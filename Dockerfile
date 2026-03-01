FROM maven:3.9-amazoncorretto-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY google-credentials.json ./

RUN mvn clean package -DskipTests

FROM maven:3.9-amazoncorretto-21

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

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp /app/models

RUN if [ ! -d "/app/models/vosk-model-small-en" ]; then \
    curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip && \
    unzip /tmp/model.zip -d /tmp/ && \
    mkdir -p /app/models/vosk-model-small-en && \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/ && \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15; \
    fi

EXPOSE 8080

CMD mvn jpro:run -DskipTests -Dhttp.port=8080 -Djpro.host=0.0.0.0