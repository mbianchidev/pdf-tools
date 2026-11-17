package com.pdftools.operations.markdownpdf;

import com.pdftools.operations.OperationException;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.ImageRef;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.emoji.Emoji;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.emoji.EmojiImageType;
import com.vladsch.flexmark.ext.emoji.EmojiShortcutType;
import com.vladsch.flexmark.ext.emoji.internal.EmojiResolvedShortcut;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.TextCollectingVisitor;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import com.vladsch.flexmark.util.sequence.Escaping;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Renders untrusted Markdown into one self-contained, script-free HTML
 * document for the Chromium print sidecar.
 */
final class MarkdownToHtmlRenderer {

    /**
     * Autolinks outside http(s), mailto, in-document fragments, www, and bare
     * e-mail addresses render as plain text. flexmark leaves the URL of
     * {@code <user@example.com>} mail links empty, so empty URLs pass. flexmark
     * matches this against the raw source, so inline and reference links are
     * re-checked after entity decoding by {@link SafeNodeRenderer}.
     */
    static final String SUPPRESSED_LINKS =
        "(?is)(?!(?:https?://|mailto:|#|www\\.)|[^\\s:/?#@&\\\\]+@[^\\s:/?#@&\\\\]+$|$).*";

    private static final Pattern ALLOWED_HREF =
        Pattern.compile("^(?:https?://|mailto:|#)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_SCHEME =
        Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");

    static final String CONTENT_SECURITY_POLICY = String.join("; ",
        "default-src 'none'",
        "img-src data:",
        "style-src 'unsafe-inline'",
        "base-uri 'none'",
        "form-action 'none'"
    );

    private static final EmojiShortcutType EMOJI_SHORTCUTS =
        EmojiShortcutType.ANY_GITHUB_PREFERRED;
    private static final String STYLESHEET = loadStylesheet();

    private final Parser parser;
    private final HtmlRenderer renderer;

    MarkdownToHtmlRenderer(MarkdownImageEmbedder images) {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
            TablesExtension.create(),
            TableParagraphSplitter.EXTENSION,
            GitHubStrikethroughExtension.create(),
            TaskListExtension.create(),
            AutolinkExtension.create(),
            FootnoteExtension.create(),
            YamlFrontMatterExtension.create(),
            EmojiExtension.create()
        ));
        options.set(EmojiExtension.USE_SHORTCUT_TYPE, EMOJI_SHORTCUTS);
        options.set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_ONLY);
        options.set(TablesExtension.COLUMN_SPANS, false);
        options.set(TablesExtension.APPEND_MISSING_COLUMNS, true);
        options.set(TablesExtension.DISCARD_EXTRA_COLUMNS, true);
        options.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true);
        options.set(TablesExtension.MIN_SEPARATOR_DASHES, 1);
        options.set(TablesExtension.MIN_HEADER_ROWS, 1);
        options.set(TablesExtension.MAX_HEADER_ROWS, 1);
        options.set(TablesExtension.WITH_CAPTION, false);
        options.set(HtmlRenderer.ESCAPE_HTML, true);
        options.set(HtmlRenderer.SUPPRESSED_LINKS, SUPPRESSED_LINKS);
        options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
        options.set(HtmlRenderer.RENDER_HEADER_ID, true);
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options)
            .nodeRendererFactory(ignored -> new SafeNodeRenderer(images))
            .build();
    }

    String render(String markdown) {
        try {
            Document document = parser.parse(markdown);
            GitHubAlert.apply(document);
            String body = renderer.render(document);
            return "<!doctype html>\n<html>\n<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\""
                + CONTENT_SECURITY_POLICY + "\">\n"
                + "<title>" + Escaping.escapeHtml(title(document), false)
                + "</title>\n"
                + "<style>\n" + STYLESHEET + "</style>\n"
                + "</head>\n<body>\n<main class=\"markdown-body\">\n"
                + body
                + "</main>\n</body>\n</html>\n";
        } catch (StackOverflowError error) {
            throw new OperationException(
                "MARKDOWN_TOO_COMPLEX",
                "The Markdown document is nested too deeply to render"
            );
        }
    }

    static OperationException renderedTooLarge() {
        return new OperationException(
            "MARKDOWN_RENDERED_HTML_TOO_LARGE",
            "The rendered document exceeds the HTML renderer input limit"
        );
    }

    private static String title(Document document) {
        for (Node node : document.getChildren()) {
            if (node instanceof Heading heading && heading.getLevel() == 1) {
                String text = new TextCollectingVisitor()
                    .collectAndGetText(heading)
                    .strip();
                if (!text.isEmpty()) {
                    return text.length() > 200
                        ? text.substring(0, 200)
                        : text;
                }
            }
        }
        return "Document";
    }

    private static String loadStylesheet() {
        try (InputStream input = MarkdownToHtmlRenderer.class
                .getResourceAsStream("/markdownpdf/markdown-print.css")) {
            if (input == null) {
                throw new IllegalStateException(
                    "Markdown print stylesheet is missing"
                );
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static boolean allowedHref(String url) {
        return ALLOWED_HREF.matcher(url).find();
    }

    /** Document-relative targets cannot resolve inside a standalone PDF. */
    private static boolean relativeHref(String url) {
        String trimmed = url.strip();
        return !trimmed.isEmpty()
            && !trimmed.startsWith("//")
            && !URL_SCHEME.matcher(trimmed).find();
    }

    private static void renderUnlinked(
            Node node,
            String url,
            NodeRendererContext context,
            HtmlWriter html) {
        context.renderChildren(node);
        if (relativeHref(url)) {
            html.text(" ");
            html.attr("class", "link-target").withAttr().tag("span");
            html.text("(" + url.strip() + ")");
            html.tag("/span");
        }
    }

    private static final class SafeNodeRenderer implements NodeRenderer {

        private final MarkdownImageEmbedder images;

        private SafeNodeRenderer(MarkdownImageEmbedder images) {
            this.images = images;
        }

        @Override
        public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
            return Set.of(
                new NodeRenderingHandler<>(Image.class, this::render),
                new NodeRenderingHandler<>(ImageRef.class, this::render),
                new NodeRenderingHandler<>(Link.class, this::render),
                new NodeRenderingHandler<>(LinkRef.class, this::render),
                new NodeRenderingHandler<>(GitHubAlert.class, this::render),
                new NodeRenderingHandler<>(Emoji.class, this::render)
            );
        }

        /**
         * flexmark checks {@link #SUPPRESSED_LINKS} against the raw URL but
         * emits the entity-decoded one, so the decoded href is allowlisted
         * here before delegating to the core renderer.
         */
        private void render(
                Link node,
                NodeRendererContext context,
                HtmlWriter html) {
            String url = node.getUrl().unescape();
            if (allowedHref(url)) {
                context.delegateRender();
            } else {
                renderUnlinked(node, url, context, html);
            }
        }

        private void render(
                LinkRef node,
                NodeRendererContext context,
                HtmlWriter html) {
            Reference reference = node.isDefined()
                ? node.getReferenceNode(
                    Parser.REFERENCES.get(context.getDocument())
                )
                : null;
            String url = reference == null
                ? null
                : reference.getUrl().unescape();
            if (url == null || allowedHref(url)) {
                context.delegateRender();
            } else {
                renderUnlinked(node, url, context, html);
            }
        }

        /** Lets resolved shortcode emoji prefer the color emoji font. */
        private void render(
                Emoji node,
                NodeRendererContext context,
                HtmlWriter html) {
            EmojiResolvedShortcut shortcut = EmojiResolvedShortcut.getEmojiText(
                node,
                EMOJI_SHORTCUTS,
                EmojiImageType.UNICODE_ONLY,
                ""
            );
            if (shortcut.emoji == null || !shortcut.isUnicode) {
                context.delegateRender();
                return;
            }
            html.attr("class", "emoji").withAttr().tag("span");
            context.delegateRender();
            html.tag("/span");
        }

        private void render(
                GitHubAlert node,
                NodeRendererContext context,
                HtmlWriter html) {
            GitHubAlert.Type type = node.type();
            html.line();
            html.attr("class", "markdown-alert markdown-alert-" + type.cssName())
                .withAttr()
                .tag("div")
                .line();
            html.attr("class", "markdown-alert-title").withAttr().tag("p");
            html.raw(type.icon());
            html.text(type.title());
            html.tag("/p").line();
            context.renderChildren(node);
            html.tag("/div").line();
        }

        private void render(
                Image node,
                NodeRendererContext context,
                HtmlWriter html) {
            renderImage(
                node,
                node.getUrl().unescape(),
                node.getTitle(),
                html
            );
        }

        private void render(
                ImageRef node,
                NodeRendererContext context,
                HtmlWriter html) {
            Reference reference = node.isDefined()
                ? node.getReferenceNode(
                    Parser.REFERENCES.get(context.getDocument())
                )
                : null;
            if (reference == null) {
                html.text(node.getChars().unescape());
                return;
            }
            renderImage(
                node,
                reference.getUrl().unescape(),
                reference.getTitle(),
                html
            );
        }

        private void renderImage(
                Node node,
                String url,
                BasedSequence title,
                HtmlWriter html) {
            String alt = new TextCollectingVisitor().collectAndGetText(node);
            String source = images.resolve(url);
            if (source == null) {
                html.attr("class", "image-unavailable").withAttr().tag("span");
                html.text(alt.isBlank() ? "[image]" : alt);
                html.tag("/span");
                return;
            }
            html.attr("src", source).attr("alt", alt);
            if (title.isNotNull()) {
                html.attr("title", title.unescape());
            }
            html.withAttr().tagVoid("img");
        }
    }
}
