package com.pdftools.operations;

import com.pdftools.util.FilenameSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ZipArtifactService {

    private static final Logger logger = LoggerFactory.getLogger(ZipArtifactService.class);

    public OperationOutput create(
            List<OperationOutput> outputs,
            Path destination,
            String filename) {
        return create(
            outputs,
            destination,
            filename,
            Long.MAX_VALUE,
            () -> {
            },
            false
        );
    }

    public OperationOutput create(
            List<OperationOutput> outputs,
            Path destination,
            String filename,
            long maxBytes,
            Runnable cancellationCheck,
            boolean deleteSources) {
        if (outputs.isEmpty()) {
            throw new OperationException("EMPTY_ZIP", "A ZIP artifact requires at least one output");
        }

        try (OutputStream fileOutput = Files.newOutputStream(destination);
             BoundedOutputStream boundedOutput = new BoundedOutputStream(
                 fileOutput,
                 maxBytes,
                 cancellationCheck
             );
             ZipOutputStream zip = new ZipOutputStream(
                 new BufferedOutputStream(boundedOutput)
             )) {
            Set<String> usedNames = new HashSet<>();
            byte[] buffer = new byte[64 * 1024];
            for (OperationOutput output : outputs) {
                cancellationCheck.run();
                String sanitized = FilenameSanitizer.sanitize(output.filename(), "output");
                String entryName = uniqueEntryName(sanitized, usedNames);
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                try (BufferedInputStream input = new BufferedInputStream(
                        Files.newInputStream(output.path()))) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            cancellationCheck.run();
                            zip.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
                if (deleteSources) {
                    Files.deleteIfExists(output.path());
                }
            }
        } catch (OutputLimitExceededException exception) {
            deletePartialZip(destination);
            throw new OperationException(
                "ZIP_SIZE_LIMIT_EXCEEDED",
                "ZIP output exceeds the configured size limit"
            );
        } catch (OperationCancelledException exception) {
            deletePartialZip(destination);
            throw exception;
        } catch (IOException exception) {
            deletePartialZip(destination);
            throw new OperationException("ZIP_CREATION_FAILED", "Failed to create ZIP artifact", exception);
        }

        return new OperationOutput(destination, filename, "application/zip");
    }

    private void deletePartialZip(Path destination) {
        try {
            Files.deleteIfExists(destination);
        } catch (IOException exception) {
            logger.warn("Failed to remove partial ZIP {}", destination, exception);
        }
    }

    private String uniqueEntryName(String filename, Set<String> usedNames) {
        String candidate = filename;
        int suffix = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = FilenameSanitizer.withSuffix(filename, "-" + suffix);
            suffix++;
        }
        return candidate;
    }
}
