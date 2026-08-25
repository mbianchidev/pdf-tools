package com.pdftools.operations.pdfword;

import com.pdftools.operations.OperationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record PdfToWordRequest(
    Path source,
    Path output,
    Path workspace,
    PdfToWordPlanFactory.PdfToWordPlan plan,
    PdfToWordProperties properties
) {

    private static final int VERSION = 1;

    static void write(
            Path requestPath,
            Path source,
            Path output,
            Path workspace,
            PdfToWordPlanFactory.PdfToWordPlan plan,
            PdfToWordProperties properties) {
        try (DataOutputStream data = new DataOutputStream(
                new BufferedOutputStream(
                    Files.newOutputStream(requestPath)))) {
            data.writeInt(VERSION);
            data.writeUTF(source.toAbsolutePath().toString());
            data.writeUTF(output.toAbsolutePath().toString());
            data.writeUTF(workspace.toAbsolutePath().toString());
            data.writeUTF(plan.mode());
            data.writeBoolean(plan.includeImages());
            data.writeBoolean(plan.detectTables());
            data.writeBoolean(plan.preservePageBreaks());
            data.writeInt(properties.getMaxPages());
            data.writeInt(properties.getMaxTextCharacters());
            data.writeInt(properties.getMaxImages());
            data.writeLong(properties.getMaxPixelsPerImage());
            data.writeLong(properties.getMaxTotalImagePixels());
            data.writeLong(properties.getMaxImageBytes());
            data.writeLong(properties.getMaxTotalImageBytes());
            data.writeLong(properties.getMaxOutputBytes());
            data.writeInt(properties.getRenderDpi());
            data.writeLong(properties.getMaxRenderPixelsPerPage());
            data.writeInt(properties.getMaxImageDimension());
            data.writeInt(properties.getMaxTableColumns());
            data.writeInt(properties.getMaxPageTreeNodes());
            data.writeInt(properties.getMaxPageTreeDepth());
            data.writeInt(properties.getMaxContentStreamsPerPage());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static PdfToWordRequest read(Path requestPath) {
        try (DataInputStream data = new DataInputStream(
                new BufferedInputStream(
                    Files.newInputStream(requestPath)))) {
            if (data.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            Path source = Path.of(data.readUTF());
            Path output = Path.of(data.readUTF());
            Path workspace = Path.of(data.readUTF());
            var plan = new PdfToWordPlanFactory.PdfToWordPlan(
                data.readUTF(),
                data.readBoolean(),
                data.readBoolean(),
                data.readBoolean()
            );
            PdfToWordProperties properties = new PdfToWordProperties();
            properties.setMaxPages(data.readInt());
            properties.setMaxTextCharacters(data.readInt());
            properties.setMaxImages(data.readInt());
            properties.setMaxPixelsPerImage(data.readLong());
            properties.setMaxTotalImagePixels(data.readLong());
            properties.setMaxImageBytes(data.readLong());
            properties.setMaxTotalImageBytes(data.readLong());
            properties.setMaxOutputBytes(data.readLong());
            properties.setRenderDpi(data.readInt());
            properties.setMaxRenderPixelsPerPage(data.readLong());
            properties.setMaxImageDimension(data.readInt());
            properties.setMaxTableColumns(data.readInt());
            properties.setMaxPageTreeNodes(data.readInt());
            properties.setMaxPageTreeDepth(data.readInt());
            properties.setMaxContentStreamsPerPage(data.readInt());
            if (data.read() != -1
                    || (!plan.mode().equals("editable")
                        && !plan.mode().equals("visual"))
                    || !source.isAbsolute()
                    || !output.isAbsolute()
                    || !workspace.isAbsolute()
                    || source.equals(output)
                    || !output.normalize().startsWith(workspace.normalize())
                    || !valid(properties)) {
                throw protocolFailure(null);
            }
            return new PdfToWordRequest(
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

    private static boolean valid(PdfToWordProperties properties) {
        return properties.getMaxPages() > 0
            && properties.getMaxTextCharacters() > 0
            && properties.getMaxImages() > 0
            && properties.getMaxPixelsPerImage() > 0
            && properties.getMaxTotalImagePixels() > 0
            && properties.getMaxImageBytes() > 0
            && properties.getMaxTotalImageBytes() > 0
            && properties.getMaxOutputBytes() > 0
            && properties.getRenderDpi() >= 72
            && properties.getRenderDpi() <= 300
            && properties.getMaxRenderPixelsPerPage() > 0
            && properties.getMaxImageDimension() > 0
            && properties.getMaxTableColumns() >= 2
            && properties.getMaxPageTreeNodes() > 0
            && properties.getMaxPageTreeDepth() > 0
            && properties.getMaxContentStreamsPerPage() > 0;
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "PDF_WORD_WORKER_PROTOCOL_ERROR",
            "The PDF-to-Word worker received invalid state",
            cause
        );
    }
}
