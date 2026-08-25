package com.pdftools.operations.pptpdf;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.office.LibreOfficeConverter;
import com.pdftools.operations.office.NativeProcessSandbox;
import com.pdftools.operations.office.OfficeConversionProperties;
import com.pdftools.operations.office.OfficeConversionQueueClient;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerPointToPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OfficeConversionProperties properties = properties();
    private final PowerPointDocumentValidator validator =
        new PowerPointDocumentValidator(properties);
    private final PowerPointToPdfOperation operation =
        new PowerPointToPdfOperation(
            new PowerPointToPdfConverter(
                properties,
                new LibreOfficePowerPointConverter(
                    validator,
                    new LibreOfficeConverter(
                        properties,
                        new NativeProcessSandbox()
                    )
                ),
                new OfficeConversionQueueClient(properties)
            ),
            validator
        );

    @Test
    void convertsSlidesTextShapesAndPageOrder() throws Exception {
        OperationOutput output = operation.execute(context(presentation()))
            .getFirst();

        assertEquals("deck.pdf", output.filename());
        assertEquals("application/pdf", output.mediaType());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertEquals(3, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("FIRST SLIDE"));
            assertTrue(text.contains("SECOND SLIDE"));
            assertTrue(text.contains("HIDDEN SLIDE"));
            BufferedImage rendered = new PDFRenderer(document).renderImage(0);
            try {
                assertTrue(containsBluePixel(rendered));
            } finally {
                rendered.flush();
            }
        }
    }

    @Test
    void validatesSubmissionAndMalformedPresentation() throws Exception {
        operation.validateSubmission(new OperationSubmission(
            objectMapper.readTree("{}"),
            List.of(new OperationSubmission.UploadDescriptor(
                1,
                "deck.pptx",
                "application/vnd.openxmlformats-officedocument."
                    + "presentationml.presentation",
                100
            ))
        ));
        assertSubmissionCode(
            "INVALID_POWERPOINT_FILE",
            new OperationSubmission.UploadDescriptor(
                1,
                "deck.docx",
                "application/octet-stream",
                100
            )
        );
        assertCode(
            "INVALID_POWERPOINT_DOCUMENT",
            invalidPresentation(false)
        );
        assertCode(
            "INVALID_POWERPOINT_DOCUMENT",
            invalidPresentation(true)
        );
        assertCode(
            "POWERPOINT_MACROS_NOT_SUPPORTED",
            macroPresentation()
        );
        assertCode(
            "INVALID_POWERPOINT_DOCUMENT",
            wordOleRenamedAsPowerPoint()
        );
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

    private Path presentation() throws Exception {
        Path source = temporaryDirectory.resolve("deck.pptx");
        try (XMLSlideShow presentation = new XMLSlideShow()) {
            presentation.setPageSize(new Dimension(960, 540));
            XSLFSlide first = presentation.createSlide();
            addText(first, "FIRST SLIDE", 70, 50, 600, 80);
            XSLFAutoShape blue = first.createAutoShape();
            blue.setShapeType(ShapeType.RECT);
            blue.setAnchor(new Rectangle2D.Double(120, 180, 300, 180));
            blue.setFillColor(Color.BLUE);
            blue.setLineColor(Color.BLUE);

            XSLFSlide second = presentation.createSlide();
            addText(second, "SECOND SLIDE", 70, 50, 600, 80);
            addText(
                second,
                "Presentation order preserved",
                70,
                180,
                700,
                80
            );
            XSLFSlide hidden = presentation.createSlide();
            hidden.setHidden(true);
            addText(hidden, "HIDDEN SLIDE", 70, 50, 600, 80);
            try (var output = Files.newOutputStream(source)) {
                presentation.write(output);
            }
        }
        return source;
    }

    private void addText(
            XSLFSlide slide,
            String value,
            double x,
            double y,
            double width,
            double height) {
        XSLFTextBox textBox = slide.createTextBox();
        textBox.setAnchor(new Rectangle2D.Double(
            x,
            y,
            width,
            height
        ));
        var paragraph = textBox.addNewTextParagraph();
        var run = paragraph.addNewTextRun();
        run.setText(value);
        run.setFontSize(30.0);
        run.setFontColor(Color.BLACK);
    }

    private Path invalidPresentation(boolean traversal) throws Exception {
        Path source = temporaryDirectory.resolve(
            traversal ? "traversal.pptx" : "missing.pptx"
        );
        try (ZipOutputStream output = new ZipOutputStream(
                Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("[Content_Types].xml"));
            output.write("<Types/>".getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            ));
            output.closeEntry();
            if (traversal) {
                output.putNextEntry(new ZipEntry("../outside"));
                output.write(1);
                output.closeEntry();
            }
        }
        return source;
    }

    private Path macroPresentation() throws Exception {
        Path source = temporaryDirectory.resolve("macro.pptx");
        try (ZipOutputStream output = new ZipOutputStream(
                Files.newOutputStream(source))) {
            zipEntry(
                output,
                "[Content_Types].xml",
                """
                <Types>
                  <Override PartName="/ppt/macros/project.bin"
                    ContentType="application/vnd.ms-powerpoint.presentation.macro&#69;nabled.main+xml"/>
                </Types>
                """
            );
            zipEntry(output, "ppt/presentation.xml", "<presentation/>");
            zipEntry(
                output,
                "ppt/_rels/presentation.xml.rels",
                """
                <Relationships>
                  <Relationship
                    Type="http://schemas.microsoft.com/office/2006/relationships/vba&#80;roject"
                    Target="macros/project.bin"/>
                </Relationships>
                """
            );
            zipEntry(output, "ppt/macros/project.bin", "macro");
        }
        return source;
    }

    private Path wordOleRenamedAsPowerPoint() throws Exception {
        Path source = temporaryDirectory.resolve("word-as-presentation.ppt");
        try (POIFSFileSystem filesystem = new POIFSFileSystem();
             var output = Files.newOutputStream(source)) {
            filesystem.getRoot().createDocument(
                "WordDocument",
                new ByteArrayInputStream(new byte[]{1, 2, 3})
            );
            filesystem.writeFilesystem(output);
        }
        return source;
    }

    private void zipEntry(
            ZipOutputStream output,
            String name,
            String value) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(value.getBytes(
            java.nio.charset.StandardCharsets.UTF_8
        ));
        output.closeEntry();
    }

    private void assertSubmissionCode(
            String code,
            OperationSubmission.UploadDescriptor descriptor)
            throws Exception {
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
            () -> operation.execute(context(source))
        );
        assertEquals(code, exception.getCode());
    }

    private OperationContext context(Path source) throws Exception {
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree("{}"),
            List.of(new OperationInput(
                1,
                source,
                "deck.pptx",
                "application/vnd.openxmlformats-officedocument."
                    + "presentationml.presentation",
                Files.size(source),
                "powerpoint-source"
            )),
            Files.createTempDirectory(
                temporaryDirectory,
                "powerpoint-pdf-context-"
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
