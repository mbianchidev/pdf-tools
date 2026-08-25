package com.pdftools.operations.split;

import com.pdftools.operations.OperationContext;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OperationOutput;
import com.pdftools.operations.OperationSubmission;
import com.pdftools.operations.ZipArtifactService;
import com.pdftools.operations.shared.pages.PageExpressionParser;
import com.pdftools.testing.PdfTestFixtures;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroupAttributes;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDShadingPattern;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitPdfOperationTest {

    @TempDir
    Path temporaryDirectory;

    private final SplitProperties properties = new SplitProperties();
    private final SplitPlanFactory planFactory = new SplitPlanFactory(
        new PageExpressionParser(),
        properties
    );
    private final SplitPdfOperation operation = new SplitPdfOperation(
        new PdfSplitEngine(planFactory, properties),
        new ZipArtifactService(),
        properties
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsOrderedRangeDocumentsInOneDeterministicZip() throws Exception {
        Path source = sourcePdf();
        OperationContext context = context(
            source,
            "{\"mode\":\"ranges\",\"ranges\":[\"2-3\",\"1,5\"]}"
        );

        OperationOutput zip = operation.execute(context).getFirst();

        assertEquals("source_split.zip", zip.filename());
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            List<? extends ZipEntry> entries = archive.stream().toList();
            assertEquals(
                List.of("source_part_0001.pdf", "source_part_0002.pdf"),
                entries.stream().map(ZipEntry::getName).toList()
            );
            try (PDDocument first = Loader.loadPDF(read(archive, entries.get(0)));
                 PDDocument second = Loader.loadPDF(read(archive, entries.get(1)))) {
                assertEquals(List.of(102f, 103f), widths(first));
                assertEquals(List.of(101f, 105f), widths(second));
                assertCenterColor(new PDFRenderer(first).renderImage(0), Color.GREEN);
                assertCenterColor(new PDFRenderer(first).renderImage(1), Color.BLUE);
                assertCenterColor(new PDFRenderer(second).renderImage(0), Color.RED);
                assertCenterColor(new PDFRenderer(second).renderImage(1), Color.MAGENTA);
            }
        }
    }

    @Test
    void supportsIndividualAndFixedModes() throws Exception {
        Path source = sourcePdf();
        OperationOutput individual = operation.execute(context(
            source,
            "{\"mode\":\"individual\"}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(individual.path().toFile())) {
            assertEquals(5, archive.size());
            assertTrue(archive.getEntry("source_page_0001.pdf") != null);
            assertTrue(archive.getEntry("source_page_0005.pdf") != null);
        }

        OperationOutput fixed = operation.execute(context(
            source,
            "{\"mode\":\"fixed\",\"fixedGroupSize\":2,"
                + "\"outputFilename\":\"groups.zip\"}"
        )).getFirst();
        assertEquals("groups.zip", fixed.filename());
        try (ZipFile archive = new ZipFile(fixed.path().toFile())) {
            List<Integer> pageCounts = new ArrayList<>();
            for (ZipEntry entry : archive.stream().toList()) {
                try (PDDocument document = Loader.loadPDF(read(archive, entry))) {
                    pageCounts.add(document.getNumberOfPages());
                }
            }
            assertEquals(List.of(2, 2, 1), pageCounts);
        }
    }

    @Test
    void validatesStaticSubmissionOptionsBeforeStorage() throws Exception {
        OperationSubmission.UploadDescriptor pdf =
            new OperationSubmission.UploadDescriptor(
                1,
                "source.pdf",
                "application/pdf",
                10
            );
        assertEquals(
            "INVALID_FILE_COUNT",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.createObjectNode(),
                    List.of(pdf, pdf)
                ))
            ).getCode()
        );
        assertEquals(
            "INVALID_FIXED_GROUP_SIZE",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree(
                        "{\"mode\":\"fixed\",\"fixedGroupSize\":0}"
                    ),
                    List.of(pdf)
                ))
            ).getCode()
        );
        assertEquals(
            "SPLIT_RANGES_REQUIRED",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree("{\"mode\":\"ranges\",\"ranges\":[]}"),
                    List.of(pdf)
                ))
            ).getCode()
        );
        assertEquals(
            "INVALID_OUTPUT_FILENAME",
            assertThrows(
                OperationException.class,
                () -> operation.validateSubmission(new OperationSubmission(
                    objectMapper.readTree(
                        "{\"mode\":\"individual\",\"outputFilename\":\"parts.pdf\"}"
                    ),
                    List.of(pdf)
                ))
            ).getCode()
        );
    }

    @Test
    void rejectsUnreadableAndEncryptedPdfs() throws Exception {
        Path invalid = temporaryDirectory.resolve("invalid.bin");
        Files.writeString(invalid, "not a PDF");
        assertEquals(
            "INVALID_PDF",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(invalid, "{\"mode\":\"individual\"}"))
            ).getCode()
        );

        Path encrypted = temporaryDirectory.resolve("encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                "owner",
                "",
                new org.apache.pdfbox.pdmodel.encryption.AccessPermission()
            ));
            document.save(encrypted.toFile());
        }
        assertEquals(
            "ENCRYPTED_PDF",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(encrypted, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void producesByteDeterministicZipsForIdenticalInputs() throws Exception {
        Path source = sourcePdf();

        OperationOutput first = operation.execute(context(
            source,
            "{\"mode\":\"fixed\",\"fixedGroupSize\":2}"
        )).getFirst();
        OperationOutput second = operation.execute(context(
            source,
            "{\"mode\":\"fixed\",\"fixedGroupSize\":2}"
        )).getFirst();

        assertArrayEquals(Files.readAllBytes(first.path()), Files.readAllBytes(second.path()));
    }

    @Test
    void boundsSingleAndCumulativeOutputBytes() throws Exception {
        properties.setMaxOutputBytes(100);
        properties.setMaxTotalOutputBytes(200);

        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(
                sourcePdf(),
                "{\"mode\":\"individual\"}"
            ))
        );

        assertEquals("SPLIT_OUTPUT_SIZE_LIMIT_EXCEEDED", exception.getCode());
    }

    @Test
    void removesLinksToOmittedPagesAndTheirRecoverableContent() throws Exception {
        Path source = temporaryDirectory.resolve("linked.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage publicPage = new PDPage();
            PDPage secretPage = new PDPage();
            document.addPage(publicPage);
            document.addPage(secretPage);
            writeText(document, publicPage, "PUBLIC", true);
            writeText(document, secretPage, "SECRET-OMITTED", false);

            PDPageFitDestination destination = new PDPageFitDestination();
            destination.setPage(secretPage);
            PDActionGoTo action = new PDActionGoTo();
            action.setDestination(destination);
            PDAnnotationLink link = new PDAnnotationLink();
            link.setRectangle(new PDRectangle(50, 700, 200, 40));
            link.setAction(action);
            publicPage.setAnnotations(List.of(link));
            COSDictionary bead = new COSDictionary();
            bead.setItem(COSName.P, secretPage.getCOSObject());
            COSArray beads = new COSArray();
            beads.add(bead);
            publicPage.getCOSObject().setItem(COSName.B, beads);
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"ranges\",\"ranges\":[\"1\"]}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            ZipEntry entry = archive.entries().nextElement();
            byte[] output = read(archive, entry);
            assertFalse(
                new String(output, StandardCharsets.ISO_8859_1)
                    .contains("SECRET-OMITTED")
            );
            try (PDDocument split = Loader.loadPDF(output)) {
                assertTrue(split.getPage(0).getAnnotations().isEmpty());
            }
        }
    }

    @Test
    void rejectsPagesWhoseDecodedContentExceedsTheLimit() throws Exception {
        properties.setMaxDecodedPageBytes(100);
        Path source = temporaryDirectory.resolve("decoded-limit.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            org.apache.pdfbox.pdmodel.common.PDStream stream =
                new org.apache.pdfbox.pdmodel.common.PDStream(document);
            try (var output = stream.createOutputStream(COSName.FLATE_DECODE)) {
                output.write("q\n".repeat(1000).getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(stream);
            document.save(source.toFile());
        }

        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(source, "{\"mode\":\"individual\"}"))
        );

        assertEquals("PDF_PAGE_CONTENT_LIMIT_EXCEEDED", exception.getCode());
    }

    @Test
    void boundsDecodedContentAcrossTheWholeSplitJob() throws Exception {
        properties.setMaxDecodedPageBytes(200);
        properties.setMaxTotalDecodedBytes(250);
        Path source = temporaryDirectory.resolve("decoded-total-limit.pdf");
        try (PDDocument document = new PDDocument()) {
            for (int index = 0; index < 2; index++) {
                PDPage page = new PDPage();
                document.addPage(page);
                org.apache.pdfbox.pdmodel.common.PDStream stream =
                    new org.apache.pdfbox.pdmodel.common.PDStream(document);
                try (var output = stream.createOutputStream()) {
                    output.write("q\n".repeat(80).getBytes(StandardCharsets.US_ASCII));
                }
                page.setContents(stream);
            }
            document.save(source.toFile());
        }

        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(source, "{\"mode\":\"individual\"}"))
        );

        assertEquals("SPLIT_DECODED_CONTENT_LIMIT_EXCEEDED", exception.getCode());
    }

    @Test
    void boundsAggregateResourceScratchWrites() throws Exception {
        properties.setMaxResourceScratchBytes(1);
        Path source = temporaryDirectory.resolve("resource-scratch-limit.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage imageData = new BufferedImage(
                10,
                10,
                BufferedImage.TYPE_INT_RGB
            );
            PDImageXObject image = LosslessFactory.createFromImage(
                document,
                imageData
            );
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Im"), image);
            page.setResources(resources);
            setRawContent(document, page, "/Im Do");
            document.save(source.toFile());
        }

        assertEquals(
            "SPLIT_RESOURCE_SCRATCH_LIMIT_EXCEEDED",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(source, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void boundsResourceStructuresAcrossTheWholeJob() throws Exception {
        properties.setMaxTotalResourceNodes(2);
        Path source = temporaryDirectory.resolve("resource-node-limit.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage imageData = new BufferedImage(
                1,
                1,
                BufferedImage.TYPE_INT_RGB
            );
            PDImageXObject image = LosslessFactory.createFromImage(
                document,
                imageData
            );
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Im"), image);
            page.setResources(resources);
            setRawContent(document, page, "/Im Do");
            document.save(source.toFile());
        }

        assertEquals(
            "PDF_RESOURCE_TOTAL_COMPLEXITY_LIMIT_EXCEEDED",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(source, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void resetsPerGraphResourceLimitAcrossMultiPageOutputs() throws Exception {
        properties.setMaxResourceNodes(100);
        properties.setMaxTotalResourceNodes(5_000);
        Path source = temporaryDirectory.resolve("multi-page-resources.pdf");
        try (PDDocument document = new PDDocument()) {
            for (int pageIndex = 0; pageIndex < 25; pageIndex++) {
                PDPage page = new PDPage();
                document.addPage(page);
                BufferedImage imageData = new BufferedImage(
                    1,
                    1,
                    BufferedImage.TYPE_INT_RGB
                );
                PDImageXObject image = LosslessFactory.createFromImage(
                    document,
                    imageData
                );
                PDResources resources = new PDResources();
                resources.put(COSName.getPDFName("Im"), image);
                page.setResources(resources);
                setRawContent(document, page, "/Im Do");
            }
            document.save(source.toFile());
        }

        OperationOutput output = operation.execute(context(
            source,
            "{\"mode\":\"ranges\",\"ranges\":[\"all\"]}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(output.path().toFile())) {
            assertEquals(1, archive.size());
        }
    }

    @Test
    void prunesUnusedSharedXObjectsFromSelectedPages() throws Exception {
        Path source = temporaryDirectory.resolve("shared-resources.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage selectedPage = new PDPage();
            PDPage omittedPage = new PDPage();
            document.addPage(selectedPage);
            document.addPage(omittedPage);

            PDFormXObject secretForm = new PDFormXObject(document);
            secretForm.setBBox(new PDRectangle(0, 0, 10, 10));
            secretForm.setResources(new PDResources());
            try (var output = secretForm.getContentStream().createOutputStream()) {
                output.write(
                    "%SECRET-XOBJECT\nq Q".getBytes(StandardCharsets.ISO_8859_1)
                );
            }
            COSName secretName = COSName.getPDFName("Secret");
            PDResources shared = new PDResources();
            shared.put(secretName, secretForm);
            selectedPage.setResources(shared);
            omittedPage.setResources(shared);
            setRawContent(document, selectedPage, "q Q");
            setRawContent(document, omittedPage, "q /Secret Do Q");
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"ranges\",\"ranges\":[\"1\"]}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            assertFalse(
                new String(output, StandardCharsets.ISO_8859_1)
                    .contains("SECRET-XOBJECT")
            );
            try (PDDocument split = Loader.loadPDF(output)) {
                assertFalse(
                    split.getPage(0).getResources().getXObjectNames().iterator().hasNext()
                );
            }
        }
    }

    @Test
    void rebuildsTransparencyGroupsWithoutOmittedPageReferences() throws Exception {
        Path source = temporaryDirectory.resolve("group-reference.pdf");
        COSName hiddenReference = COSName.getPDFName("HiddenReference");
        COSName formName = COSName.getPDFName("Fm");
        try (PDDocument document = new PDDocument()) {
            PDPage publicPage = new PDPage();
            PDPage secretPage = new PDPage();
            document.addPage(publicPage);
            document.addPage(secretPage);
            setRawContent(document, secretPage, "%SECRET-GROUP-LEAK\nq Q");
            var secretContent = secretPage.getCOSObject()
                .getDictionaryObject(COSName.CONTENTS);

            COSDictionary pageGroup = new COSDictionary();
            pageGroup.setItem(COSName.S, COSName.TRANSPARENCY);
            pageGroup.setItem(COSName.CS, COSName.DEVICERGB);
            pageGroup.setBoolean(COSName.I, true);
            pageGroup.setItem(hiddenReference, secretContent);
            publicPage.getCOSObject().setItem(COSName.GROUP, pageGroup);

            PDFormXObject form = new PDFormXObject(document);
            form.setBBox(new PDRectangle(0, 0, 10, 10));
            form.setResources(new PDResources());
            try (var output = form.getContentStream().createOutputStream()) {
                output.write("q Q".getBytes(StandardCharsets.US_ASCII));
            }
            PDTransparencyGroupAttributes formGroup =
                new PDTransparencyGroupAttributes();
            formGroup.getCOSObject().setBoolean(COSName.K, true);
            formGroup.getCOSObject().setItem(hiddenReference, secretContent);
            form.setGroup(formGroup);

            PDResources resources = new PDResources();
            resources.put(formName, form);
            publicPage.setResources(resources);
            setRawContent(document, publicPage, "q /Fm Do Q");
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"ranges\",\"ranges\":[\"1\"]}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            assertFalse(
                new String(output, StandardCharsets.ISO_8859_1)
                    .contains("SECRET-GROUP-LEAK")
            );
            try (PDDocument split = Loader.loadPDF(output)) {
                COSDictionary pageGroup = split.getPage(0).getCOSObject()
                    .getCOSDictionary(COSName.GROUP);
                assertFalse(pageGroup.containsKey(hiddenReference));
                assertTrue(pageGroup.getBoolean(COSName.I, false));
                assertEquals(
                    COSName.DEVICERGB,
                    pageGroup.getDictionaryObject(COSName.CS)
                );

                PDFormXObject form = (PDFormXObject) split.getPage(0)
                    .getResources()
                    .getXObject(formName);
                assertFalse(
                    form.getGroup().getCOSObject().containsKey(hiddenReference)
                );
                assertTrue(form.getGroup().isKnockout());
            }
        }
    }

    @Test
    void boundsDecodedContentInsideReferencedForms() throws Exception {
        properties.setMaxDecodedPageBytes(100);
        properties.setMaxTotalDecodedBytes(200);
        Path source = temporaryDirectory.resolve("nested-limit.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDFormXObject form = new PDFormXObject(document);
            form.setBBox(new PDRectangle(0, 0, 10, 10));
            try (var output = form.getContentStream().createOutputStream()) {
                output.write("q\n".repeat(1000).getBytes(StandardCharsets.US_ASCII));
            }
            PDResources resources = new PDResources();
            COSName formName = COSName.getPDFName("Fm");
            resources.put(formName, form);
            page.setResources(resources);
            setRawContent(document, page, "q /Fm Do Q");
            document.save(source.toFile());
        }

        OperationException exception = assertThrows(
            OperationException.class,
            () -> operation.execute(context(source, "{\"mode\":\"individual\"}"))
        );

        assertEquals("PDF_PAGE_CONTENT_LIMIT_EXCEEDED", exception.getCode());
    }

    @Test
    void retainsTransitiveImageColorSpaceDependencies() throws Exception {
        Path source = temporaryDirectory.resolve("image-colorspace.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(100, 100));
            document.addPage(page);
            BufferedImage imageData = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = imageData.createGraphics();
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 10, 10);
            graphics.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(document, imageData);
            COSName customColorSpace = COSName.getPDFName("CustomRGB");
            image.getCOSObject().setItem(COSName.COLORSPACE, customColorSpace);
            PDResources resources = new PDResources();
            resources.put(customColorSpace, PDDeviceRGB.INSTANCE);
            COSName imageName = COSName.getPDFName("Im");
            resources.put(imageName, image);
            page.setResources(resources);
            setRawContent(document, page, "q 80 0 0 80 10 10 cm /Im Do Q");
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"individual\"}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            try (PDDocument split = Loader.loadPDF(output)) {
                BufferedImage rendered = new PDFRenderer(split).renderImage(0);
                assertCenterColor(rendered, Color.RED);
            }
        }
    }

    @Test
    void retainsNamedColorSpaceDependencies() throws Exception {
        Path source = temporaryDirectory.resolve("named-colorspace.pdf");
        COSName baseName = COSName.getPDFName("Base");
        COSName indexedName = COSName.getPDFName("Idx");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(100, 100));
            document.addPage(page);
            COSArray indexed = new COSArray();
            indexed.add(COSName.INDEXED);
            indexed.add(baseName);
            indexed.add(COSInteger.ONE);
            indexed.add(new COSString(new byte[] {
                (byte) 0xff, 0, 0,
                0, (byte) 0xff, 0
            }));
            COSDictionary colorSpaces = new COSDictionary();
            colorSpaces.setItem(baseName, COSName.DEVICERGB);
            colorSpaces.setItem(indexedName, indexed);
            PDResources resources = new PDResources();
            resources.getCOSObject().setItem(COSName.COLORSPACE, colorSpaces);
            page.setResources(resources);
            setRawContent(
                document,
                page,
                "/Idx cs 0 scn 0 0 100 100 re f"
            );
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"individual\"}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            try (PDDocument split = Loader.loadPDF(output)) {
                assertTrue(split.getPage(0).getResources().hasColorSpace(baseName));
                BufferedImage rendered = new PDFRenderer(split).renderImage(0);
                assertCenterColor(rendered, Color.RED);
            }
        }
    }

    @Test
    void ignoresColorantNamesThatCollideWithResourceNames() throws Exception {
        Path source = temporaryDirectory.resolve("colorant-collision.pdf");
        COSName separationName = COSName.getPDFName("Sep");
        COSName colorantName = COSName.getPDFName("Spot");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            COSDictionary tintFunction = new COSDictionary();
            tintFunction.setInt(COSName.FUNCTION_TYPE, 2);
            COSArray domain = new COSArray();
            domain.add(COSInteger.ZERO);
            domain.add(COSInteger.ONE);
            tintFunction.setItem(COSName.DOMAIN, domain);
            tintFunction.setInt(COSName.N, 1);
            COSArray separation = new COSArray();
            separation.add(COSName.SEPARATION);
            separation.add(colorantName);
            separation.add(COSName.DEVICERGB);
            separation.add(tintFunction);

            var hiddenProfile =
                new org.apache.pdfbox.pdmodel.common.PDStream(document);
            hiddenProfile.getCOSObject().setInt(COSName.N, 3);
            try (var output = hiddenProfile.createOutputStream()) {
                output.write(
                    "SECRET-COLORANT-RESOURCE"
                        .getBytes(StandardCharsets.ISO_8859_1)
                );
            }
            COSArray collidingResource = new COSArray();
            collidingResource.add(COSName.ICCBASED);
            collidingResource.add(hiddenProfile);

            COSDictionary colorSpaces = new COSDictionary();
            colorSpaces.setItem(separationName, separation);
            colorSpaces.setItem(colorantName, collidingResource);
            PDResources resources = new PDResources();
            resources.getCOSObject().setItem(COSName.COLORSPACE, colorSpaces);
            page.setResources(resources);
            setRawContent(
                document,
                page,
                "/Sep cs 0.5 scn 0 0 100 100 re f"
            );
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"individual\"}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            assertFalse(
                new String(output, StandardCharsets.ISO_8859_1)
                    .contains("SECRET-COLORANT-RESOURCE")
            );
            try (PDDocument split = Loader.loadPDF(output)) {
                COSDictionary colorSpaces = split.getPage(0)
                    .getResources()
                    .getCOSObject()
                    .getCOSDictionary(COSName.COLORSPACE);
                assertTrue(colorSpaces.containsKey(separationName));
                assertFalse(colorSpaces.containsKey(colorantName));
            }
        }
    }

    @Test
    void stripsUnknownResourceMetadataAndRejectsSoftMasks() throws Exception {
        Path unsafe = temporaryDirectory.resolve("unsafe-resource.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage publicPage = new PDPage();
            PDPage secretPage = new PDPage();
            document.addPage(publicPage);
            document.addPage(secretPage);
            setRawContent(document, secretPage, "%SECRET-RESOURCE-LEAK\nq Q");
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.getCOSObject().setItem(
                COSName.getPDFName("HiddenReference"),
                secretPage.getCOSObject().getDictionaryObject(COSName.CONTENTS)
            );
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Gs"), state);
            publicPage.setResources(resources);
            setRawContent(document, publicPage, "/Gs gs");
            document.save(unsafe.toFile());
        }
        OperationOutput safeZip = operation.execute(context(
            unsafe,
            "{\"mode\":\"ranges\",\"ranges\":[\"1\"]}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(safeZip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            assertFalse(
                new String(output, StandardCharsets.ISO_8859_1)
                    .contains("SECRET-RESOURCE-LEAK")
            );
        }

        Path softMask = temporaryDirectory.resolve("soft-mask.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.getCOSObject().setItem(COSName.SMASK, new COSDictionary());
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Gs"), state);
            page.setResources(resources);
            setRawContent(document, page, "/Gs gs");
            document.save(softMask.toFile());
        }
        assertEquals(
            "UNSUPPORTED_SOFT_MASK",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(softMask, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void rejectsSoftMasksEmbeddedInShadingPatterns() throws Exception {
        Path source = temporaryDirectory.resolve("shading-soft-mask.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDShadingPattern pattern = new PDShadingPattern();
            COSDictionary shading = new COSDictionary();
            shading.setInt(COSName.SHADING_TYPE, 2);
            shading.setItem(COSName.COLORSPACE, COSName.DEVICERGB);
            COSArray coordinates = new COSArray();
            for (int value : List.of(0, 0, 100, 100)) {
                coordinates.add(COSInteger.get(value));
            }
            shading.setItem(COSName.COORDS, coordinates);
            COSDictionary function = new COSDictionary();
            function.setInt(COSName.FUNCTION_TYPE, 2);
            COSArray domain = new COSArray();
            domain.add(COSInteger.ZERO);
            domain.add(COSInteger.ONE);
            function.setItem(COSName.DOMAIN, domain);
            function.setItem(COSName.C0, new COSArray());
            function.setItem(COSName.C1, new COSArray());
            function.setInt(COSName.N, 1);
            shading.setItem(COSName.FUNCTION, function);
            pattern.getCOSObject().setItem(COSName.SHADING, shading);
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.getCOSObject().setItem(COSName.SMASK, new COSDictionary());
            pattern.setExtendedGraphicsState(state);

            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("P"), pattern);
            page.setResources(resources);
            setRawContent(
                document,
                page,
                "/Pattern cs /P scn 0 0 100 100 re f"
            );
            document.save(source.toFile());
        }

        assertEquals(
            "UNSUPPORTED_SOFT_MASK",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(source, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void sanitizesDirectShadingsAndRejectsCyclicFunctions() throws Exception {
        Path source = temporaryDirectory.resolve("direct-shading.pdf");
        COSName shadingName = COSName.getPDFName("Shade");
        COSName hiddenReference = COSName.getPDFName("HiddenReference");
        try (PDDocument document = new PDDocument()) {
            PDPage publicPage = new PDPage();
            PDPage secretPage = new PDPage();
            document.addPage(publicPage);
            document.addPage(secretPage);
            setRawContent(document, secretPage, "%SECRET-SHADING-LEAK\nq Q");

            COSDictionary shading = basicAxialShading();
            COSArray matrix = new COSArray();
            for (int value : List.of(1, 0, 0, 1, 5, 7)) {
                matrix.add(COSInteger.get(value));
            }
            shading.setItem(COSName.MATRIX, matrix);
            shading.setItem(
                hiddenReference,
                secretPage.getCOSObject().getDictionaryObject(COSName.CONTENTS)
            );
            COSDictionary shadings = new COSDictionary();
            shadings.setItem(shadingName, shading);
            PDResources resources = new PDResources();
            resources.getCOSObject().setItem(COSName.SHADING, shadings);
            publicPage.setResources(resources);
            setRawContent(document, publicPage, "/Shade sh");
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"ranges\",\"ranges\":[\"1\"]}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            assertFalse(
                new String(output, StandardCharsets.ISO_8859_1)
                    .contains("SECRET-SHADING-LEAK")
            );
            try (PDDocument split = Loader.loadPDF(output)) {
                assertTrue(
                    split.getPage(0).getResources()
                        .getShading(shadingName)
                        .getCOSObject()
                        .containsKey(COSName.MATRIX)
                );
            }
        }

        Path cyclic = temporaryDirectory.resolve("cyclic-function.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            COSDictionary function = new COSDictionary();
            function.setInt(COSName.FUNCTION_TYPE, 3);
            COSArray nestedFunctions = new COSArray();
            nestedFunctions.add(function);
            function.setItem(COSName.getPDFName("Functions"), nestedFunctions);
            COSDictionary shading = basicAxialShading();
            shading.setItem(COSName.FUNCTION, function);
            COSDictionary shadings = new COSDictionary();
            shadings.setItem(shadingName, shading);
            PDResources resources = new PDResources();
            resources.getCOSObject().setItem(COSName.SHADING, shadings);
            page.setResources(resources);
            setRawContent(document, page, "/Shade sh");
            document.save(cyclic.toFile());
        }
        assertEquals(
            "CYCLIC_PDF_RESOURCE",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(cyclic, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void ignoresInlineMarkedContentDictionariesWhenPruningResources() throws Exception {
        Path source = temporaryDirectory.resolve("inline-properties.pdf");
        COSName hiddenReference = COSName.getPDFName("HiddenReference");
        try (PDDocument document = new PDDocument()) {
            PDPage publicPage = new PDPage();
            PDPage secretPage = new PDPage();
            document.addPage(publicPage);
            document.addPage(secretPage);
            setRawContent(document, secretPage, "%SECRET-PROPERTY-LEAK\nq Q");

            COSDictionary property = new COSDictionary();
            property.setItem(
                hiddenReference,
                secretPage.getCOSObject().getDictionaryObject(COSName.CONTENTS)
            );
            COSDictionary properties = new COSDictionary();
            properties.setItem(COSName.getPDFName("Span"), property);
            PDResources resources = new PDResources();
            resources.getCOSObject().setItem(COSName.PROPERTIES, properties);
            publicPage.setResources(resources);
            setRawContent(
                document,
                publicPage,
                "/Span << /Lang (en) >> BDC EMC"
            );
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"ranges\",\"ranges\":[\"1\"]}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            assertFalse(
                new String(output, StandardCharsets.ISO_8859_1)
                    .contains("SECRET-PROPERTY-LEAK")
            );
        }
    }

    @Test
    void enforcesPageLimitAgainstMaterializedPageTree() throws Exception {
        properties.setMaxPages(1);
        Path source = temporaryDirectory.resolve("forged-page-count.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.getDocumentCatalog()
                .getPages()
                .getCOSObject()
                .setInt(COSName.COUNT, 1);
            document.save(source.toFile());
        }

        assertEquals(
            "PDF_PAGE_LIMIT_EXCEEDED",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(source, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void rejectsCyclicNamedColorSpacesWithoutPdfBoxRecursion() throws Exception {
        Path source = temporaryDirectory.resolve("cyclic-colorspace.pdf");
        COSName loop = COSName.getPDFName("Loop");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            COSDictionary colorSpaces = new COSDictionary();
            colorSpaces.setItem(loop, loop);
            PDResources resources = new PDResources();
            resources.getCOSObject().setItem(COSName.COLORSPACE, colorSpaces);
            page.setResources(resources);
            setRawContent(document, page, "/Loop cs 0 scn");
            document.save(source.toFile());
        }

        assertEquals(
            "CYCLIC_PDF_RESOURCE",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(source, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void keepsSharedImageColorSpacesContextualAcrossPages() throws Exception {
        Path source = temporaryDirectory.resolve("contextual-image.pdf");
        COSName custom = COSName.getPDFName("Custom");
        COSName imageName = COSName.getPDFName("Im");
        try (PDDocument document = new PDDocument()) {
            BufferedImage imageData = new BufferedImage(
                1,
                1,
                BufferedImage.TYPE_INT_RGB
            );
            PDImageXObject image = LosslessFactory.createFromImage(
                document,
                imageData
            );
            image.getCOSObject().setItem(COSName.COLORSPACE, custom);

            PDPage rgbPage = new PDPage();
            PDPage grayPage = new PDPage();
            document.addPage(rgbPage);
            document.addPage(grayPage);
            for (PDPage page : List.of(rgbPage, grayPage)) {
                PDResources resources = new PDResources();
                resources.put(imageName, image);
                COSDictionary colorSpaces = new COSDictionary();
                colorSpaces.setItem(
                    custom,
                    page == rgbPage ? COSName.DEVICERGB : COSName.DEVICEGRAY
                );
                resources.getCOSObject().setItem(
                    COSName.COLORSPACE,
                    colorSpaces
                );
                page.setResources(resources);
                setRawContent(document, page, "q /Im Do Q");
            }
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"ranges\",\"ranges\":[\"1-2\"]}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            try (PDDocument split = Loader.loadPDF(output)) {
                for (int index = 0; index < 2; index++) {
                    COSBase imageColorSpace = split.getPage(index)
                        .getResources()
                        .getXObject(imageName)
                        .getCOSObject()
                        .getDictionaryObject(COSName.COLORSPACE);
                    assertEquals(custom, imageColorSpace);
                }
                assertEquals(
                    COSName.DEVICERGB,
                    split.getPage(0).getResources().getCOSObject()
                        .getCOSDictionary(COSName.COLORSPACE)
                        .getDictionaryObject(custom)
                );
                assertEquals(
                    COSName.DEVICEGRAY,
                    split.getPage(1).getResources().getCOSObject()
                        .getCOSDictionary(COSName.COLORSPACE)
                        .getDictionaryObject(custom)
                );
            }
        }
    }

    @Test
    void retainsNestedColorSpaceDependenciesForInlineImages() throws Exception {
        Path source = temporaryDirectory.resolve("inline-image-colorspace.pdf");
        COSName base = COSName.getPDFName("Base");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            COSDictionary colorSpaces = new COSDictionary();
            colorSpaces.setItem(base, COSName.DEVICERGB);
            PDResources resources = new PDResources();
            resources.getCOSObject().setItem(COSName.COLORSPACE, colorSpaces);
            page.setResources(resources);
            setRawContent(
                document,
                page,
                "BI /W 1 /H 1 /BPC 8 "
                    + "/CS [/Indexed /Base 1 <FF000000FF00>] ID \0 EI"
            );
            document.save(source.toFile());
        }

        OperationOutput zip = operation.execute(context(
            source,
            "{\"mode\":\"individual\"}"
        )).getFirst();
        try (ZipFile archive = new ZipFile(zip.path().toFile())) {
            byte[] output = read(archive, archive.entries().nextElement());
            try (PDDocument split = Loader.loadPDF(output)) {
                assertTrue(
                    split.getPage(0).getResources().getCOSObject()
                        .getCOSDictionary(COSName.COLORSPACE)
                        .containsKey(base)
                );
            }
        }
    }

    @Test
    void rejectsUnsupportedInheritedType3AndGroupColorSpaces() throws Exception {
        Path type3Source = temporaryDirectory.resolve("type3-inherited.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            COSDictionary fonts = new COSDictionary();
            fonts.setItem(
                COSName.getPDFName("F1"),
                inheritedType3Font(document)
            );
            PDResources resources = new PDResources();
            resources.getCOSObject().setItem(COSName.FONT, fonts);
            page.setResources(resources);
            setRawContent(document, page, "BT /F1 12 Tf (A) Tj ET");
            document.save(type3Source.toFile());
        }
        assertEquals(
            "UNSUPPORTED_TYPE3_FONT",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(
                    type3Source,
                    "{\"mode\":\"individual\"}"
                ))
            ).getCode()
        );

        Path stateSource = temporaryDirectory.resolve("type3-state.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            COSArray fontSetting = new COSArray();
            fontSetting.add(inheritedType3Font(document));
            fontSetting.add(COSInteger.get(12));
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.getCOSObject().setItem(COSName.FONT, fontSetting);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Gs"), state);
            page.setResources(resources);
            setRawContent(document, page, "/Gs gs");
            document.save(stateSource.toFile());
        }
        assertEquals(
            "UNSUPPORTED_TYPE3_FONT",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(
                    stateSource,
                    "{\"mode\":\"individual\"}"
                ))
            ).getCode()
        );

        Path groupSource = temporaryDirectory.resolve("group-colorspace.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            COSDictionary group = new COSDictionary();
            group.setItem(COSName.S, COSName.TRANSPARENCY);
            group.setItem(COSName.CS, COSName.getPDFName("Custom"));
            page.getCOSObject().setItem(COSName.GROUP, group);
            document.save(groupSource.toFile());
        }
        assertEquals(
            "UNSUPPORTED_TRANSPARENCY_GROUP_COLOR_SPACE",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(
                    groupSource,
                    "{\"mode\":\"individual\"}"
                ))
            ).getCode()
        );
    }

    @Test
    void rejectsOptionalContentAndExcessiveResourceComplexity() throws Exception {
        Path layered = temporaryDirectory.resolve("layered.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.getDocumentCatalog().setOCProperties(
                new PDOptionalContentProperties()
            );
            document.save(layered.toFile());
        }
        assertEquals(
            "OPTIONAL_CONTENT_UNSUPPORTED",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(layered, "{\"mode\":\"individual\"}"))
            ).getCode()
        );

        properties.setMaxContentTokens(5);
        Path tokenDense = temporaryDirectory.resolve("token-dense.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            setRawContent(document, page, "q Q ".repeat(20));
            document.save(tokenDense.toFile());
        }
        assertEquals(
            "PDF_CONTENT_COMPLEXITY_LIMIT_EXCEEDED",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(tokenDense, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    @Test
    void boundsNestedContentObjectsAndContentStreamArrays() throws Exception {
        properties.setMaxContentTokens(5);
        Path nested = temporaryDirectory.resolve("nested-operands.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            setRawContent(document, page, "[0 0 0 0 0 0] TJ");
            document.save(nested.toFile());
        }
        assertEquals(
            "PDF_CONTENT_COMPLEXITY_LIMIT_EXCEEDED",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(nested, "{\"mode\":\"individual\"}"))
            ).getCode()
        );

        properties.setMaxContentTokens(250_000);
        properties.setMaxContentStreamsPerPage(2);
        Path streams = temporaryDirectory.resolve("many-content-streams.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            List<org.apache.pdfbox.pdmodel.common.PDStream> contents =
                new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                var stream = new org.apache.pdfbox.pdmodel.common.PDStream(document);
                try (var output = stream.createOutputStream()) {
                    output.write("q Q".getBytes(StandardCharsets.US_ASCII));
                }
                contents.add(stream);
            }
            page.setContents(contents);
            document.save(streams.toFile());
        }
        assertEquals(
            "PDF_CONTENT_STREAM_LIMIT_EXCEEDED",
            assertThrows(
                OperationException.class,
                () -> operation.execute(context(streams, "{\"mode\":\"individual\"}"))
            ).getCode()
        );
    }

    private Path sourcePdf() throws Exception {
        return PdfTestFixtures.coloredPdf(
            temporaryDirectory.resolve("source-" + java.util.UUID.randomUUID() + ".pdf"),
            List.of(
                new PdfTestFixtures.PageSpec(101, 200, Color.RED),
                new PdfTestFixtures.PageSpec(102, 200, Color.GREEN),
                new PdfTestFixtures.PageSpec(103, 200, Color.BLUE),
                new PdfTestFixtures.PageSpec(104, 200, Color.YELLOW),
                new PdfTestFixtures.PageSpec(105, 200, Color.MAGENTA)
            )
        );
    }

    private OperationContext context(Path source, String options) throws Exception {
        Path workspace = Files.createTempDirectory(temporaryDirectory, "split-context-");
        return new OperationContext(
            java.util.UUID.randomUUID(),
            objectMapper.readTree(options),
            List.of(new OperationInput(
                1,
                source,
                "source.pdf",
                "application/pdf",
                Files.size(source),
                "test-sha"
            )),
            workspace,
            ignored -> {
            },
            () -> false
        );
    }

    private byte[] read(ZipFile archive, ZipEntry entry) throws Exception {
        try (var input = archive.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private List<Float> widths(PDDocument document) {
        List<Float> widths = new ArrayList<>();
        document.getPages().forEach(page -> widths.add(page.getMediaBox().getWidth()));
        return widths;
    }

    private void assertCenterColor(BufferedImage image, Color expected) {
        Color actual = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
        assertTrue(Math.abs(actual.getRed() - expected.getRed()) <= 2);
        assertTrue(Math.abs(actual.getGreen() - expected.getGreen()) <= 2);
        assertTrue(Math.abs(actual.getBlue() - expected.getBlue()) <= 2);
    }

    private void writeText(
            PDDocument document,
            PDPage page,
            String text,
            boolean compress) throws Exception {
        try (PDPageContentStream content = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.OVERWRITE,
                compress)) {
            content.beginText();
            content.setFont(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                18
            );
            content.newLineAtOffset(72, 700);
            content.showText(text);
            content.endText();
        }
    }

    private void setRawContent(
            PDDocument document,
            PDPage page,
            String content) throws Exception {
        org.apache.pdfbox.pdmodel.common.PDStream stream =
            new org.apache.pdfbox.pdmodel.common.PDStream(document);
        try (var output = stream.createOutputStream()) {
            output.write(content.getBytes(StandardCharsets.US_ASCII));
        }
        page.setContents(stream);
    }

    private COSDictionary inheritedType3Font(PDDocument document)
            throws Exception {
        COSDictionary font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, COSName.TYPE3);
        font.setInt(COSName.FIRST_CHAR, 65);
        font.setInt(COSName.LAST_CHAR, 65);
        COSArray widths = new COSArray();
        widths.add(COSInteger.get(500));
        font.setItem(COSName.WIDTHS, widths);
        COSArray fontBBox = new COSArray();
        for (int value : List.of(0, 0, 1000, 1000)) {
            fontBBox.add(COSInteger.get(value));
        }
        font.setItem(COSName.FONT_BBOX, fontBBox);
        COSArray fontMatrix = new COSArray();
        for (float value : new float[] {0.001f, 0, 0, 0.001f, 0, 0}) {
            fontMatrix.add(new org.apache.pdfbox.cos.COSFloat(value));
        }
        font.setItem(COSName.FONT_MATRIX, fontMatrix);
        COSDictionary charProcs = new COSDictionary();
        var charProc = new org.apache.pdfbox.pdmodel.common.PDStream(document);
        try (var output = charProc.createOutputStream()) {
            output.write(
                "0 0 1000 1000 re f".getBytes(StandardCharsets.US_ASCII)
            );
        }
        charProcs.setItem(COSName.getPDFName("A"), charProc);
        font.setItem(COSName.CHAR_PROCS, charProcs);
        COSDictionary encoding = new COSDictionary();
        COSArray differences = new COSArray();
        differences.add(COSInteger.get(65));
        differences.add(COSName.getPDFName("A"));
        encoding.setItem(COSName.DIFFERENCES, differences);
        font.setItem(COSName.ENCODING, encoding);
        return font;
    }

    private COSDictionary basicAxialShading() {
        COSDictionary shading = new COSDictionary();
        shading.setInt(COSName.SHADING_TYPE, 2);
        shading.setItem(COSName.COLORSPACE, COSName.DEVICERGB);
        COSArray coordinates = new COSArray();
        for (int value : List.of(0, 0, 100, 100)) {
            coordinates.add(COSInteger.get(value));
        }
        shading.setItem(COSName.COORDS, coordinates);
        return shading;
    }
}
