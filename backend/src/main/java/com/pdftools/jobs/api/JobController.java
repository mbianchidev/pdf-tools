package com.pdftools.jobs.api;

import com.pdftools.api.ApiException;
import com.pdftools.jobs.JobEventService;
import com.pdftools.jobs.JobService;
import com.pdftools.jobs.persistence.JobOutputEntity;
import com.pdftools.storage.StorageService;
import com.pdftools.storage.StoredResource;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private static final int MAX_OPERATION_BYTES = 64;
    private static final int MAX_OPTIONS_BYTES = 65_536;

    private final JobService jobService;
    private final JobEventService eventService;
    private final StorageService storageService;

    public JobController(
            JobService jobService,
            JobEventService eventService,
            StorageService storageService) {
        this.jobService = jobService;
        this.eventService = eventService;
        this.storageService = storageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> create(
            HttpServletRequest request,
            @RequestParam("files") List<MultipartFile> files) {
        String operation = readTextPart(request, "operation", MAX_OPERATION_BYTES, true);
        String options = readTextPart(request, "options", MAX_OPTIONS_BYTES, false);
        JobResponse job = jobService.create(operation, options, files);
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .location(URI.create("/api/v1/jobs/" + job.id()))
            .body(job);
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@PathVariable UUID jobId) {
        return jobService.get(jobId);
    }

    @GetMapping("/{jobId}/events")
    public ResponseEntity<?> events(@PathVariable UUID jobId) {
        try {
            jobService.get(jobId);
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND
                    || exception.getStatus() == HttpStatus.GONE) {
                return ResponseEntity.status(exception.getStatus())
                    .header("X-Error-Code", exception.getCode())
                    .build();
            }
            throw exception;
        }
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(eventService.subscribe(jobId));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<JobResponse> cancel(@PathVariable UUID jobId) {
        return ResponseEntity.accepted().body(jobService.cancel(jobId));
    }

    @GetMapping("/{jobId}/outputs/{outputId}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable UUID jobId,
            @PathVariable UUID outputId) {
        JobOutputEntity output = jobService.getOutput(jobId, outputId);
        StreamingResponseBody body = response -> {
            try (StoredResource resource = storageService.get(output.getStorageKey())) {
                resource.inputStream().transferTo(response);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(output.getMediaType()));
        headers.setContentLength(output.getSizeBytes());
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(output.getFilename(), StandardCharsets.UTF_8)
            .build());
        headers.setCacheControl("private, no-store");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private String readTextPart(
            HttpServletRequest request,
            String name,
            int maxBytes,
            boolean required) {
        Part part;
        try {
            part = request.getPart(name);
        } catch (IOException | ServletException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_MULTIPART_REQUEST",
                "The multipart upload could not be processed"
            );
        }
        if (part == null) {
            if (required) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MISSING_MULTIPART_PART",
                    "Missing required multipart field: " + name
                );
            }
            return null;
        }
        if (part.getSize() > maxBytes) {
            throw new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                name.equals("operation") ? "OPERATION_TOO_LARGE" : "OPTIONS_TOO_LARGE",
                "Multipart field " + name + " exceeds its size limit"
            );
        }

        try (InputStream input = part.getInputStream()) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    name.equals("operation") ? "OPERATION_TOO_LARGE" : "OPTIONS_TOO_LARGE",
                    "Multipart field " + name + " exceeds its size limit"
                );
            }
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_MULTIPART_TEXT",
                "Multipart field " + name + " must contain valid UTF-8"
            );
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_MULTIPART_REQUEST",
                "Multipart field " + name + " could not be read"
            );
        }
    }
}
