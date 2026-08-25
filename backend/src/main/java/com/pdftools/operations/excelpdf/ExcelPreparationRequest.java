package com.pdftools.operations.excelpdf;

import com.pdftools.operations.OperationException;
import org.apache.poi.ss.SpreadsheetVersion;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

record ExcelPreparationRequest(
    Path source,
    Path destination,
    ExcelPlanFactory.ExcelPlan plan,
    SpreadsheetVersion spreadsheetVersion,
    ExcelProperties properties
) {

    private static final int VERSION = 1;

    static void write(
            Path path,
            Path source,
            Path destination,
            ExcelPlanFactory.ExcelPlan plan,
            SpreadsheetVersion spreadsheetVersion,
            ExcelProperties properties) {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            output.writeInt(VERSION);
            output.writeUTF(source.toAbsolutePath().toString());
            output.writeUTF(destination.toAbsolutePath().toString());
            output.writeUTF(plan.printAreaMode());
            output.writeUTF(plan.printArea());
            output.writeUTF(plan.orientation());
            output.writeUTF(spreadsheetVersion.name());
            output.writeInt(properties.getMaxSheets());
            output.writeLong(properties.getMaxUsedCells());
            output.writeLong(properties.getMaxPreparedBytes());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static ExcelPreparationRequest read(Path path) {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            Path source = Path.of(input.readUTF());
            Path destination = Path.of(input.readUTF());
            ExcelPlanFactory.ExcelPlan plan = new ExcelPlanFactory.ExcelPlan(
                input.readUTF(),
                input.readUTF(),
                input.readUTF()
            );
            SpreadsheetVersion spreadsheetVersion =
                SpreadsheetVersion.valueOf(input.readUTF());
            ExcelProperties properties = new ExcelProperties();
            properties.setMaxSheets(input.readInt());
            properties.setMaxUsedCells(input.readLong());
            properties.setMaxPreparedBytes(input.readLong());
            if (input.read() != -1
                    || properties.getMaxSheets() < 1
                    || properties.getMaxUsedCells() < 1
                    || properties.getMaxPreparedBytes() < 1
                    || source.equals(destination)) {
                throw protocolFailure(null);
            }
            return new ExcelPreparationRequest(
                source,
                destination,
                plan,
                spreadsheetVersion,
                properties
            );
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "EXCEL_PREPARATION_PROTOCOL_ERROR",
            "The Excel preparation worker received invalid state",
            cause
        );
    }
}
