FROM maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY google-credentials.json ./

RUN mvn clean package -DskipTests

FROM amazoncorretto:17-al2-jdk

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

COPY --from=builder /app/target/speakAI-*.jar app.jar
COPY --from=builder /app/src/main/resources /app/resources
COPY --from=builder /app/google-credentials.json /app/

RUN mkdir -p /app/data /app/recordings /app/logs /app/exports /app/tmp /app/models

RUN curl -L https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -o /tmp/model.zip && \
    unzip /tmp/model.zip -d /tmp/ && \
    mkdir -p /app/models/vosk-model-small-en && \
    mv /tmp/vosk-model-small-en-us-0.15/* /app/models/vosk-model-small-en/ && \
    rm -rf /tmp/model.zip /tmp/vosk-model-small-en-us-0.15

RUN echo "/app/app.jar" > /app/jprocp-file

RUN echo "#!/bin/bash" > /app/start.sh && \
    echo "" >> /app/start.sh && \
    echo "if [ ! -f /app/jprocp-file ]; then" >> /app/start.sh && \
    echo "    echo \"Creating jprocp-file...\"" >> /app/start.sh && \
    echo "    echo \"/app/app.jar\" > /app/jprocp-file" >> /app/start.sh && \
    echo "fi" >> /app/start.sh && \
    echo "" >> /app/start.sh && \
    echo "echo \"Starting JPro with application: com.mygitgor.JProWebApp\"" >> /app/start.sh && \
    echo "" >> /app/start.sh && \
    echo "exec java \\" >> /app/start.sh && \
    echo "    --add-opens=java.base/java.lang=ALL-UNNAMED \\" >> /app/start.sh && \
    echo "    --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \\" >> /app/start.sh && \
    echo "    --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \\" >> /app/start.sh && \
    echo "    --add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \\" >> /app/start.sh && \
    echo "    --add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED \\" >> /app/start.sh && \
    echo "    -Djava.awt.headless=true \\" >> /app/start.sh && \
    echo "    -Dprism.order=sw \\" >> /app/start.sh && \
    echo "    -Dprism.verbose=false \\" >> /app/start.sh && \
    echo "    -Duser.dir=/app \\" >> /app/start.sh && \
    echo "    -Djpro.port=8080 \\" >> /app/start.sh && \
    echo "    -Djpro.host=0.0.0.0 \\" >> /app/start.sh && \
    echo "    -Djpro.http.port=8080 \\" >> /app/start.sh && \
    echo "    -Djpro.deployment=MAVEN-Normal \\" >> /app/start.sh && \
    echo "    -Djpro.mode=dev \\" >> /app/start.sh && \
    echo "    -Djpro.applications.default=com.mygitgor.JProWebApp \\" >> /app/start.sh && \
    echo "    -Djprocpfile=/app/jprocp-file \\" >> /app/start.sh && \
    echo "    -cp /app/app.jar \\" >> /app/start.sh && \
    echo "    com.jpro.boot.JProBoot" >> /app/start.sh && \
    chmod +x /app/start.sh

EXPOSE 8080

CMD ["/app/start.sh"]