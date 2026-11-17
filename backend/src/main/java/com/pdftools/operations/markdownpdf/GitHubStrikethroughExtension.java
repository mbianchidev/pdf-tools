package com.pdftools.operations.markdownpdf;

import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.internal.StrikethroughNodeRenderer;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.InlineParser;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.core.delimiter.Delimiter;
import com.vladsch.flexmark.parser.delimiter.DelimiterProcessor;
import com.vladsch.flexmark.parser.delimiter.DelimiterRun;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.sequence.BasedSequence;

/**
 * GitHub strikethrough: a matched run of one or two tildes. flexmark's
 * StrikethroughExtension only accepts two, and flexmark allows a single
 * delimiter processor per character, so this replaces its parser half and
 * reuses its HTML renderer.
 */
final class GitHubStrikethroughExtension
        implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

    static GitHubStrikethroughExtension create() {
        return new GitHubStrikethroughExtension();
    }

    @Override
    public void parserOptions(MutableDataHolder options) {
    }

    @Override
    public void extend(Parser.Builder builder) {
        builder.customDelimiterProcessor(new Processor());
    }

    @Override
    public void rendererOptions(MutableDataHolder options) {
    }

    @Override
    public void extend(HtmlRenderer.Builder builder, String rendererType) {
        builder.nodeRendererFactory(new StrikethroughNodeRenderer.Factory());
    }

    private static final class Processor implements DelimiterProcessor {

        @Override
        public char getOpeningCharacter() {
            return '~';
        }

        @Override
        public char getClosingCharacter() {
            return '~';
        }

        @Override
        public int getMinLength() {
            return 1;
        }

        @Override
        public boolean canBeOpener(
                String before,
                String after,
                boolean leftFlanking,
                boolean rightFlanking,
                boolean beforeIsPunctuation,
                boolean afterIsPunctuation,
                boolean beforeIsWhitespace,
                boolean afterIsWhiteSpace) {
            return leftFlanking;
        }

        @Override
        public boolean canBeCloser(
                String before,
                String after,
                boolean leftFlanking,
                boolean rightFlanking,
                boolean beforeIsPunctuation,
                boolean afterIsPunctuation,
                boolean beforeIsWhitespace,
                boolean afterIsWhiteSpace) {
            return rightFlanking;
        }

        @Override
        public Node unmatchedDelimiterNode(
                InlineParser inlineParser,
                DelimiterRun delimiter) {
            return null;
        }

        @Override
        public boolean skipNonOpenerCloser() {
            return false;
        }

        @Override
        public int getDelimiterUse(DelimiterRun opener, DelimiterRun closer) {
            return opener.length() == closer.length() && opener.length() <= 2
                ? opener.length()
                : 0;
        }

        @Override
        public void process(
                Delimiter opener,
                Delimiter closer,
                int delimitersUsed) {
            Strikethrough strikethrough = new Strikethrough(
                opener.getTailChars(delimitersUsed),
                BasedSequence.NULL,
                closer.getLeadChars(delimitersUsed)
            );
            opener.moveNodesBetweenDelimitersTo(strikethrough, closer);
        }
    }
}
