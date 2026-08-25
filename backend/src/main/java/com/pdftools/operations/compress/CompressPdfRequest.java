package com.pdftools.operations.compress;

import com.pdftools.operations.OperationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record CompressPdfRequest(
    Path source,
    Path output,
    Path workspace,
    String sourceSha256,
    CompressPdfPlanFactory.CompressionMode mode,
    CompressPdfProperties properties
) {

    private static final int VERSION = 1;

    static void write(
            Path path,
            Path source,
            Path output,
            Path workspace,
            String sourceSha256,
            CompressPdfPlanFactory.CompressionMode mode,
            CompressPdfProperties properties) {
        try (DataOutputStream data = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            data.writeInt(VERSION);
            data.writeUTF(source.toAbsolutePath().toString());
            data.writeUTF(output.toAbsolutePath().toString());
            data.writeUTF(workspace.toAbsolutePath().toString());
            data.writeUTF(sourceSha256);
            data.writeUTF(mode.option());
            data.writeLong(properties.getMaxInputBytes());
            data.writeInt(properties.getMaxPages());
            data.writeInt(properties.getMaxImages());
            data.writeLong(properties.getMaxPixelsPerImage());
            data.writeLong(properties.getMaxTotalImagePixels());
            data.writeInt(properties.getMaxImageDimension());
            data.writeInt(properties.getRecommendedMaxImageDimension());
            data.writeInt(properties.getRecommendedJpegQuality());
            data.writeInt(properties.getExtremeMaxImageDimension());
            data.writeInt(properties.getExtremeJpegQuality());
            data.writeLong(properties.getMaxTemporaryImageBytes());
            data.writeLong(
                properties.getMaxTotalRecompressedImageBytes()
            );
            data.writeInt(properties.getMaxXObjects());
            data.writeInt(properties.getMaxResourceDepth());
            data.writeLong(properties.getMaxOutputBytes());
            data.writeInt(properties.getMaxPageTreeNodes());
            data.writeInt(properties.getMaxPageTreeDepth());
            data.writeInt(properties.getMaxContentStreamsPerPage());
        } catch (IOException exception) {
            throw protocolFailure(exception);
        }
    }

    static CompressPdfRequest read(Path path) {
        try (DataInputStream data = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            if (data.readInt() != VERSION) {
                throw protocolFailure(null);
            }
            Path source = Path.of(data.readUTF());
            Path output = Path.of(data.readUTF());
            Path workspace = Path.of(data.readUTF());
            String sourceSha256 = data.readUTF();
            CompressPdfPlanFactory.CompressionMode mode =
                CompressPdfPlanFactory.CompressionMode.fromOption(
                    data.readUTF()
                );
            CompressPdfProperties properties = new CompressPdfProperties();
            properties.setMaxInputBytes(data.readLong());
            properties.setMaxPages(data.readInt());
            properties.setMaxImages(data.readInt());
            properties.setMaxPixelsPerImage(data.readLong());
            properties.setMaxTotalImagePixels(data.readLong());
            properties.setMaxImageDimension(data.readInt());
            properties.setRecommendedMaxImageDimension(data.readInt());
            properties.setRecommendedJpegQuality(data.readInt());
            properties.setExtremeMaxImageDimension(data.readInt());
            properties.setExtremeJpegQuality(data.readInt());
            properties.setMaxTemporaryImageBytes(data.readLong());
            properties.setMaxTotalRecompressedImageBytes(data.readLong());
            properties.setMaxXObjects(data.readInt());
            properties.setMaxResourceDepth(data.readInt());
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
                    || !sourceSha256.matches("[0-9a-f]{64}")
                    || !valid(properties)) {
                throw protocolFailure(null);
            }
            return new CompressPdfRequest(
                source,
                output,
                workspace,
                sourceSha256,
                mode,
                properties
            );
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw protocolFailure(exception);
        }
    }

    private static boolean valid(CompressPdfProperties properties) {
        return properties.getMaxInputBytes() > 0
            && properties.getMaxPages() > 0
            && properties.getMaxImages() > 0
            && properties.getMaxPixelsPerImage() > 0
            && properties.getMaxTotalImagePixels() > 0
            && properties.getMaxImageDimension() > 0
            && properties.getRecommendedMaxImageDimension() > 0
            && validQuality(properties.getRecommendedJpegQuality())
            && properties.getExtremeMaxImageDimension() > 0
            && validQuality(properties.getExtremeJpegQuality())
            && properties.getExtremeMaxImageDimension()
                <= properties.getRecommendedMaxImageDimension()
            && properties.getRecommendedMaxImageDimension()
                <= properties.getMaxImageDimension()
            && properties.getMaxTemporaryImageBytes() > 0
            && properties.getMaxTotalRecompressedImageBytes() > 0
            && properties.getMaxXObjects() > 0
            && properties.getMaxResourceDepth() > 0
            && properties.getMaxOutputBytes()
                >= properties.getMaxInputBytes()
            && properties.getMaxPageTreeNodes() > 0
            && properties.getMaxPageTreeDepth() > 0
            && properties.getMaxContentStreamsPerPage() > 0;
    }

    private static boolean validQuality(int value) {
        return value >= 10 && value <= 100;
    }

    private static OperationException protocolFailure(Throwable cause) {
        return new OperationException(
            "COMPRESS_WORKER_PROTOCOL_ERROR",
            "The PDF compression worker received invalid state",
            cause
        );
    }
}
