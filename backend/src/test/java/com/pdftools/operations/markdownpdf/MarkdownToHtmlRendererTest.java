package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownToHtmlRendererTest {

    static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII="
    );

    @Test
    void escapesRawHtmlAndEmitsNoScripts() {
        String html = render("""
            # Report

            <script>alert(1)</script>

            Inline <img src=x onerror="alert(2)"> and <b>bold</b>.
            """);

        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<img src=x"));
        assertFalse(html.contains("<b>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt;"));
        assertTrue(html.contains(
            "content=\"" + MarkdownToHtmlRenderer.CONTENT_SECURITY_POLICY
        ));
        assertTrue(html.contains("<title>Report</title>"));
    }

    @Test
    void keepsOnlyWebMailAndFragmentLinks() {
        String html = render("""
            [web](https://example.com/a?b=1) [mail](mailto:team@example.com)
            [section](#report) [script](javascript:alert(1))
            [file](file:///etc/passwd) [relative](docs/other.md)
            <https://example.com/auto> <team@example.com>
            [encoded](javascript&colon;alert(1);'@x')
            [encodedFile](file&colon;&sol;&sol;&sol;etc&sol;passwd&num;@x)
            [bareAt](foo@bar) [goodRef][good] [badRef][bad]

            [good]: https://example.com/ref
            [bad]: javascript&colon;alert(2)
            """);

        assertTrue(html.contains("href=\"https://example.com/a?b=1\""));
        assertTrue(html.contains("href=\"mailto:team@example.com\""));
        assertTrue(html.contains("href=\"#report\""));
        assertTrue(html.contains("href=\"https://example.com/auto\""));
        assertTrue(html.contains("href=\"mailto:team@example.com\">team@"));
        assertTrue(html.contains("href=\"https://example.com/ref\""));
        assertFalse(html.contains("javascript:"));
        assertFalse(html.contains("file:"));
        assertFalse(html.contains("docs/other.md"));
        assertFalse(html.contains("href=\"foo@bar\""));
        assertTrue(html.contains("relative"));
        assertTrue(html.contains("encodedFile"));
        assertTrue(html.contains("badRef"));
    }

    @Test
    void embedsDataImagesAndReplacesRemoteOrUnsupportedImagesWithAltText() {
        String png = Base64.getEncoder().encodeToString(PNG);
        String svg = Base64.getEncoder().encodeToString(
            "<svg xmlns='http://www.w3.org/2000/svg'><script/></svg>"
                .getBytes()
        );
        String html = render("""
            ![dot](data:image/png;base64,%s)
            ![remote logo](https://example.com/logo.png)
            ![vector](data:image/webp;base64,%s)
            ![ref image][logo]

            [logo]: https://example.com/ref.png
            """.formatted(png, svg));

        assertTrue(html.contains(
            "src=\"data:image/png;base64," + png + "\" alt=\"dot\""
        ));
        assertTrue(html.contains(
            "<span class=\"image-unavailable\">remote logo</span>"
        ));
        assertTrue(html.contains(
            "<span class=\"image-unavailable\">vector</span>"
        ));
        assertTrue(html.contains(
            "<span class=\"image-unavailable\">ref image</span>"
        ));
        assertFalse(html.contains("example.com/logo.png"));
        assertFalse(html.contains("example.com/ref.png"));
    }

    @Test
    void rendersGithubFlavoredStructureAndHidesFrontMatter() {
        String html = render("""
            ---
            title: Hidden metadata
            ---
            # Title

            | Name | Value |
            | ---- | ----: |
            | a    | 1     |

            - [x] done
            - [ ] todo

            ~~old~~ text[^1]

            ```java
            int x = 1 < 2 ? 1 : 0;
            ```

            [^1]: A footnote.
            """);

        assertFalse(html.contains("Hidden metadata"));
        assertTrue(html.contains("<h1 id=\"title\">Title</h1>"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("task-list-item"));
        assertTrue(html.contains("<del>old</del>"));
        assertTrue(html.contains("class=\"language-java\""));
        assertTrue(html.contains("1 &lt; 2"));
        assertTrue(html.contains("footnote"));
    }

    @Test
    void rejectsEmbeddedImagesBeyondTheRenderedSizeLimit() {
        String png = Base64.getEncoder().encodeToString(PNG);
        String markdown = ("![a](data:image/png;base64," + png + ")\n")
            .repeat(20);
        OperationException failure = assertThrows(
            OperationException.class,
            () -> new MarkdownToHtmlRenderer(new MarkdownImageEmbedder(
                null,
                "",
                10,
                1024,
                png.length() * 5L
            )).render(markdown)
        );

        assertEquals("MARKDOWN_RENDERED_HTML_TOO_LARGE", failure.getCode());
    }

    private String render(String markdown) {
        return new MarkdownToHtmlRenderer(new MarkdownImageEmbedder(
            null,
            "",
            10,
            1024 * 1024,
            10L * 1024 * 1024
        )).render(markdown);
    }
}
