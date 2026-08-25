package com.pdftools.jobs.api;

import com.pdftools.jobs.persistence.JobEntity;
import com.pdftools.jobs.persistence.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.servlet.multipart.max-file-size=100KB",
        "spring.servlet.multipart.max-request-size=100KB",
        "server.tomcat.max-part-count=110",
        "pdf.jobs.max-files=100",
        "pdf.jobs.enabled-operations=jpg-to-pdf"
    }
)
class JobApiContractIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void rejectsUnsupportedContentTypesWithAClientError() throws Exception {
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(415, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"UNSUPPORTED_MEDIA_TYPE\""));
    }

    @Test
    void reportsMissingMultipartFilesAsStructuredBadRequests() throws Exception {
        String boundary = "pdf-tools-boundary";
        byte[] body = multipartField(boundary, "operation", "merge");
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"MISSING_PARAMETER\"")
            || response.body().contains("\"code\":\"MISSING_MULTIPART_PART\""));
    }

    @Test
    void returnsNotFoundBeforeOpeningAnEventStream() throws Exception {
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs/" + UUID.randomUUID() + "/events"))
                .header("Accept", "text/event-stream")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(404, response.statusCode());
    }

    @Test
    void rejectsUnacceptableResponseTypesWithoutA500() throws Exception {
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs/" + UUID.randomUUID()))
                .header("Accept", "application/xml")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(406, response.statusCode());
    }

    @Test
    void returnsStructuredMethodAndRouteErrors() throws Exception {
        HttpResponse<String> methodResponse = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        HttpResponse<String> routeResponse = client.send(
            HttpRequest.newBuilder(uri("/api/v1/does-not-exist"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(405, methodResponse.statusCode());
        assertTrue(methodResponse.body().contains("\"code\":\"METHOD_NOT_ALLOWED\""));
        assertEquals(404, routeResponse.statusCode());
        assertTrue(routeResponse.body().contains("\"code\":\"ROUTE_NOT_FOUND\""));
    }

    @Test
    void replaysTheLatestTerminalStateToNewEventSubscribers() throws Exception {
        Instant now = Instant.now();
        JobEntity job = JobEntity.pending("test", "{}", now, now.plus(Duration.ofHours(2)));
        job.start(now.plusMillis(1));
        job.complete(now.plusMillis(2), now.plus(Duration.ofHours(2)));
        jobRepository.saveAndFlush(job);

        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs/" + job.getId() + "/events"))
                .header("Accept", "text/event-stream")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"COMPLETED\""));
    }

    @Test
    void distinguishesExpiredJobsBeforeOpeningAnEventStream() throws Exception {
        Instant now = Instant.now();
        JobEntity job = JobEntity.pending("test", "{}", now.minusSeconds(10), now.minusSeconds(1));
        job.start(now.minusSeconds(5));
        job.complete(now.minusSeconds(4), now.minusSeconds(1));
        jobRepository.saveAndFlush(job);

        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs/" + job.getId() + "/events"))
                .header("Accept", "text/event-stream")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(410, response.statusCode());
        assertEquals(
            "JOB_EXPIRED",
            response.headers().firstValue("X-Error-Code").orElseThrow()
        );
        jobRepository.deleteById(job.getId());
    }

    @Test
    void preservesTheLegacyErrorEnvelope() throws Exception {
        String boundary = "legacy-boundary";
        byte[] body = multipartFile(
            boundary,
            "file",
            "invalid.pdf",
            "application/pdf",
            "not a PDF".getBytes(StandardCharsets.UTF_8)
        );
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/api/pdf/info"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"success\":false"));
        assertFalse(response.body().contains("\"code\":"));
    }

    @Test
    void returnsPathSpecificEnvelopesForOversizedUploads() throws Exception {
        String v1Boundary = "large-v1-boundary";
        HttpResponse<String> v1Response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs"))
                .header("Content-Type", "multipart/form-data; boundary=" + v1Boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                    multipartJob(v1Boundary, new byte[150_000])
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        String legacyBoundary = "large-legacy-boundary";
        HttpResponse<String> legacyResponse = client.send(
            HttpRequest.newBuilder(uri("/api/pdf/info"))
                .header("Content-Type", "multipart/form-data; boundary=" + legacyBoundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                    multipartFile(
                        legacyBoundary,
                        "file",
                        "large.pdf",
                        "application/pdf",
                        new byte[150_000]
                    )
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(413, v1Response.statusCode());
        assertTrue(v1Response.body().contains("\"code\":\"UPLOAD_TOO_LARGE\""));
        assertEquals(413, legacyResponse.statusCode());
        assertTrue(legacyResponse.body().contains("\"success\":false"));
    }

    @Test
    void boundsMultipartMetadataBeforeDecodingIt() throws Exception {
        String operationBoundary = "large-operation-boundary";
        HttpResponse<String> operationResponse = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs"))
                .header("Content-Type", "multipart/form-data; boundary=" + operationBoundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                    multipartJob(operationBoundary, "m".repeat(65), null, new byte[]{1})
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        String optionsBoundary = "large-options-boundary";
        HttpResponse<String> optionsResponse = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs"))
                .header("Content-Type", "multipart/form-data; boundary=" + optionsBoundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                    multipartJob(
                        optionsBoundary,
                        "merge",
                        "x".repeat(65_537),
                        new byte[]{1}
                    )
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(413, operationResponse.statusCode());
        assertTrue(operationResponse.body().contains("\"code\":\"OPERATION_TOO_LARGE\""));
        assertEquals(413, optionsResponse.statusCode());
        assertTrue(optionsResponse.body().contains("\"code\":\"OPTIONS_TOO_LARGE\""));
    }

    @Test
    void acceptsOneHundredFilePartsAtTheConnectorBoundary()
            throws Exception {
        String boundary = "many-jpg-parts";
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(uri("/api/v1/jobs"))
                .header(
                    "Content-Type",
                    "multipart/form-data; boundary=" + boundary
                )
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                    multipartJpgJob(boundary, 100)
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(422, response.statusCode(), response.body());
        assertTrue(
            response.body().contains(
                "\"code\":\"INVALID_JPG_PDF_MARGIN\""
            )
        );
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private byte[] multipartField(String boundary, String name, String value) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write((
                "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            body.write(value.getBytes(StandardCharsets.UTF_8));
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return body.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] multipartFile(
            String boundary,
            String field,
            String filename,
            String mediaType,
            byte[] content) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write((
                "Content-Disposition: form-data; name=\"" + field
                    + "\"; filename=\"" + filename + "\"\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            body.write(("Content-Type: " + mediaType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
            body.write(content);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return body.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] multipartJob(String boundary, byte[] content) {
        return multipartJob(boundary, "merge", null, content);
    }

    private byte[] multipartJob(
            String boundary,
            String operation,
            String options,
            byte[] content) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(
                ("Content-Disposition: form-data; name=\"operation\"\r\n\r\n"
                    + operation + "\r\n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            if (options != null) {
                body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                body.write(
                    ("Content-Disposition: form-data; name=\"options\"\r\n\r\n"
                        + options + "\r\n")
                        .getBytes(StandardCharsets.UTF_8)
                );
            }
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(
                "Content-Disposition: form-data; name=\"files\"; filename=\"large.pdf\"\r\n"
                    .getBytes(StandardCharsets.UTF_8)
            );
            body.write("Content-Type: application/pdf\r\n\r\n"
                .getBytes(StandardCharsets.UTF_8));
            body.write(content);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return body.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] multipartJpgJob(String boundary, int fileCount) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
            body.write(
                ("Content-Disposition: form-data; name=\"operation\"\r\n\r\n"
                    + "jpg-to-pdf\r\n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            body.write(("--" + boundary + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
            body.write(
                ("Content-Disposition: form-data; name=\"options\"\r\n\r\n"
                    + "{\"margin\":145}\r\n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            for (int index = 0; index < fileCount; index++) {
                body.write(("--" + boundary + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
                body.write(
                    ("Content-Disposition: form-data; name=\"files\"; "
                        + "filename=\"image-" + index + ".jpg\"\r\n")
                        .getBytes(StandardCharsets.UTF_8)
                );
                body.write("Content-Type: image/jpeg\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
                body.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
                body.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            body.write(("--" + boundary + "--\r\n")
                .getBytes(StandardCharsets.UTF_8));
            return body.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
