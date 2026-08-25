package com.pdftools.operations.wordpdf;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.office.NativeProcessSandbox;
import com.pdftools.operations.office.OfficeConversionQueueClient;
import com.pdftools.operations.office.OfficeConversionProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WordToPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OfficeConversionProperties properties = properties();
    private final WordDocumentValidator validator =
        new WordDocumentValidator(properties);
    private final WordToPdfOperation operation = new WordToPdfOperation(
        new WordToPdfConverter(
            properties,
            new LibreOfficeWordConverter(
                properties,
                validator,
                new NativeProcessSandbox()
            ),
            new OfficeConversionQueueClient(properties)
        ),
        validator
    );

    @Test
    void convertsTextTablesImagesAndPaginationWithLibreOffice()
            throws Exception {
        Path source = docx();

        OperationOutput output = operation.execute(context(source, "{}"))
            .getFirst();

        assertEquals("source.pdf", output.filename());
        assertEquals("application/pdf", output.mediaType());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("WORD TO PDF FIXTURE"));
            assertTrue(text.contains("Cell A"));
            assertTrue(text.contains("SECOND PAGE"));
            assertTrue(document.getNumberOfPages() >= 2);
            BufferedImage rendered = new PDFRenderer(document).renderImage(0);
            try {
                assertTrue(containsBluePixel(rendered));
            } finally {
                rendered.flush();
            }
        }
    }

    @Test
    void validatesSubmissionAndMalformedDocxContainers() throws Exception {
        OperationSubmission.UploadDescriptor valid =
            new OperationSubmission.UploadDescriptor(
                1,
                "source.docx",
                "application/vnd.openxmlformats-officedocument."
                    + "wordprocessingml.document",
                100
            );
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{}"),
            List.of(valid)
        ));
        assertSubmissionCode(
            "INVALID_WORD_FILE",
            new OperationSubmission.UploadDescriptor(
                1,
                "source.xlsx",
                "application/octet-stream",
                100
            )
        );
        assertSubmissionCode(
            "WORD_INPUT_SIZE_LIMIT_EXCEEDED",
            new OperationSubmission.UploadDescriptor(
                1,
                "source.docx",
                "application/octet-stream",
                properties.getMaxInputBytes() + 1
            )
        );

        assertCode("INVALID_WORD_DOCUMENT", invalidDocx("missing.docx", false));
        assertCode("INVALID_WORD_DOCUMENT", invalidDocx("traversal.docx", true));

        OfficeConversionProperties expanded = properties();
        expanded.setMaxExpandedInputBytes(32);
        WordDocumentValidator expandedValidator =
            new WordDocumentValidator(expanded);
        OperationException expandedFailure = assertThrows(
            OperationException.class,
            () -> expandedValidator.validate(
                docx(),
                "source.docx",
                () -> {
                }
            )
        );
        assertEquals(
            "WORD_EXPANDED_SIZE_LIMIT_EXCEEDED",
            expandedFailure.getCode()
        );
    }

    @Test
    void enforcesOutputAndWallTimeLimits() throws Exception {
        OfficeConversionProperties smallOutput = properties();
        smallOutput.setMaxOutputBytes(100);
        WordDocumentValidator smallValidator =
            new WordDocumentValidator(smallOutput);
        WordToPdfOperation smallOperation = new WordToPdfOperation(
            new WordToPdfConverter(
                smallOutput,
                new LibreOfficeWordConverter(
                    smallOutput,
                    smallValidator,
                    new NativeProcessSandbox()
                ),
                new OfficeConversionQueueClient(smallOutput)
            ),
            smallValidator
        );
        OperationException outputFailure = assertThrows(
            OperationException.class,
            () -> smallOperation.execute(context(docx(), "{}"))
        );
        assertEquals(
            "WORD_PDF_OUTPUT_LIMIT_EXCEEDED",
            outputFailure.getCode()
        );

        OfficeConversionProperties timed = properties();
        timed.setWallTimeout(Duration.ofNanos(1));
        WordDocumentValidator timedValidator =
            new WordDocumentValidator(timed);
        WordToPdfOperation timedOperation = new WordToPdfOperation(
            new WordToPdfConverter(
                timed,
                new LibreOfficeWordConverter(
                    timed,
                    timedValidator,
                    new NativeProcessSandbox()
                ),
                new OfficeConversionQueueClient(timed)
            ),
            timedValidator
        );
        OperationException timeout = assertThrows(
            OperationException.class,
            () -> timedOperation.execute(context(docx(), "{}"))
        );
        assertEquals("WORD_CONVERSION_TIMEOUT", timeout.getCode());
    }

    @Test
    void killsBackgroundProcessesBeforeReadingOutput() throws Exception {
        assumeTrue(System.getProperty("os.name")
            .toLowerCase(java.util.Locale.ROOT)
            .contains("linux"));
        OfficeConversionProperties detached = properties();
        Path workspace = Files.createTempDirectory(
            temporaryDirectory,
            "detached-context-"
        );
        Path converterRoot = workspace.resolve("word-converter");
        Files.createDirectories(converterRoot);
        Path executable = converterRoot.resolve("fake-soffice");
        Files.writeString(executable, """
            #!/bin/sh
            sleep 30 &
            exit 0
            """);
        Files.setPosixFilePermissions(
            executable,
            PosixFilePermissions.fromString("rwx------")
        );
        detached.setLibreOfficeBinary(executable.toString());
        WordDocumentValidator detachedValidator =
            new WordDocumentValidator(detached);
        LibreOfficeWordConverter converter =
            new LibreOfficeWordConverter(
                detached,
                detachedValidator,
                new NativeProcessSandbox()
            );
        OperationInput input = context(docx(), "{}").inputs().getFirst();

        OperationException failure = assertThrows(
            OperationException.class,
            () -> converter.convert(
                input,
                workspace,
                ignored -> {
                },
                () -> {
                }
            )
        );

        assertEquals("WORD_CONVERTER_PROCESS_LEAK", failure.getCode());
    }

    @Test
    void killsChildrenForkedByTermHandler() throws Exception {
        assumeTrue(System.getProperty("os.name")
            .toLowerCase(java.util.Locale.ROOT)
            .contains("linux"));
        OfficeConversionProperties timed = properties();
        timed.setWallTimeout(Duration.ofMillis(200));
        Path workspace = Files.createTempDirectory(
            temporaryDirectory,
            "term-context-"
        );
        Path converterRoot = workspace.resolve("word-converter");
        Files.createDirectories(converterRoot);
        Path executable = converterRoot.resolve("term-soffice");
        Files.writeString(executable, """
            #!/bin/sh
            trap '/bin/sh -c "sleep 30" word-test-term-child & exit 0' TERM
            while :; do sleep 1; done
            """);
        Files.setPosixFilePermissions(
            executable,
            PosixFilePermissions.fromString("rwx------")
        );
        timed.setLibreOfficeBinary(executable.toString());
        WordDocumentValidator timedValidator =
            new WordDocumentValidator(timed);
        LibreOfficeWordConverter converter =
            new LibreOfficeWordConverter(
                timed,
                timedValidator,
                new NativeProcessSandbox()
            );
        OperationInput input = context(docx(), "{}").inputs().getFirst();

        OperationException failure = assertThrows(
            OperationException.class,
            () -> converter.convert(
                input,
                workspace,
                ignored -> {
                },
                () -> {
                }
            )
        );

        assertEquals("WORD_CONVERSION_TIMEOUT", failure.getCode());
        Thread.sleep(100);
        assertFalse(ProcessHandle.allProcesses().anyMatch(process ->
            process.info().commandLine()
                .orElse("")
                .contains("word-test-term-child")
        ));
    }

    private OfficeConversionProperties properties() {
        OfficeConversionProperties configured =
            new OfficeConversionProperties();
        configured.setMode("direct");
        configured.setIsolatedContainer(true);
        configured.setWorkerUser(System.getProperty("user.name"));
        configured.setMaxWorkerProcesses(4096);
        configured.setLibreOfficeBinary(localSoffice());
        configured.setWallTimeout(Duration.ofMinutes(1));
        configured.setCpuTimeSeconds(60);
        return configured;
    }

    private String localSoffice() {
        for (String candidate : List.of(
                "/opt/homebrew/bin/soffice",
                "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                "/usr/bin/soffice")) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return "soffice";
    }

    private Path docx() throws Exception {
        Path source = temporaryDirectory.resolve(
            "source-" + UUID.randomUUID() + ".docx"
        );
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream imageBytes = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Title");
            heading.createRun().setText("WORD TO PDF FIXTURE");
            document.createParagraph()
                .createRun()
                .setText("Layout, table, image, and pagination.");
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Cell A");
            table.getRow(0).getCell(1).setText("Cell B");
            table.getRow(1).getCell(0).setText("Cell C");
            table.getRow(1).getCell(1).setText("Cell D");

            BufferedImage image = new BufferedImage(
                80,
                40,
                BufferedImage.TYPE_INT_RGB
            );
            var graphics = image.createGraphics();
            try {
                graphics.setColor(Color.BLUE);
                graphics.fillRect(0, 0, 80, 40);
            } finally {
                graphics.dispose();
            }
            ImageIO.write(image, "png", imageBytes);
            image.flush();
            XWPFRun imageRun = document.createParagraph().createRun();
            imageRun.addPicture(
                new ByteArrayInputStream(imageBytes.toByteArray()),
                Document.PICTURE_TYPE_PNG,
                "blue.png",
                Units.toEMU(160),
                Units.toEMU(80)
            );

            XWPFRun breakRun = document.createParagraph().createRun();
            breakRun.addBreak(BreakType.PAGE);
            document.createParagraph().createRun().setText("SECOND PAGE");
            try (var output = Files.newOutputStream(source)) {
                document.write(output);
            }
        }
        return source;
    }

    private Path invalidDocx(String name, boolean traversal)
            throws Exception {
        Path source = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(
                Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("[Content_Types].xml"));
            output.write("<Types/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            if (traversal) {
                output.putNextEntry(new ZipEntry("../outside"));
                output.write(1);
                output.closeEntry();
            }
        }
        return source;
    }

    private void assertSubmissionCode(
            String code,
            OperationSubmission.UploadDescriptor descriptor) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree("{}"),
                List.of(descriptor)
            ))
        );
        assertEquals(code, exception.getCode());
    }

    private void assertCode(String code, Path source) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(source, "{}"))
        );
        assertEquals(code, exception.getCode());
    }

    private OperationContext context(Path source, String options)
            throws Exception {
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            List.of(new OperationInput(
                1,
                source,
                "source.docx",
                "application/vnd.openxmlformats-officedocument."
                    + "wordprocessingml.document",
                Files.size(source),
                "word-source-sha"
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "word-pdf-context-"
            ),
            ignored -> {
            },
            () -> false
        );
    }

    private boolean containsBluePixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getBlue() > 150
                        && color.getBlue() > color.getRed() * 2
                        && color.getBlue() > color.getGreen() * 2) {
                    return true;
                }
            }
        }
        return false;
    }
}
