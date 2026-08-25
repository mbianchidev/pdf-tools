package com.pdftools.operations.split;

import com.pdftools.operations.OperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

final class PdfContentPreflight {

    private static final long CANCELLATION_INTERVAL_BYTES = 64L * 1024L;

    private final int maxTokens;
    private final int maxDepth;

    PdfContentPreflight(int maxTokens, int maxDepth) {
        this.maxTokens = maxTokens;
        this.maxDepth = maxDepth;
    }

    void validate(InputStream source, Runnable cancellationCheck)
            throws IOException {
        Counter counter = new Counter(cancellationCheck);
        try (PushbackInputStream input = new PushbackInputStream(source, 2)) {
            int depth = 0;
            boolean inlineImage = false;
            int current;
            while ((current = counter.read(input)) >= 0) {
                if (isWhitespace(current)) {
                    continue;
                }
                if (current == '%') {
                    skipComment(input, counter);
                    continue;
                }
                if (current == '(') {
                    countToken(counter);
                    skipLiteralString(input, counter);
                    continue;
                }
                if (current == '<') {
                    int next = counter.read(input);
                    countToken(counter);
                    if (next == '<') {
                        depth = openContainer(depth);
                    } else {
                        skipHexString(input, counter, next);
                    }
                    continue;
                }
                if (current == '>') {
                    if (counter.read(input) != '>') {
                        throw invalidContent("Invalid dictionary terminator");
                    }
                    depth = closeContainer(depth);
                    continue;
                }
                if (current == '[') {
                    countToken(counter);
                    depth = openContainer(depth);
                    continue;
                }
                if (current == ']') {
                    depth = closeContainer(depth);
                    continue;
                }

                countToken(counter);
                Token token = readToken(current, input, counter);
                if (inlineImage && token.matches("ID")) {
                    skipInlineImageData(input, counter);
                    countToken(counter);
                    inlineImage = false;
                } else if (token.matches("BI")) {
                    inlineImage = true;
                }
            }
            if (depth != 0 || inlineImage) {
                throw invalidContent("Unterminated PDF content object");
            }
            cancellationCheck.run();
        }
    }

    private int openContainer(int depth) {
        int next = depth + 1;
        if (next > maxDepth) {
            throw complexity("PDF content nesting exceeds the configured limit");
        }
        return next;
    }

    private int closeContainer(int depth) {
        if (depth < 1) {
            throw invalidContent("Unexpected PDF content terminator");
        }
        return depth - 1;
    }

    private void countToken(Counter counter) {
        if (++counter.tokens > maxTokens) {
            throw complexity("PDF content contains too many nested objects");
        }
    }

    private Token readToken(
            int first,
            PushbackInputStream input,
            Counter counter) throws IOException {
        int length = 1;
        int firstByte = first;
        int secondByte = -1;
        int current;
        while ((current = counter.read(input)) >= 0
                && !isWhitespace(current)
                && !isDelimiter(current)) {
            if (length == 1) {
                secondByte = current;
            }
            length++;
        }
        if (current >= 0 && !isWhitespace(current)) {
            input.unread(current);
        }
        return new Token(firstByte, secondByte, length);
    }

    private void skipComment(
            PushbackInputStream input,
            Counter counter) throws IOException {
        int current;
        while ((current = counter.read(input)) >= 0) {
            if (current == '\r' || current == '\n') {
                return;
            }
        }
    }

    private void skipLiteralString(
            PushbackInputStream input,
            Counter counter) throws IOException {
        int depth = 1;
        int current;
        while ((current = counter.read(input)) >= 0) {
            if (current == '\\') {
                int escaped = counter.read(input);
                if (escaped == '\r') {
                    int next = counter.read(input);
                    if (next >= 0 && next != '\n') {
                        input.unread(next);
                    }
                }
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return;
            }
        }
        throw invalidContent("Unterminated PDF literal string");
    }

    private void skipHexString(
            PushbackInputStream input,
            Counter counter,
            int first) throws IOException {
        int current = first;
        while (current >= 0) {
            if (current == '>') {
                return;
            }
            current = counter.read(input);
        }
        throw invalidContent("Unterminated PDF hexadecimal string");
    }

    private void skipInlineImageData(
            PushbackInputStream input,
            Counter counter) throws IOException {
        int separator = counter.read(input);
        if (!isWhitespace(separator)) {
            throw invalidContent("Inline image data is missing its separator");
        }
        if (separator == '\r') {
            int next = counter.read(input);
            if (next >= 0 && next != '\n') {
                input.unread(next);
            }
        }

        int previous = separator;
        int current;
        while ((current = counter.read(input)) >= 0) {
            if (isWhitespace(previous) && current == 'E') {
                int i = counter.read(input);
                if (i == 'I') {
                    int after = counter.read(input);
                    if (after < 0 || isWhitespace(after) || isDelimiter(after)) {
                        if (after >= 0 && !isWhitespace(after)) {
                            input.unread(after);
                        }
                        return;
                    }
                    previous = after;
                    continue;
                }
                previous = i;
                continue;
            }
            previous = current;
        }
        throw invalidContent("Unterminated inline image data");
    }

    private boolean isDelimiter(int value) {
        return switch (value) {
            case '(', ')', '<', '>', '[', ']', '{', '}', '/', '%' -> true;
            default -> false;
        };
    }

    private boolean isWhitespace(int value) {
        return value == 0
            || value == '\t'
            || value == '\n'
            || value == '\f'
            || value == '\r'
            || value == ' ';
    }

    private OperationException complexity(String message) {
        return new OperationException(
            "PDF_CONTENT_COMPLEXITY_LIMIT_EXCEEDED",
            message
        );
    }

    private OperationException invalidContent(String message) {
        return new OperationException("INVALID_PDF_CONTENT", message);
    }

    private static final class Counter {
        private final Runnable cancellationCheck;
        private long bytes;
        private long nextCancellation = CANCELLATION_INTERVAL_BYTES;
        private int tokens;

        private Counter(Runnable cancellationCheck) {
            this.cancellationCheck = cancellationCheck;
        }

        private int read(InputStream input) throws IOException {
            int value = input.read();
            if (value >= 0 && ++bytes >= nextCancellation) {
                cancellationCheck.run();
                nextCancellation = bytes + CANCELLATION_INTERVAL_BYTES;
            }
            return value;
        }
    }

    private record Token(int first, int second, int length) {
        private boolean matches(String value) {
            return length == value.length()
                && first == value.charAt(0)
                && second == value.charAt(1);
        }
    }
}
