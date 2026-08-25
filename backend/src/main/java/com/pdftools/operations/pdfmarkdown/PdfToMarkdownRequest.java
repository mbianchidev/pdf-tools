package com.pdftools.operations.pdfmarkdown;

import com.pdftools.operations.OperationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record PdfToMarkdownRequest(
    Path source,
    Path output,
    Path workspace,
    PdfToMarkdownPlanFactory.PdfToMarkdownPlan plan,
    PdfToMarkdownProperties properties
) {

    private static final int VERSION = 1;

    static void write(
            Path path,
            Path source,
            Path output,
            Path workspace,
            PdfToMarkdownPlanFactory.PdfToMarkdownPlan plan,
            PdfToMarkdownProperties properties) {
        try (DataOutputStream data = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            data.writeInt(VERSION);
            data.writeUTF(source.toAbsolutePath().toString());
            data.writeUTF(output.toAbsolutePath().toString());
            data.writeUTF(workspace.toAbsolutePath().toString());
            data.writeBoolean(plan.detectHeadings());
            data.writeBoolean(plan.detectLists());
            data.writeBoolean(plan.detectTables());
            data.writeBoolean(plan.includeImages());
            data.writeBoolean(plan.preservePageBreaks());
            data.writeInt(properties.getMaxPages());
            data.writeInt(properties.getMaxTextCharacters());
            data.writeInt(properties.getMaxMarkdownCharacters());
            data.writeInt(properties.getMaxTables());
            data.writeInt(properties.getMaxTableColumns());
            data.writeInt(properties.getMaxImages());
            data.writeLong(properties.getMaxPixelsPerImage());
            data.writeLong(properties.getMaxTotalImagePixels());
            data.writeLong(properties.getMaxImageBytes());
            data.writeLong(properties.getMaxTotalImageBytes());
            data.writeInt(properties.getMaxImageDimension());
            data.writeLong(properties.getMaxOutputBytes());
            data.writeInt(properties.getMaxPageTreeNodes());
            data.writeInt(properties.getMaxPageTreeDepth());
            data.writeInt(properties.getMaxContentStreamsPerPage());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static PdfToMarkdownRequest read(Path path) {
        try (DataInputStream data = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (data.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            Path source = Path.of(data.readUTF());
            Path output = Path.of(data.readUTF());
            Path workspace = Path.of(data.readUTF());
            var plan = new PdfToMarkdownPlanFactory.PdfToMarkdownPlan(
                data.readBoolean(),
                data.readBoolean(),
                data.readBoolean(),
                data.readBoolean(),
                data.readBoolean()
            );
            PdfToMarkdownProperties properties =
                new PdfToMarkdownProperties();
            properties.setMaxPages(data.readInt());
            properties.setMaxTextCharacters(data.readInt());
            properties.setMaxMarkdownCharacters(data.readInt());
            properties.setMaxTables(data.readInt());
            properties.setMaxTableColumns(data.readInt());
            properties.setMaxImages(data.readInt());
            properties.setMaxPixelsPerImage(data.readLong());
            properties.setMaxTotalImagePixels(data.readLong());
            properties.setMaxImageBytes(data.readLong());
            properties.setMaxTotalImageBytes(data.readLong());
            properties.setMaxImageDimension(data.readInt());
            properties.setMaxOutputBytes(data.readLong());
            properties.setMaxPageTreeNodes(data.readInt());
            properties.setMaxPageTreeDepth(data.readInt());
            properties.setMaxContentStreamsPerPage(data.readInt());
            if (data.read() != -1
                    || !source.isAbsolute()
                    || !output.isAbsolute()
                    || !workspace.isAbsolute()
                    || source.equals(output)
                    || !output.normalize().startsWith(workspace.normalize())
                    || !valid(properties)) {
                throw protocolFailure(null);
            }
            return new PdfToMarkdownRequest(
                source,
                output,
                workspace,
                plan,
                properties
            );
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    private static boolean valid(PdfToMarkdownProperties properties) {
        return properties.getMaxPages() > 0
            && properties.getMaxTextCharacters() > 0
            && properties.getMaxMarkdownCharacters() > 0
            && properties.getMaxTables() > 0
            && properties.getMaxTableColumns() > 1
            && properties.getMaxImages() > 0
            && properties.getMaxPixelsPerImage() > 0
            && properties.getMaxTotalImagePixels() > 0
            && properties.getMaxImageBytes() > 0
            && properties.getMaxTotalImageBytes() > 0
            && properties.getMaxImageDimension() > 0
            && properties.getMaxOutputBytes() > 0
            && properties.getMaxPageTreeNodes() > 0
            && properties.getMaxPageTreeDepth() > 0
            && properties.getMaxContentStreamsPerPage() > 0;
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "PDF_MARKDOWN_WORKER_PROTOCOL_ERROR",
            "The PDF-to-Markdown worker received invalid state",
            cause
        );
    }
}
