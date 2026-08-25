package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record PdfAValidationRequest(
    Path source,
    Path report,
    PdfAPlanFactory.PdfAProfile profile,
    long maxSourceBytes,
    int maxRuleFailures,
    int maxFailureCharacters,
    long maxReportBytes
) {

    private static final int VERSION = 1;

    static void write(
            Path path,
            Path source,
            Path report,
            PdfAPlanFactory.PdfAProfile profile,
            PdfAProperties properties) {
        try (DataOutputStream data = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            data.writeInt(VERSION);
            data.writeUTF(source.toAbsolutePath().toString());
            data.writeUTF(report.toAbsolutePath().toString());
            data.writeUTF(profile.option());
            data.writeLong(properties.getMaxOutputBytes());
            data.writeInt(properties.getMaxRuleFailures());
            data.writeInt(properties.getMaxFailureCharacters());
            data.writeLong(properties.getMaxReportBytes());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static PdfAValidationRequest read(Path path) {
        try (DataInputStream data = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (data.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            Path source = Path.of(data.readUTF());
            Path report = Path.of(data.readUTF());
            PdfAPlanFactory.PdfAProfile profile =
                PdfAPlanFactory.PdfAProfile.fromOption(data.readUTF());
            long maxSourceBytes = data.readLong();
            int maxRuleFailures = data.readInt();
            int maxFailureCharacters = data.readInt();
            long maxReportBytes = data.readLong();
            if (data.read() != -1
                    || !source.isAbsolute()
                    || !report.isAbsolute()
                    || source.equals(report)
                    || !report.normalize().startsWith(
                        path.toAbsolutePath().getParent().normalize())
                    || maxSourceBytes < 1
                    || maxRuleFailures < 1
                    || maxFailureCharacters < 8
                    || maxReportBytes < 1) {
                throw protocolFailure(null);
            }
            return new PdfAValidationRequest(
                source,
                report,
                profile,
                maxSourceBytes,
                maxRuleFailures,
                maxFailureCharacters,
                maxReportBytes
            );
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "PDFA_VALIDATOR_PROTOCOL_ERROR",
            "The PDF/A validator received invalid state",
            cause
        );
    }
}
