package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownToHtmlWorkerTest {

    @TempDir
    Path workspace;

    @Test
    void rendersPdfToMarkdownBundleWithInlinedImages() throws Exception {
        Path bundle = zip("bundle.zip", Map.of(
            "document.md",
            "# Round trip\n\n![page image](images/page-001-image-001.png)\n"
                + "![missing](images/none.png)\n",
            "images/page-001-image-001.png",
            MarkdownToHtmlRendererTest.PNG
        ));

        String html = convert(bundle, MarkdownSourceKind.BUNDLE, limits());

        assertTrue(html.contains(
            "src=\"data:image/png;base64,"
                + Base64.getEncoder().encodeToString(
                    MarkdownToHtmlRendererTest.PNG
                )
        ));
        assertTrue(html.contains(
            "<span class=\"image-unavailable\">missing</span>"
        ));
        assertFalse(html.contains("images/page-001"));
    }

    @Test
    void resolvesImagesInsideSingleFolderBundlesButNotOutsideTheBundle()
            throws Exception {
        Path bundle = zip("folder.zip", Map.of(
            "notes/readme.md",
            "![inside](img/dot.png) ![escape](../../secret.png) "
                + "![text](img/fake.png)",
            "notes/img/dot.png",
            MarkdownToHtmlRendererTest.PNG,
            "notes/img/fake.png",
            "not an image",
            "__MACOSX/notes/._readme.md",
            "resource fork"
        ));

        String html = convert(bundle, MarkdownSourceKind.BUNDLE, limits());

        assertTrue(html.contains("alt=\"inside\""));
        assertTrue(html.contains(
            "<span class=\"image-unavailable\">escape</span>"
        ));
        assertTrue(html.contains(
            "<span class=\"image-unavailable\">text</span>"
        ));
    }

    @Test
    void rejectsUnsafeAmbiguousOrDocumentlessBundles() throws Exception {
        assertCode(
            "INVALID_MARKDOWN_BUNDLE",
            zip("unsafe.zip", Map.of(
                "document.md", "# ok",
                "../evil.png", MarkdownToHtmlRendererTest.PNG
            ))
        );
        assertCode(
            "MARKDOWN_BUNDLE_AMBIGUOUS_DOCUMENT",
            zip("ambiguous.zip", Map.of("a.md", "# a", "b.md", "# b"))
        );
        assertCode(
            "MARKDOWN_BUNDLE_MISSING_DOCUMENT",
            zip("empty.zip", Map.of(
                "images/a.png", MarkdownToHtmlRendererTest.PNG
            ))
        );
        Path notZip = workspace.resolve("not.zip");
        Files.writeString(notZip, "plain text");
        assertCode("INVALID_MARKDOWN_BUNDLE", notZip);
    }

    @Test
    void enforcesBundleEntryAndImageBudgets() throws Exception {
        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put("document.md", "![a](a.png) ![b](b.png)");
        entries.put("a.png", MarkdownToHtmlRendererTest.PNG);
        entries.put("b.png", MarkdownToHtmlRendererTest.PNG);
        Path bundle = zip("budget.zip", entries);

        OperationException entryLimit = assertThrows(
            OperationException.class,
            () -> convert(bundle, MarkdownSourceKind.BUNDLE, limits(2, 10, 1024))
        );
        assertEquals(
            "MARKDOWN_BUNDLE_ENTRY_LIMIT_EXCEEDED",
            entryLimit.getCode()
        );
        OperationException imageCount = assertThrows(
            OperationException.class,
            () -> convert(bundle, MarkdownSourceKind.BUNDLE, limits(10, 1, 1024))
        );
        assertEquals("MARKDOWN_IMAGE_LIMIT_EXCEEDED", imageCount.getCode());
        OperationException imageBytes = assertThrows(
            OperationException.class,
            () -> convert(bundle, MarkdownSourceKind.BUNDLE, limits(10, 10, 100))
        );
        assertEquals(
            "MARKDOWN_IMAGE_SIZE_LIMIT_EXCEEDED",
            imageBytes.getCode()
        );
    }

    @Test
    void rejectedImagesFallBackToAltTextWithoutUsingImageBudgets()
            throws Exception {
        String svgAsWebp = Base64.getEncoder().encodeToString(
            "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(
                StandardCharsets.UTF_8
            )
        );
        Path bundle = zip("rejected.zip", Map.of(
            "document.md",
            "![ok](ok.png) ![missing](missing.png) ![big text](big.txt) "
                + "![fake](data:image/webp;base64," + svgAsWebp + ") "
                + "![broken](data:image/png;base64,***)",
            "ok.png", MarkdownToHtmlRendererTest.PNG,
            "big.txt", "x".repeat(4096)
        ));

        String html = convert(
            bundle,
            MarkdownSourceKind.BUNDLE,
            limits(10, 1, MarkdownToHtmlRendererTest.PNG.length)
        );

        assertTrue(html.contains("alt=\"ok\""));
        for (String alt : new String[] {"missing", "big text", "fake", "broken"}) {
            assertTrue(html.contains(
                "<span class=\"image-unavailable\">" + alt + "</span>"
            ), alt);
        }
    }

    @Test
    void rejectsInvalidMarkdownText() throws Exception {
        Path invalid = workspace.resolve("invalid.md");
        Files.write(invalid, new byte[] {'#', ' ', (byte) 0xC3, 0x28});
        OperationException encoding = assertThrows(
            OperationException.class,
            () -> convert(invalid, MarkdownSourceKind.MARKDOWN, limits())
        );
        assertEquals("INVALID_MARKDOWN_ENCODING", encoding.getCode());

        Path blank = workspace.resolve("blank.md");
        Files.writeString(blank, "\uFEFF \n\t\n");
        OperationException empty = assertThrows(
            OperationException.class,
            () -> convert(blank, MarkdownSourceKind.MARKDOWN, limits())
        );
        assertEquals("EMPTY_MARKDOWN_DOCUMENT", empty.getCode());
    }

    private void assertCode(String code, Path bundle) {
        OperationException failure = assertThrows(
            OperationException.class,
            () -> convert(bundle, MarkdownSourceKind.BUNDLE, limits())
        );
        assertEquals(code, failure.getCode());
    }

    private String convert(
            Path source,
            MarkdownSourceKind kind,
            MarkdownToHtmlRequest.Limits limits) throws IOException {
        Path output = workspace.resolve("out-" + System.nanoTime() + ".html");
        new MarkdownToHtmlWorker().convert(new MarkdownToHtmlRequest(
            source,
            kind,
            output,
            workspace,
            limits
        ));
        return Files.readString(output);
    }

    private MarkdownToHtmlRequest.Limits limits() {
        return limits(100, 10, 1024 * 1024);
    }

    private MarkdownToHtmlRequest.Limits limits(
            int maxEntries,
            int maxImages,
            long maxImageBytes) {
        return new MarkdownToHtmlRequest.Limits(
            1024 * 1024,
            maxEntries,
            maxImages,
            maxImageBytes,
            10L * 1024 * 1024
        );
    }

    private Path zip(String name, Map<String, ?> entries) throws IOException {
        Path path = workspace.resolve(name);
        try (OutputStream file = Files.newOutputStream(path);
             ZipOutputStream zip = new ZipOutputStream(file)) {
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue() instanceof byte[] bytes
                    ? bytes
                    : entry.getValue().toString()
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }
}
