FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY src ./src

ARG KOTLIN_VERSION=1.9.24
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL -o /tmp/kotlin.zip https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip \
    && unzip -q /tmp/kotlin.zip -d /opt \
    && rm /tmp/kotlin.zip

RUN /opt/kotlinc/bin/kotlinc src/*.kt -include-runtime -d app.jar

FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar
COPY README.md /app/README.md

ENV PORT=8080
EXPOSE 8080

CMD ["/app/app.jar"]
