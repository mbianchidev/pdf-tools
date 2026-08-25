package com.pdftools.operations.compare;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.pdf.PdfPageTreeLimits;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record CompareWorkerRequest(
    Path baseline,
    Path candidate,
    Path report,
    Path archive,
    Path workspace,
    ComparePlanFactory.ComparePlan plan,
    long maxInputBytes,
    int maxPages,
    int maxTextCharactersPerDocument,
    int maxTextLinesPerPage,
    int maxLineCharacters,
    long maxDiffMatrixCells,
    int maxTextChanges,
    long maxPixelsPerPage,
    long maxTotalRenderPixels,
    int maxImageDimension,
    long maxDiffImageBytes,
    long maxTotalDiffImageBytes,
    long maxReportBytes,
    long maxArchiveBytes,
    int maxPageTreeNodes,
    int maxPageTreeDepth,
    int maxContentStreamsPerPage
) implements PdfPageTreeLimits {

    private static final int VERSION = 1;

    static void write(
            Path path,
            Path baseline,
            Path candidate,
            Path report,
            Path archive,
            Path workspace,
            ComparePlanFactory.ComparePlan plan,
            CompareProperties properties) {
        try (DataOutputStream data = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            data.writeInt(VERSION);
            data.writeUTF(baseline.toAbsolutePath().toString());
            data.writeUTF(candidate.toAbsolutePath().toString());
            data.writeUTF(report.toAbsolutePath().toString());
            data.writeUTF(archive.toAbsolutePath().toString());
            data.writeUTF(workspace.toAbsolutePath().toString());
            data.writeInt(plan.renderDpi());
            data.writeInt(plan.pixelTolerance());
            data.writeDouble(plan.layoutTolerancePoints());
            data.writeLong(properties.getMaxInputBytes());
            data.writeInt(properties.getMaxPages());
            data.writeInt(
                properties.getMaxTextCharactersPerDocument()
            );
            data.writeInt(properties.getMaxTextLinesPerPage());
            data.writeInt(properties.getMaxLineCharacters());
            data.writeLong(properties.getMaxDiffMatrixCells());
            data.writeInt(properties.getMaxTextChanges());
            data.writeLong(properties.getMaxPixelsPerPage());
            data.writeLong(properties.getMaxTotalRenderPixels());
            data.writeInt(properties.getMaxImageDimension());
            data.writeLong(properties.getMaxDiffImageBytes());
            data.writeLong(properties.getMaxTotalDiffImageBytes());
            data.writeLong(properties.getMaxReportBytes());
            data.writeLong(properties.getMaxArchiveBytes());
            data.writeInt(properties.getMaxPageTreeNodes());
            data.writeInt(properties.getMaxPageTreeDepth());
            data.writeInt(properties.getMaxContentStreamsPerPage());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static CompareWorkerRequest read(Path path) {
        try (DataInputStream data = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (data.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            CompareWorkerRequest request = new CompareWorkerRequest(
                Path.of(data.readUTF()),
                Path.of(data.readUTF()),
                Path.of(data.readUTF()),
                Path.of(data.readUTF()),
                Path.of(data.readUTF()),
                new ComparePlanFactory.ComparePlan(
                    data.readInt(),
                    data.readInt(),
                    data.readDouble()
                ),
                data.readLong(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readLong(),
                data.readInt(),
                data.readLong(),
                data.readLong(),
                data.readInt(),
                data.readLong(),
                data.readLong(),
                data.readLong(),
                data.readLong(),
                data.readInt(),
                data.readInt(),
                data.readInt()
            );
            if (data.read() != -1 || !request.valid()) {
                throw protocolFailure(null);
            }
            return request;
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    private boolean valid() {
        Path root = workspace.normalize();
        return baseline.isAbsolute()
            && candidate.isAbsolute()
            && report.isAbsolute()
            && archive.isAbsolute()
            && workspace.isAbsolute()
            && !report.equals(archive)
            && report.normalize().startsWith(root)
            && archive.normalize().startsWith(root)
            && plan.renderDpi() >= 72
            && plan.renderDpi() <= 200
            && plan.pixelTolerance() >= 0
            && plan.pixelTolerance() <= 255
            && Double.isFinite(plan.layoutTolerancePoints())
            && plan.layoutTolerancePoints() >= 0.1
            && plan.layoutTolerancePoints() <= 20
            && maxInputBytes > 0
            && maxPages > 0
            && maxTextCharactersPerDocument > 0
            && maxTextLinesPerPage > 0
            && maxLineCharacters > 0
            && maxDiffMatrixCells > 0
            && maxTextChanges > 0
            && maxPixelsPerPage > 0
            && maxTotalRenderPixels > 0
            && maxImageDimension > 0
            && maxDiffImageBytes > 0
            && maxTotalDiffImageBytes > 0
            && maxReportBytes > 0
            && maxArchiveBytes >= maxReportBytes
            && maxPageTreeNodes > 0
            && maxPageTreeDepth > 0
            && maxContentStreamsPerPage > 0;
    }

    @Override
    public int maxPages() {
        return maxPages;
    }

    @Override
    public int maxPageTreeNodes() {
        return maxPageTreeNodes;
    }

    @Override
    public int maxPageTreeDepth() {
        return maxPageTreeDepth;
    }

    @Override
    public int maxContentStreamsPerPage() {
        return maxContentStreamsPerPage;
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "COMPARE_WORKER_PROTOCOL_ERROR",
            "The PDF comparison worker received invalid state",
            cause
        );
    }
}
