package com.pdftools.operations.pdfexcel;

import com.pdftools.operations.OperationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record PdfToExcelRequest(
    Path source,
    Path output,
    Path workspace,
    PdfToExcelPlanFactory.PdfToExcelPlan plan,
    PdfToExcelProperties properties
) {

    private static final int VERSION = 1;

    static void write(
            Path path,
            Path source,
            Path output,
            Path workspace,
            PdfToExcelPlanFactory.PdfToExcelPlan plan,
            PdfToExcelProperties properties) {
        try (DataOutputStream data = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            data.writeInt(VERSION);
            data.writeUTF(source.toAbsolutePath().toString());
            data.writeUTF(output.toAbsolutePath().toString());
            data.writeUTF(workspace.toAbsolutePath().toString());
            data.writeUTF(plan.sheetMode());
            data.writeBoolean(plan.includeNonTableText());
            data.writeInt(properties.getMaxPages());
            data.writeInt(properties.getMaxTextCharacters());
            data.writeInt(properties.getMaxTables());
            data.writeInt(properties.getMaxSheets());
            data.writeInt(properties.getMaxRowsPerSheet());
            data.writeInt(properties.getMaxColumns());
            data.writeLong(properties.getMaxCells());
            data.writeLong(properties.getMaxOutputBytes());
            data.writeInt(properties.getMaxPageTreeNodes());
            data.writeInt(properties.getMaxPageTreeDepth());
            data.writeInt(properties.getMaxContentStreamsPerPage());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static PdfToExcelRequest read(Path path) {
        try (DataInputStream data = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (data.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            Path source = Path.of(data.readUTF());
            Path output = Path.of(data.readUTF());
            Path workspace = Path.of(data.readUTF());
            var plan = new PdfToExcelPlanFactory.PdfToExcelPlan(
                data.readUTF(),
                data.readBoolean()
            );
            PdfToExcelProperties properties = new PdfToExcelProperties();
            properties.setMaxPages(data.readInt());
            properties.setMaxTextCharacters(data.readInt());
            properties.setMaxTables(data.readInt());
            properties.setMaxSheets(data.readInt());
            properties.setMaxRowsPerSheet(data.readInt());
            properties.setMaxColumns(data.readInt());
            properties.setMaxCells(data.readLong());
            properties.setMaxOutputBytes(data.readLong());
            properties.setMaxPageTreeNodes(data.readInt());
            properties.setMaxPageTreeDepth(data.readInt());
            properties.setMaxContentStreamsPerPage(data.readInt());
            if (data.read() != -1
                    || (!plan.sheetMode().equals("pages")
                        && !plan.sheetMode().equals("tables"))
                    || !source.isAbsolute()
                    || !output.isAbsolute()
                    || !workspace.isAbsolute()
                    || source.equals(output)
                    || !output.normalize().startsWith(workspace.normalize())
                    || !valid(properties)) {
                throw protocolFailure(null);
            }
            return new PdfToExcelRequest(
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

    private static boolean valid(PdfToExcelProperties properties) {
        return properties.getMaxPages() > 0
            && properties.getMaxTextCharacters() > 0
            && properties.getMaxTables() > 0
            && properties.getMaxSheets() > 0
            && properties.getMaxRowsPerSheet() > 0
            && properties.getMaxColumns() > 1
            && properties.getMaxCells() > 0
            && properties.getMaxOutputBytes() > 0
            && properties.getMaxPageTreeNodes() > 0
            && properties.getMaxPageTreeDepth() > 0
            && properties.getMaxContentStreamsPerPage() > 0;
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "PDF_EXCEL_WORKER_PROTOCOL_ERROR",
            "The PDF-to-Excel worker received invalid state",
            cause
        );
    }
}
