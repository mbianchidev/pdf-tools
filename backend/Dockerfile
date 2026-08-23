FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gosu \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 pdftools \
    && useradd --system --uid 10001 --gid pdftools --home-dir /app pdftools
COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/pdf-tools-entrypoint
RUN mkdir -p /tmp/pdf-storage /tmp/pdf-work /tmp/pdf-multipart \
    && chown -R pdftools:pdftools /app /tmp/pdf-storage /tmp/pdf-work /tmp/pdf-multipart \
    && chmod 0755 /usr/local/bin/pdf-tools-entrypoint
EXPOSE 8080
ENTRYPOINT ["pdf-tools-entrypoint"]
