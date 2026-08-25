package com.pdftools.operations.redact;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record RedactWorkerRequest(
    Path source,
    Path output,
    String sourceSha256,
    List<RedactPlanFactory.RedactArea> areas,
    int maxAreas,
    int maxAreasPerPage,
    int maxDocumentPages,
    int renderDpi,
    int jpegQuality,
    long maxPixelsPerPage,
    int maxImageDimension,
    long maxImageBytes,
    long maxTotalImageBytes,
    long maxOutputBytes,
    int maxPageTreeNodes,
    int maxPageTreeDepth,
    int maxContentStreamsPerPage
) implements PdfPageTreeLimits {

    private static final int VERSION = 1;

    RedactWorkerRequest {
        areas = List.copyOf(areas);
    }

    static void write(
            Path path,
            Path source,
            Path output,
            String sourceSha256,
            RedactPlanFactory.RedactPlan plan,
            RedactProperties properties) {
        try (DataOutputStream stream = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            stream.writeInt(VERSION);
            stream.writeUTF(source.toAbsolutePath().toString());
            stream.writeUTF(output.toAbsolutePath().toString());
            stream.writeUTF(sourceSha256);
            stream.writeInt(plan.areas().size());
            for (RedactPlanFactory.RedactArea area : plan.areas()) {
                stream.writeInt(area.page());
                stream.writeFloat(area.x());
                stream.writeFloat(area.y());
                stream.writeFloat(area.width());
                stream.writeFloat(area.height());
            }
            stream.writeInt(properties.getMaxAreas());
            stream.writeInt(properties.getMaxAreasPerPage());
            stream.writeInt(properties.getMaxDocumentPages());
            stream.writeInt(properties.getRenderDpi());
            stream.writeInt(properties.getJpegQuality());
            stream.writeLong(properties.getMaxPixelsPerPage());
            stream.writeInt(properties.getMaxImageDimension());
            stream.writeLong(properties.getMaxImageBytes());
            stream.writeLong(properties.getMaxTotalImageBytes());
            stream.writeLong(properties.getMaxOutputBytes());
            stream.writeInt(properties.maxPageTreeNodes());
            stream.writeInt(properties.maxPageTreeDepth());
            stream.writeInt(properties.maxContentStreamsPerPage());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static RedactWorkerRequest read(Path path) {
        try (DataInputStream stream = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (stream.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            Path source = Path.of(stream.readUTF());
            Path output = Path.of(stream.readUTF());
            String sourceSha256 = stream.readUTF();
            int areaCount = stream.readInt();
            if (areaCount < 1 || areaCount > 10_000) {
                throw protocolFailure(null);
            }
            List<RedactPlanFactory.RedactArea> areas =
                new ArrayList<>(areaCount);
            for (int index = 0; index < areaCount; index++) {
                areas.add(new RedactPlanFactory.RedactArea(
                    stream.readInt(),
                    stream.readFloat(),
                    stream.readFloat(),
                    stream.readFloat(),
                    stream.readFloat()
                ));
            }
            RedactWorkerRequest request = new RedactWorkerRequest(
                source,
                output,
                sourceSha256,
                areas,
                stream.readInt(),
                stream.readInt(),
                stream.readInt(),
                stream.readInt(),
                stream.readInt(),
                stream.readLong(),
                stream.readInt(),
                stream.readLong(),
                stream.readLong(),
                stream.readLong(),
                stream.readInt(),
                stream.readInt(),
                stream.readInt()
            );
            if (stream.read() != -1) {
                throw protocolFailure(null);
            }
            return request;
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    @Override
    public int maxPages() {
        return maxDocumentPages;
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "REDACT_WORKER_PROTOCOL_ERROR",
            "The secure redaction worker received an invalid request",
            cause
        );
    }
}
