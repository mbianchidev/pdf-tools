package com.pdftools.operations;

import com.pdftools.util.FilenameSanitizer;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
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

    public OperationOutput create(
            List<OperationOutput> outputs,
            Path destination,
            String filename) {
        if (outputs.isEmpty()) {
            throw new OperationException("EMPTY_ZIP", "A ZIP artifact requires at least one output");
        }

        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(destination)))) {
            Set<String> usedNames = new HashSet<>();
            for (OperationOutput output : outputs) {
                String sanitized = FilenameSanitizer.sanitize(output.filename(), "output");
                String entryName = uniqueEntryName(sanitized, usedNames);
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                try (BufferedInputStream input = new BufferedInputStream(
                        Files.newInputStream(output.path()))) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new OperationException("ZIP_CREATION_FAILED", "Failed to create ZIP artifact", exception);
        }

        return new OperationOutput(destination, filename, "application/zip");
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
