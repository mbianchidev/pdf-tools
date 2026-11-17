package com.pdftools.operations.markdownpdf;

import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ext.tables.internal.TableParagraphPreProcessor;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.block.ParagraphPreProcessor;
import com.vladsch.flexmark.parser.block.ParagraphPreProcessorFactory;
import com.vladsch.flexmark.parser.block.ParserState;
import com.vladsch.flexmark.parser.core.ReferencePreProcessorFactory;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * GitHub lets a table start directly under paragraph text, while flexmark
 * only recognizes a table at the start of a paragraph. This pre-processor
 * runs before flexmark's table pre-processor and moves the text above the
 * first header and delimiter row pair into its own paragraph.
 */
final class TableParagraphSplitter implements ParagraphPreProcessor {

    static final Parser.ParserExtension EXTENSION =
        new Parser.ParserExtension() {
            @Override
            public void parserOptions(MutableDataHolder options) {
            }

            @Override
            public void extend(Parser.Builder builder) {
                builder.paragraphPreProcessorFactory(new Factory());
            }
        };

    private static final Pattern DELIMITER_CELL =
        Pattern.compile(":?-+:?");

    @Override
    public int preProcessBlock(Paragraph block, ParserState state) {
        List<BasedSequence> lines = block.getContentLines();
        for (int index = 1; index < lines.size(); index++) {
            if (isTableStart(
                    lines.get(index - 1).toString(),
                    lines.get(index).toString())) {
                return index == 1 ? 0 : split(block, index - 1, state);
            }
        }
        return 0;
    }

    static boolean isTableStart(String header, String delimiter) {
        if (header.indexOf('|') < 0 || delimiter.indexOf('|') < 0) {
            return false;
        }
        List<String> delimiterCells = cells(delimiter);
        if (delimiterCells.isEmpty()
                || delimiterCells.stream().anyMatch(cell ->
                    !DELIMITER_CELL.matcher(cell).matches())) {
            return false;
        }
        return cells(header).size() == delimiterCells.size();
    }

    private static List<String> cells(String row) {
        String trimmed = row.strip();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (character == '\\' && index + 1 < trimmed.length()) {
                cell.append(character).append(trimmed.charAt(++index));
            } else if (character == '|') {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }

    private int split(Paragraph block, int headerLine, ParserState state) {
        List<BasedSequence> lines = block.getContentLines();
        BasedSequence chars = block.getChars();
        int headerStart = lines.get(headerLine).getStartOffset();
        Paragraph intro = new Paragraph(
            chars.baseSubSequence(chars.getStartOffset(), headerStart),
            lines.subList(0, headerLine),
            Arrays.copyOf(block.getLineIndents(), headerLine)
        );
        block.insertBefore(intro);
        state.getInlineParser().parse(intro.getContentChars(), intro);
        return headerStart - chars.getStartOffset();
    }

    private static final class Factory implements ParagraphPreProcessorFactory {

        @Override
        public boolean affectsGlobalScope() {
            return false;
        }

        @Override
        public Set<Class<?>> getAfterDependents() {
            return Set.of(ReferencePreProcessorFactory.class);
        }

        @Override
        public Set<Class<?>> getBeforeDependents() {
            return Set.of(TableParagraphPreProcessor.Factory().getClass());
        }

        @Override
        public ParagraphPreProcessor apply(ParserState state) {
            return new TableParagraphSplitter();
        }
    }
}
