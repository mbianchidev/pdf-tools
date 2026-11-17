package com.pdftools.operations.markdownpdf;

import com.vladsch.flexmark.parser.InlineParser;
import com.vladsch.flexmark.parser.InlineParserExtension;
import com.vladsch.flexmark.parser.InlineParserExtensionFactory;
import com.vladsch.flexmark.parser.LightInlineParser;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.util.Set;

/**
 * GitHub math: {@code $...$} and {@code $`...`$} inline, {@code $$...$$}
 * display. The TeX source is captured before Markdown inline processing and
 * typeset later by KaTeX in the HTML sidecar. Dollar rules follow pandoc so
 * prices such as "$5 and $10" stay text.
 */
final class GitHubMath extends Node {

    static final Parser.ParserExtension EXTENSION =
        new Parser.ParserExtension() {
            @Override
            public void parserOptions(MutableDataHolder options) {
            }

            @Override
            public void extend(Parser.Builder builder) {
                builder.customInlineParserExtensionFactory(new Factory());
            }
        };

    private final String tex;
    private final boolean display;

    private GitHubMath(BasedSequence chars, String tex, boolean display) {
        super(chars);
        this.tex = tex;
        this.display = display;
    }

    String tex() {
        return tex;
    }

    boolean display() {
        return display;
    }

    @Override
    public BasedSequence[] getSegments() {
        return EMPTY_SEGMENTS;
    }

    record Match(int contentStart, int contentEnd, int end, boolean display) {
    }

    /** Finds math starting at {@code start}, which must hold a dollar. */
    static Match find(CharSequence text, int start) {
        int length = text.length();
        if (start + 1 < length && text.charAt(start + 1) == '`') {
            for (int index = start + 2; index + 1 < length; index++) {
                if (text.charAt(index) == '`' && text.charAt(index + 1) == '$') {
                    return index > start + 2
                        ? new Match(start + 2, index, index + 2, false)
                        : null;
                }
            }
            return null;
        }
        if (start + 1 < length && text.charAt(start + 1) == '$') {
            for (int index = start + 2; index + 1 < length; index++) {
                if (text.charAt(index) == '$'
                        && text.charAt(index + 1) == '$'
                        && !escaped(text, index)) {
                    return isBlank(text, start + 2, index)
                        ? null
                        : new Match(start + 2, index, index + 2, true);
                }
            }
            return null;
        }
        if (start + 1 >= length || Character.isWhitespace(text.charAt(start + 1))) {
            return null;
        }
        for (int index = start + 1; index < length; index++) {
            if (text.charAt(index) != '$' || escaped(text, index)) {
                continue;
            }
            boolean closes = !Character.isWhitespace(text.charAt(index - 1))
                && (index + 1 >= length
                    || !Character.isDigit(text.charAt(index + 1)));
            return closes && index > start + 1
                ? new Match(start + 1, index, index + 1, false)
                : null;
        }
        return null;
    }

    private static boolean escaped(CharSequence text, int index) {
        int backslashes = 0;
        for (int cursor = index - 1;
                cursor >= 0 && text.charAt(cursor) == '\\';
                cursor--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static boolean isBlank(CharSequence text, int start, int end) {
        for (int index = start; index < end; index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static final class MathParser implements InlineParserExtension {

        @Override
        public void finalizeDocument(InlineParser inlineParser) {
        }

        @Override
        public void finalizeBlock(InlineParser inlineParser) {
        }

        @Override
        public boolean parse(LightInlineParser inlineParser) {
            BasedSequence input = inlineParser.getInput();
            int start = inlineParser.getIndex();
            Match match = find(input, start);
            if (match == null) {
                return false;
            }
            inlineParser.flushTextNode();
            inlineParser.getBlock().appendChild(new GitHubMath(
                input.subSequence(start, match.end()),
                input.subSequence(match.contentStart(), match.contentEnd())
                    .toString()
                    .strip(),
                match.display()
            ));
            inlineParser.setIndex(match.end());
            return true;
        }
    }

    private static final class Factory implements InlineParserExtensionFactory {

        @Override
        public CharSequence getCharacters() {
            return "$";
        }

        @Override
        public InlineParserExtension apply(LightInlineParser inlineParser) {
            return new MathParser();
        }

        @Override
        public Set<Class<?>> getAfterDependents() {
            return null;
        }

        @Override
        public Set<Class<?>> getBeforeDependents() {
            return null;
        }

        @Override
        public boolean affectsGlobalScope() {
            return false;
        }
    }
}
