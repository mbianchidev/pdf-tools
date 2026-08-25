FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        fonts-dejavu-core \
        fonts-liberation \
        gosu \
        libreoffice-calc \
        libreoffice-impress \
        libreoffice-writer \
        util-linux \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 pdftools \
    && useradd --system --uid 10001 --gid pdftools --home-dir /app pdftools \
    && groupadd --system --gid 10002 officeworker \
    && useradd --system --uid 10002 --gid officeworker \
        --home-dir /nonexistent officeworker
COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/pdf-tools-entrypoint
RUN mkdir -p \
        /tmp/pdf-storage \
        /tmp/pdf-work \
        /tmp/pdf-multipart \
        /var/lib/pdf-tools-office/requests \
        /var/lib/pdf-tools-office/responses \
        /var/lib/pdf-tools-office/signals \
        /var/lib/pdf-tools-html/requests \
        /var/lib/pdf-tools-html/responses \
        /var/lib/pdf-tools-html/signals \
    && chown -R pdftools:pdftools \
        /app \
        /tmp/pdf-storage \
        /tmp/pdf-work \
        /tmp/pdf-multipart \
        /var/lib/pdf-tools-office \
        /var/lib/pdf-tools-html \
    && chmod 0700 /tmp/pdf-multipart \
    && chmod 0755 /usr/local/bin/pdf-tools-entrypoint
EXPOSE 8080
ENTRYPOINT ["pdf-tools-entrypoint"]
