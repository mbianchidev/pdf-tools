package com.pdftools.operations.wordpdf;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.office.OfficeConversionProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class WordDocumentValidator {

    private static final byte[] OLE_HEADER = {
        (byte) 0xD0,
        (byte) 0xCF,
        0x11,
        (byte) 0xE0,
        (byte) 0xA1,
        (byte) 0xB1,
        0x1A,
        (byte) 0xE1
    };
    private static final Set<String> WORD_MEDIA_TYPES = Set.of(
        "application/msword",
        "application/vnd.openxmlformats-officedocument."
            + "wordprocessingml.document",
        "application/octet-stream"
    );

    private final OfficeConversionProperties properties;

    public WordDocumentValidator(
            OfficeConversionProperties properties) {
        this.properties = properties;
    }

    public void validateSubmission(OperationSubmission submission) {
        if (submission.files().size() != 1) {
            throw new OperationException(
                "INVALID_FILE_COUNT",
                "Word to PDF requires exactly one Word document"
            );
        }
        OperationSubmission.UploadDescriptor file =
            submission.files().getFirst();
        String lowerName = file.filename().toLowerCase(Locale.ROOT);
        if ((!lowerName.endsWith(".docx") && !lowerName.endsWith(".doc"))
                || !WORD_MEDIA_TYPES.contains(
                    file.mediaType().toLowerCase(Locale.ROOT))) {
            throw new OperationException(
                "INVALID_WORD_FILE",
                "Word to PDF accepts DOCX and DOC files"
            );
        }
        if (file.sizeBytes() < 1
                || file.sizeBytes() > properties.getMaxInputBytes()) {
            throw new OperationException(
                "WORD_INPUT_SIZE_LIMIT_EXCEEDED",
                "The Word document exceeds the configured input limit"
            );
        }
    }

    public void validate(
            Path source,
            String originalFilename,
            Runnable cancellationCheck) {
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".docx")) {
            validateDocx(source, cancellationCheck);
            return;
        }
        if (lowerName.endsWith(".doc")) {
            validateLegacyDoc(source);
            return;
        }
        throw invalidDocument();
    }

    private void validateDocx(
            Path source,
            Runnable cancellationCheck) {
        int entries = 0;
        long expandedBytes = 0;
        boolean contentTypes = false;
        boolean documentXml = false;
        byte[] buffer = new byte[8192];
        try (ZipInputStream archive = new ZipInputStream(
                Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                cancellationCheck.run();
                entries++;
                if (entries > properties.getMaxArchiveEntries()) {
                    throw invalidDocument();
                }
                String name = normalizedEntryName(entry.getName());
                if (name.equals("[Content_Types].xml")) {
                    contentTypes = true;
                }
                if (name.equals("word/document.xml")) {
                    documentXml = true;
                }
                if (name.equalsIgnoreCase("word/vbaProject.bin")) {
                    throw new OperationException(
                        "WORD_MACROS_NOT_SUPPORTED",
                        "Macro-enabled Word documents are not supported"
                    );
                }
                int read;
                while ((read = archive.read(buffer)) != -1) {
                    if (read > 0) {
                        expandedBytes = addExpandedBytes(
                            expandedBytes,
                            read
                        );
                    }
                    cancellationCheck.run();
                }
                archive.closeEntry();
            }
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new OperationException(
                "INVALID_WORD_DOCUMENT",
                "The DOCX file is not a readable Word document",
                exception
            );
        }
        if (!contentTypes || !documentXml) {
            throw invalidDocument();
        }
    }

    private String normalizedEntryName(String name) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || java.util.Arrays.stream(normalized.split("/"))
                    .anyMatch(".."::equals)) {
            throw invalidDocument();
        }
        return normalized;
    }

    private long addExpandedBytes(long current, int read) {
        long next;
        try {
            next = Math.addExact(current, read);
        } catch (ArithmeticException exception) {
            throw expandedLimit();
        }
        if (next > properties.getMaxExpandedInputBytes()) {
            throw expandedLimit();
        }
        return next;
    }

    private void validateLegacyDoc(Path source) {
        try (InputStream input = Files.newInputStream(source)) {
            byte[] header = input.readNBytes(OLE_HEADER.length);
            if (!java.util.Arrays.equals(header, OLE_HEADER)) {
                throw invalidDocument();
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_WORD_DOCUMENT",
                "The DOC file is not a readable Word document",
                exception
            );
        }
    }

    private OperationException expandedLimit() {
        return new OperationException(
            "WORD_EXPANDED_SIZE_LIMIT_EXCEEDED",
            "The expanded DOCX content exceeds the configured limit"
        );
    }

    private OperationException invalidDocument() {
        return new OperationException(
            "INVALID_WORD_DOCUMENT",
            "The file is not a valid Word document"
        );
    }
}
