package com.pdftools.operations.markdownpdf;

import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub alert ({@code > [!NOTE]}) block. {@link #apply(Node)} converts
 * matching block quotes after parsing: the marker must be alone on the
 * quote's first line and followed by content, as on GitHub.
 */
final class GitHubAlert extends Block {

    enum Type {
        NOTE("Note",
            "<circle cx=\"8\" cy=\"8\" r=\"6.25\"/>"
                + "<path d=\"M8 7.25v4\"/><path d=\"M8 4.75h.01\"/>"),
        TIP("Tip",
            "<path d=\"M5.75 10.5a4.25 4.25 0 1 1 4.5 0V12h-4.5z\"/>"
                + "<path d=\"M6.25 14.25h3.5\"/>"),
        IMPORTANT("Important",
            "<path d=\"M2.5 3.25h11v7.5H7.5l-3 2.5v-2.5h-2z\"/>"
                + "<path d=\"M8 5.25v2.5\"/><path d=\"M8 9.25h.01\"/>"),
        WARNING("Warning",
            "<path d=\"M8 2.25 14.25 13.5H1.75z\"/>"
                + "<path d=\"M8 6.5V9.5\"/><path d=\"M8 11.5h.01\"/>"),
        CAUTION("Caution",
            "<path d=\"M5.4 1.75h5.2l3.65 3.65v5.2l-3.65 3.65H5.4"
                + "L1.75 10.6V5.4z\"/>"
                + "<path d=\"M8 4.75V8.5\"/><path d=\"M8 10.75h.01\"/>");

        private final String title;
        private final String icon;

        Type(String title, String iconPaths) {
            this.title = title;
            this.icon = "<svg class=\"markdown-alert-icon\" viewBox=\"0 0 16 16\""
                + " width=\"16\" height=\"16\" fill=\"none\""
                + " stroke=\"currentColor\" stroke-width=\"1.5\""
                + " stroke-linecap=\"round\" stroke-linejoin=\"round\""
                + " aria-hidden=\"true\">" + iconPaths + "</svg>";
        }

        String cssName() {
            return name().toLowerCase(Locale.ROOT);
        }

        String title() {
            return title;
        }

        String icon() {
            return icon;
        }
    }

    private static final Pattern MARKER = Pattern.compile(
        "\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]",
        Pattern.CASE_INSENSITIVE
    );

    private final Type type;

    private GitHubAlert(Type type) {
        this.type = type;
    }

    Type type() {
        return type;
    }

    @Override
    public BasedSequence[] getSegments() {
        return EMPTY_SEGMENTS;
    }

    static void apply(Node parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            Node converted = child instanceof BlockQuote quote
                ? convert(quote)
                : null;
            apply(converted == null ? child : converted);
            child = next;
        }
    }

    private static GitHubAlert convert(BlockQuote quote) {
        if (!(quote.getFirstChild() instanceof Paragraph paragraph)
                || paragraph.getLineCount() == 0) {
            return null;
        }
        Matcher marker = MARKER.matcher(
            paragraph.getLineChars(0).toString().strip()
        );
        boolean hasContent = paragraph.getLineCount() > 1
            || paragraph.getNext() != null;
        if (!marker.matches() || !hasContent) {
            return null;
        }
        if (paragraph.getLineCount() > 1) {
            int contentStart = paragraph.getLineChars(1).getStartOffset();
            Node inline = paragraph.getFirstChild();
            while (inline != null && inline.getEndOffset() <= contentStart) {
                Node next = inline.getNext();
                inline.unlink();
                inline = next;
            }
        } else {
            paragraph.unlink();
        }
        GitHubAlert alert = new GitHubAlert(
            Type.valueOf(marker.group(1).toUpperCase(Locale.ROOT))
        );
        quote.insertBefore(alert);
        alert.takeChildren(quote);
        quote.unlink();
        return alert;
    }
}
