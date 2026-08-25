package com.pdftools.operations.office;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class OfficeDocumentValidator {

    private static final int MAX_DECLARATION_BYTES = 1024 * 1024;

    private final OfficeConversionProperties properties;

    public OfficeDocumentValidator(
            OfficeConversionProperties properties) {
        this.properties = properties;
    }

    public void validateOoxml(
            Path source,
            Profile profile,
            Runnable cancellationCheck) {
        int entries = 0;
        long expandedBytes = 0;
        boolean contentTypes = false;
        boolean documentPart = false;
        byte[] buffer = new byte[8192];
        try (ZipInputStream archive = new ZipInputStream(
                Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                cancellationCheck.run();
                entries++;
                if (entries > properties.getMaxArchiveEntries()) {
                    throw invalid(profile);
                }
                String name = normalizedEntryName(
                    entry.getName(),
                    profile
                );
                if (name.equals("[Content_Types].xml")) {
                    contentTypes = true;
                }
                if (name.equals(profile.requiredPart())) {
                    documentPart = true;
                }
                if (name.equalsIgnoreCase(profile.macroPart())) {
                    throw new OperationException(
                        profile.macrosCode(),
                        "Macro-enabled " + profile.label()
                            + " files are not supported"
                    );
                }
                ByteArrayOutputStream declaration =
                    declarationEntry(name)
                        ? new ByteArrayOutputStream()
                        : null;
                int read;
                while ((read = archive.read(buffer)) != -1) {
                    if (read > 0) {
                        expandedBytes = addExpandedBytes(
                            expandedBytes,
                            read,
                            profile
                        );
                        if (declaration != null) {
                            if (declaration.size()
                                    > MAX_DECLARATION_BYTES - read) {
                                throw invalid(profile);
                            }
                            declaration.write(buffer, 0, read);
                        }
                    }
                    cancellationCheck.run();
                }
                if (declaration != null
                        && declaresMacros(declaration, profile)) {
                    throw new OperationException(
                        profile.macrosCode(),
                        "Macro-enabled " + profile.label()
                            + " files are not supported"
                    );
                }
                archive.closeEntry();
            }
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new OperationException(
                profile.invalidCode(),
                "The file is not a readable " + profile.label(),
                exception
            );
        }
        if (!contentTypes || !documentPart) {
            throw invalid(profile);
        }
    }

    public void validateOle(Path source, Profile profile) {
        try (InputStream input = Files.newInputStream(source);
             POIFSFileSystem filesystem = new POIFSFileSystem(input)) {
            if (!filesystem.getRoot().hasEntry(
                        profile.legacyStream()
                    )) {
                throw invalid(profile);
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(
                profile.invalidCode(),
                "The file is not a readable " + profile.label(),
                exception
            );
        }
    }

    private boolean declarationEntry(String name) {
        return name.equals("[Content_Types].xml")
            || name.endsWith(".rels");
    }

    private boolean declaresMacros(
            ByteArrayOutputStream declaration,
            Profile profile) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(
            "javax.xml.stream.isSupportingExternalEntities",
            false
        );
        factory.setProperty(
            XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES,
            true
        );
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(
                new java.io.ByteArrayInputStream(
                    declaration.toByteArray()
                ),
                StandardCharsets.UTF_8.name()
            );
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        if (forbidden(reader.getLocalName())) {
                            return true;
                        }
                        for (int index = 0;
                                index < reader.getAttributeCount();
                                index++) {
                            if (forbidden(reader.getAttributeValue(index))) {
                                return true;
                            }
                        }
                    } else if ((event == XMLStreamConstants.CHARACTERS
                            || event == XMLStreamConstants.CDATA)
                            && forbidden(reader.getText())) {
                        return true;
                    }
                }
                return false;
            } finally {
                reader.close();
            }
        } catch (XMLStreamException exception) {
            throw new OperationException(
                profile.invalidCode(),
                "The " + profile.label()
                    + " package declarations are invalid",
                exception
            );
        }
    }

    private boolean forbidden(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("vbaproject")
            || normalized.contains("macroenabled");
    }

    private String normalizedEntryName(
            String name,
            Profile profile) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || java.util.Arrays.stream(normalized.split("/"))
                    .anyMatch(".."::equals)) {
            throw invalid(profile);
        }
        return normalized;
    }

    private long addExpandedBytes(
            long current,
            int read,
            Profile profile) {
        long next;
        try {
            next = Math.addExact(current, read);
        } catch (ArithmeticException exception) {
            throw expandedLimit(profile);
        }
        if (next > properties.getMaxExpandedInputBytes()) {
            throw expandedLimit(profile);
        }
        return next;
    }

    private OperationException expandedLimit(Profile profile) {
        return new OperationException(
            profile.expandedCode(),
            "The expanded " + profile.label()
                + " content exceeds the configured limit"
        );
    }

    private OperationException invalid(Profile profile) {
        return new OperationException(
            profile.invalidCode(),
            "The file is not a valid " + profile.label()
        );
    }

    public record Profile(
        String label,
        String requiredPart,
        String macroPart,
        String legacyStream,
        String invalidCode,
        String expandedCode,
        String macrosCode
    ) {
    }
}
