package com.pdftools.operations.jpgpdf;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpgToPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JpgToPdfProperties properties = new JpgToPdfProperties();
    private final JpgToPdfOperation operation = new JpgToPdfOperation(
        new JpgToPdfEngine(
            properties,
            new JpegValidationService(properties)
        ),
        new JpgToPdfPlanFactory(properties),
        properties
    );

    @Test
    void preservesMultipartOrderAndAppliesAutomaticPaperOrientation()
            throws Exception {
        Path landscape = jpeg("landscape.jpg", 200, 100, Color.BLUE);
        Path portrait = jpeg("portrait.jpg", 100, 200, Color.RED);

        OperationOutput output = operation.execute(context(
            List.of(landscape, portrait),
            List.of("landscape.jpg", "portrait.jpg"),
            """
            {
              "pageSize":"letter",
              "orientation":"auto",
              "margin":36
            }
            """
        )).getFirst();

        assertEquals("images.pdf", output.filename());
        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            assertEquals(2, document.getNumberOfPages());
            assertPage(document.getPage(0), 792, 612);
            assertPage(document.getPage(1), 612, 792);
            assertCenterColor(document, 0, Color.BLUE);
            assertCenterColor(document, 1, Color.RED);
        }
    }

    @Test
    void supportsFitPageSizingMarginsAndForcedOrientation()
            throws Exception {
        Path image = jpeg("wide.jpg", 200, 100, Color.GREEN);

        OperationOutput fit = operation.execute(context(
            List.of(image),
            List.of("wide.jpg"),
            """
            {
              "pageSize":"fit",
              "orientation":"auto",
              "margin":24
            }
            """
        )).getFirst();
        try (PDDocument document = Loader.loadPDF(fit.path().toFile())) {
            assertPage(document.getPage(0), 198, 123);
        }

        OperationOutput portrait = operation.execute(context(
            List.of(image),
            List.of("wide.jpg"),
            """
            {
              "pageSize":"a4",
              "orientation":"portrait",
              "margin":0,
              "outputFilename":"photos.pdf"
            }
            """
        )).getFirst();
        assertEquals("photos.pdf", portrait.filename());
        try (PDDocument document =
                Loader.loadPDF(portrait.path().toFile())) {
            assertPage(document.getPage(0), 595.28f, 841.89f);
        }
    }

    @Test
    void rejectsInvalidControlsAndNonJpegContent() throws Exception {
        OperationSubmission.UploadDescriptor jpeg =
            new OperationSubmission.UploadDescriptor(
                1,
                "photo.jpg",
                "image/jpeg",
                100
            );
        assertSubmissionCode(
            "INVALID_JPG_PDF_PAGE_SIZE",
            """
            {"pageSize":"tabloid"}
            """,
            jpeg
        );
        assertSubmissionCode(
            "INVALID_JPG_PDF_ORIENTATION",
            """
            {"orientation":"upside-down"}
            """,
            jpeg
        );
        assertSubmissionCode(
            "INVALID_JPG_PDF_MARGIN",
            """
            {"margin":145}
            """,
            jpeg
        );

        Path fake = temporaryDirectory.resolve("fake.jpg");
        Files.writeString(fake, "not a jpeg");
        assertCode(
            "INVALID_JPEG",
            List.of(fake),
            List.of("fake.jpg"),
            "{}"
        );
        Path malformedFrame = temporaryDirectory.resolve(
            "malformed-frame.jpg"
        );
        Files.write(
            malformedFrame,
            new byte[]{
                (byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xC0,
                0, 8, 8, 0, 1, 0, 1, 3,
                (byte) 0xFF, (byte) 0xDA,
                0, 2,
                (byte) 0xFF, (byte) 0xD9
            }
        );
        assertCode(
            "INVALID_JPEG",
            List.of(malformedFrame),
            List.of("malformed-frame.jpg"),
            "{}"
        );
        Path complete = jpeg("complete.jpg", 20, 10, Color.BLACK);
        byte[] completeBytes = Files.readAllBytes(complete);
        Path truncated = temporaryDirectory.resolve("truncated.jpg");
        Files.write(
            truncated,
            java.util.Arrays.copyOf(
                completeBytes,
                completeBytes.length - 2
            )
        );
        assertCode(
            "INVALID_JPEG",
            List.of(truncated),
            List.of("truncated.jpg"),
            "{}"
        );
        Path adobeSource = jpeg(
            "adobe-source.jpg",
            20,
            10,
            Color.BLACK
        );
        byte[] adobeBytes = Files.readAllBytes(adobeSource);
        Path invalidAdobe = temporaryDirectory.resolve(
            "invalid-adobe.jpg"
        );
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(adobeBytes, 0, 2);
            writeApp14(
                output,
                new byte[]{
                    'A', 'd', 'o', 'b', 'e',
                    0, 100, 0, 0, 0, 0, 3
                }
            );
            output.write(adobeBytes, 2, adobeBytes.length - 2);
            Files.write(invalidAdobe, output.toByteArray());
        }
        assertCode(
            "INVALID_JPEG",
            List.of(invalidAdobe),
            List.of("invalid-adobe.jpg"),
            "{}"
        );
        Path invalidYcck = temporaryDirectory.resolve(
            "invalid-ycck.jpg"
        );
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(adobeBytes, 0, 2);
            writeApp14(
                output,
                new byte[]{
                    'A', 'd', 'o', 'b', 'e',
                    0, 100, 0, 0, 0, 0, 2
                }
            );
            output.write(adobeBytes, 2, adobeBytes.length - 2);
            Files.write(invalidYcck, output.toByteArray());
        }
        assertCode(
            "INVALID_JPEG",
            List.of(invalidYcck),
            List.of("invalid-ycck.jpg"),
            "{}"
        );

        properties.setMaxPixelsPerImage(100);
        assertCode(
            "JPEG_DIMENSION_LIMIT_EXCEEDED",
            List.of(jpeg("large.jpg", 20, 10, Color.BLACK)),
            List.of("large.jpg"),
            "{}"
        );
    }

    @Test
    void appliesEveryExifOrientationWithoutRecompressingInput()
            throws Exception {
        List<Path> inputs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int orientation = 1; orientation <= 8; orientation++) {
            String name = "orientation-" + orientation + ".jpg";
            inputs.add(orientedJpeg(
                name,
                orientation,
                orientation == 6
            ));
            names.add(name);
        }

        OperationOutput output = operation.execute(context(
            inputs,
            names,
            """
            {"pageSize":"fit","orientation":"auto","margin":0}
            """
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            Color[][] expected = {
                {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW},
                {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE},
                {Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED},
                {Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN},
                {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW},
                {Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN},
                {Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED},
                {Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE}
            };
            PDFRenderer renderer = new PDFRenderer(document);
            for (int index = 0; index < expected.length; index++) {
                boolean swapped = index + 1 >= 5;
                assertPage(
                    document.getPage(index),
                    swapped ? 75 : 150,
                    swapped ? 150 : 75
                );
                assertQuadrants(
                    renderer.renderImage(index, 2),
                    expected[index][0],
                    expected[index][1],
                    expected[index][2],
                    expected[index][3]
                );
            }
        }
    }

    @Test
    void producesDeterministicPdfBytes() throws Exception {
        Path firstImage = jpeg("first.jpg", 100, 50, Color.RED);
        Path secondImage = jpeg("second.jpg", 50, 100, Color.BLUE);
        String options = """
            {"pageSize":"a4","orientation":"auto","margin":24}
            """;

        OperationOutput first = operation.execute(context(
            List.of(firstImage, secondImage),
            List.of("first.jpg", "second.jpg"),
            options
        )).getFirst();
        OperationOutput second = operation.execute(context(
            List.of(firstImage, secondImage),
            List.of("first.jpg", "second.jpg"),
            options
        )).getFirst();

        assertArrayEquals(
            Files.readAllBytes(first.path()),
            Files.readAllBytes(second.path())
        );
    }

    @Test
    void embedsGrayscaleJpegWithoutRgbExpansion() throws Exception {
        Path grayscale = grayscaleJpeg();

        OperationOutput output = operation.execute(context(
            List.of(grayscale),
            List.of("grayscale.jpg"),
            """
            {"pageSize":"fit","orientation":"auto","margin":0}
            """
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(output.path().toFile())) {
            BufferedImage rendered = new PDFRenderer(document).renderImage(0);
            Color center = new Color(rendered.getRGB(
                rendered.getWidth() / 2,
                rendered.getHeight() / 2
            ));
            assertTrue(Math.abs(center.getRed() - 128) <= 8);
            assertTrue(Math.abs(center.getGreen() - 128) <= 8);
            assertTrue(Math.abs(center.getBlue() - 128) <= 8);
        }
    }

    @Test
    void keepsAdobeDetectionAcrossLaterApp14Segments() throws Exception {
        Path source = jpeg("adobe.jpg", 20, 10, Color.BLACK);
        byte[] jpeg = Files.readAllBytes(source);
        Path tagged = temporaryDirectory.resolve("tagged-adobe.jpg");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(jpeg, 0, 2);
            writeApp14(
                output,
                new byte[]{
                    'A', 'd', 'o', 'b', 'e',
                    0, 100, 0, 0, 0, 0, 0
                }
            );
            writeApp14(
                output,
                "not-adobe-data".getBytes(
                    java.nio.charset.StandardCharsets.US_ASCII
                )
            );
            output.write(jpeg, 2, jpeg.length - 2);
            Files.write(tagged, output.toByteArray());
        }

        assertTrue(
            new JpegInspector().inspect(tagged, () -> {
            }).adobe()
        );
    }

    @Test
    void preservesCancellationAcrossMetadataFloodValidation()
            throws Exception {
        Path source = jpeg("metadata.jpg", 20, 10, Color.BLACK);
        byte[] jpeg = Files.readAllBytes(source);
        Path flooded = temporaryDirectory.resolve("metadata-flood.jpg");
        byte[] payload = new byte[60_000];
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(jpeg, 0, 2);
            for (int index = 0; index < 20; index++) {
                writeAppSegment(output, 0xE2, payload);
            }
            output.write(jpeg, 2, jpeg.length - 2);
            Files.write(flooded, output.toByteArray());
        }

        assertThrows(
            OperationCancelledException.class,
            () -> new JpegInspector().inspect(
                flooded,
                () -> {
                    throw new OperationCancelledException();
                }
            )
        );
    }

    @Test
    void rejectsProgressiveImagesBeyondCoefficientBudget()
            throws Exception {
        properties.setMaxProgressiveCoefficientBytes(1);

        assertCode(
            "JPEG_PROGRESSIVE_MEMORY_LIMIT_EXCEEDED",
            List.of(progressiveJpeg()),
            List.of("progressive.jpg"),
            "{}"
        );
    }

    @Test
    void terminatesIsolatedValidationAtConfiguredTimeout()
            throws Exception {
        properties.setValidationWorkerTimeout(Duration.ofMillis(1));

        assertCode(
            "JPEG_VALIDATION_TIMEOUT",
            List.of(jpeg("timeout.jpg", 20, 10, Color.BLACK)),
            List.of("timeout.jpg"),
            "{}"
        );
    }

    @Test
    void stripsMetadataInsertedBetweenProgressiveScans()
            throws Exception {
        Path progressive = progressiveJpeg();
        byte[] jpeg = Files.readAllBytes(progressive);
        int secondScan = secondMarker(jpeg, 0xDA);
        Path tagged = temporaryDirectory.resolve("post-scan-metadata.jpg");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(jpeg, 0, secondScan);
            writeAppSegment(output, 0xE2, new byte[60_000]);
            output.write(jpeg, secondScan, jpeg.length - secondScan);
            Files.write(tagged, output.toByteArray());
        }

        OperationOutput result = operation.execute(context(
            List.of(tagged),
            List.of("post-scan-metadata.jpg"),
            "{}"
        )).getFirst();

        try (PDDocument document = Loader.loadPDF(result.path().toFile())) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    private void assertSubmissionCode(
            String code,
            String options,
            OperationSubmission.UploadDescriptor file) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.validateSubmission(new OperationSubmission(
                objectMapper.readTree(options),
                List.of(file)
            ))
        );
        assertEquals(code, exception.getCode());
    }

    private void assertCode(
            String code,
            List<Path> paths,
            List<String> names,
            String options) throws Exception {
        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(paths, names, options))
        );
        assertEquals(code, exception.getCode());
    }

    private void assertPage(
            PDPage page,
            float expectedWidth,
            float expectedHeight) {
        assertEquals(expectedWidth, page.getMediaBox().getWidth(), 0.02);
        assertEquals(expectedHeight, page.getMediaBox().getHeight(), 0.02);
    }

    private void assertCenterColor(
            PDDocument document,
            int pageIndex,
            Color expected) throws Exception {
        BufferedImage rendered = new PDFRenderer(document).renderImage(
            pageIndex
        );
        Color actual = new Color(rendered.getRGB(
            rendered.getWidth() / 2,
            rendered.getHeight() / 2
        ));
        assertTrue(Math.abs(actual.getRed() - expected.getRed()) <= 12);
        assertTrue(Math.abs(actual.getGreen() - expected.getGreen()) <= 12);
        assertTrue(Math.abs(actual.getBlue() - expected.getBlue()) <= 12);
    }

    private void assertQuadrants(
            BufferedImage image,
            Color topLeft,
            Color topRight,
            Color bottomLeft,
            Color bottomRight) {
        assertColor(image, image.getWidth() / 4, image.getHeight() / 4, topLeft);
        assertColor(
            image,
            image.getWidth() * 3 / 4,
            image.getHeight() / 4,
            topRight
        );
        assertColor(
            image,
            image.getWidth() / 4,
            image.getHeight() * 3 / 4,
            bottomLeft
        );
        assertColor(
            image,
            image.getWidth() * 3 / 4,
            image.getHeight() * 3 / 4,
            bottomRight
        );
    }

    private void assertColor(
            BufferedImage image,
            int x,
            int y,
            Color expected) {
        Color actual = new Color(image.getRGB(x, y));
        assertTrue(Math.abs(actual.getRed() - expected.getRed()) <= 35);
        assertTrue(Math.abs(actual.getGreen() - expected.getGreen()) <= 35);
        assertTrue(Math.abs(actual.getBlue() - expected.getBlue()) <= 35);
    }

    private Path jpeg(
            String filename,
            int width,
            int height,
            Color color) throws Exception {
        BufferedImage image = new BufferedImage(
            width,
            height,
            BufferedImage.TYPE_INT_RGB
        );
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        Path path = temporaryDirectory.resolve(
            UUID.randomUUID() + "-" + filename
        );
        ImageIO.write(image, "jpeg", path.toFile());
        image.flush();
        return path;
    }

    private Path orientedJpeg(
            String filename,
            int orientation,
            boolean appendXmp)
            throws Exception {
        BufferedImage image = new BufferedImage(
            200,
            100,
            BufferedImage.TYPE_INT_RGB
        );
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 100, 50);
            graphics.setColor(Color.GREEN);
            graphics.fillRect(100, 0, 100, 50);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 50, 100, 50);
            graphics.setColor(Color.YELLOW);
            graphics.fillRect(100, 50, 100, 50);
        } finally {
            graphics.dispose();
        }
        byte[] jpeg;
        try (ByteArrayOutputStream encoded = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", encoded);
            jpeg = encoded.toByteArray();
        } finally {
            image.flush();
        }
        ByteBuffer exif = ByteBuffer.allocate(32)
            .order(ByteOrder.BIG_ENDIAN);
        exif.put(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        exif.put((byte) 'M');
        exif.put((byte) 'M');
        exif.putShort((short) 42);
        exif.putInt(8);
        exif.putShort((short) 1);
        exif.putShort((short) 0x0112);
        exif.putShort((short) 3);
        exif.putInt(1);
        exif.putShort((short) orientation);
        exif.putShort((short) 0);
        exif.putInt(0);

        Path path = temporaryDirectory.resolve(filename);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(jpeg, 0, 2);
            output.write(0xFF);
            output.write(0xE1);
            int segmentLength = exif.array().length + 2;
            output.write(segmentLength >>> 8);
            output.write(segmentLength);
            output.write(exif.array());
            if (appendXmp) {
                byte[] xmp = "http://ns.adobe.com/xap/1.0/\0<xmp/>"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                output.write(0xFF);
                output.write(0xE1);
                int xmpLength = xmp.length + 2;
                output.write(xmpLength >>> 8);
                output.write(xmpLength);
                output.write(xmp);
            }
            output.write(jpeg, 2, jpeg.length - 2);
            Files.write(path, output.toByteArray());
        }
        return path;
    }

    private Path grayscaleJpeg() throws Exception {
        BufferedImage image = new BufferedImage(
            40,
            20,
            BufferedImage.TYPE_BYTE_GRAY
        );
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(128, 128, 128));
            graphics.fillRect(0, 0, 40, 20);
        } finally {
            graphics.dispose();
        }
        Path path = temporaryDirectory.resolve("grayscale.jpg");
        ImageIO.write(image, "jpeg", path.toFile());
        image.flush();
        return path;
    }

    private Path progressiveJpeg() throws Exception {
        BufferedImage image = new BufferedImage(
            40,
            20,
            BufferedImage.TYPE_INT_RGB
        );
        Path path = temporaryDirectory.resolve("progressive.jpg");
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg")
            .next();
        try (FileImageOutputStream output =
                new FileImageOutputStream(path.toFile())) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.setOutput(output);
            writer.write(
                null,
                new javax.imageio.IIOImage(image, null, null),
                parameters
            );
        } finally {
            writer.dispose();
            image.flush();
        }
        return path;
    }

    private void writeApp14(
            ByteArrayOutputStream output,
            byte[] payload) {
        writeAppSegment(output, 0xEE, payload);
    }

    private void writeAppSegment(
            ByteArrayOutputStream output,
            int marker,
            byte[] payload) {
        output.write(0xFF);
        output.write(marker);
        int length = payload.length + 2;
        output.write(length >>> 8);
        output.write(length);
        output.writeBytes(payload);
    }

    private int secondMarker(byte[] jpeg, int marker) {
        int found = 0;
        for (int index = 0; index + 1 < jpeg.length; index++) {
            if (Byte.toUnsignedInt(jpeg[index]) == 0xFF
                    && Byte.toUnsignedInt(jpeg[index + 1]) == marker
                    && ++found == 2) {
                return index;
            }
        }
        throw new AssertionError("Expected a second JPEG marker");
    }

    private OperationContext context(
            List<Path> paths,
            List<String> names,
            String options) throws Exception {
        List<OperationInput> inputs = new ArrayList<>();
        for (int index = 0; index < paths.size(); index++) {
            Path path = paths.get(index);
            inputs.add(new OperationInput(
                index + 1,
                path,
                names.get(index),
                "image/jpeg",
                Files.size(path),
                "sha-" + index
            ));
        }
        return new OperationContext(
            UUID.randomUUID(),
            objectMapper.readTree(options),
            inputs,
            Files.createTempDirectory(temporaryDirectory, "jpg-pdf-context-"),
            ignored -> {
            },
            () -> false
        );
    }
}
