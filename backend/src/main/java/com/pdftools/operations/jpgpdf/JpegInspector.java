package com.pdftools.operations.jpgpdf;

import com.pdftools.operations.CheckpointInputStream;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.awt.image.Raster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

final class JpegInspector {

    private static final int MAX_MARKERS = 100_000;

    JpegInfo inspect(Path source, Runnable cancellationCheck) {
        try (InputStream file = Files.newInputStream(source);
             InputStream checked = new CheckpointInputStream(
                 file,
                 cancellationCheck
             );
             InputStream buffered = new BufferedInputStream(checked);
             DataInputStream input = new DataInputStream(buffered)) {
            if (input.readUnsignedShort() != 0xFFD8) {
                throw invalidJpeg();
            }
            int orientation = 1;
            int width = 0;
            int height = 0;
            int components = 0;
            int adobeTransform = -1;
            boolean sawScan = false;
            int frameMarker = 0;
            Set<Integer> frameComponents = new HashSet<>();
            int pendingMarker = -1;
            int markers = 0;
            while (true) {
                if (++markers > MAX_MARKERS) {
                    throw invalidJpeg();
                }
                int marker = pendingMarker >= 0
                    ? pendingMarker
                    : nextMarker(input);
                pendingMarker = -1;
                if (marker == 0xD9) {
                    if (!sawScan) {
                        throw invalidJpeg();
                    }
                    return markerMetadata(
                        orientation,
                        width,
                        height,
                        components,
                        adobeTransform,
                        frameMarker == 0xC2
                    );
                }
                if (marker == 0xD8
                        || marker == 0x00
                        || marker >= 0xD0 && marker <= 0xD7) {
                    throw invalidJpeg();
                }
                if (marker == 0x01) {
                    continue;
                }
                int segmentLength = input.readUnsignedShort();
                if (segmentLength < 2) {
                    throw invalidJpeg();
                }
                int payloadLength = segmentLength - 2;
                if (marker == 0xE1
                        || marker == 0xEE
                        || marker == 0xDA
                        || isStartOfFrame(marker)) {
                    byte[] payload = input.readNBytes(payloadLength);
                    if (payload.length != payloadLength) {
                        throw invalidJpeg();
                    }
                    if (marker == 0xE1) {
                        int parsedOrientation = parseExif(payload);
                        if (parsedOrientation != 0) {
                            orientation = parsedOrientation;
                        }
                    } else if (marker == 0xEE) {
                        int parsedTransform = adobeTransform(payload);
                        if (parsedTransform >= 0) {
                            if (adobeTransform >= 0
                                    && adobeTransform != parsedTransform) {
                                throw invalidJpeg();
                            }
                            adobeTransform = parsedTransform;
                        }
                    } else if (marker == 0xDA) {
                        validateScan(
                            payload,
                            frameMarker,
                            frameComponents
                        );
                    } else {
                        if (payload.length < 6
                                || Byte.toUnsignedInt(payload[0]) != 8
                                || marker != 0xC0
                                    && marker != 0xC1
                                    && marker != 0xC2) {
                            throw invalidJpeg();
                        }
                        if (frameMarker != 0) {
                            throw invalidJpeg();
                        }
                        height = unsignedShort(payload, 1);
                        width = unsignedShort(payload, 3);
                        components = Byte.toUnsignedInt(payload[5]);
                        validateFrame(payload, components);
                        frameMarker = marker;
                        for (int index = 0; index < components; index++) {
                            frameComponents.add(Byte.toUnsignedInt(
                                payload[6 + index * 3]
                            ));
                        }
                    }
                } else {
                    input.skipNBytes(payloadLength);
                }
                if (marker == 0xDA) {
                    sawScan = true;
                    pendingMarker = nextEntropyMarker(input);
                }
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (OperationCancelledException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw invalidJpeg();
        } catch (IOException | RuntimeException exception) {
            throw new OperationException(
                "INVALID_JPEG",
                "An input is not a readable JPEG image",
                exception
            );
        }
    }

    void writeValidationCopy(
            Path source,
            Path validationCopy,
            JpegInfo expected,
            Runnable cancellationCheck) {
        try {
            writeMetadataStrippedCopy(
                source,
                validationCopy,
                expected,
                cancellationCheck
            );
            cancellationCheck.run();
        } catch (OperationException | OperationCancelledException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new OperationException(
                "INVALID_JPEG",
                "An input is not a decodable JPEG image",
                exception
            );
        }
    }

    void validateDecodableCopy(
            Path validationCopy,
            JpegInfo expected) {
        try (FileImageInputStream input =
                new FileImageInputStream(validationCopy.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidJpeg();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) != expected.width()
                        || reader.getHeight(0) != expected.height()) {
                    throw invalidJpeg();
                }
                int largestSide = Math.max(
                    expected.width(),
                    expected.height()
                );
                int subsampling = Math.max(
                    1,
                    (largestSide + 511) / 512
                );
                ImageReadParam parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(
                    subsampling,
                    subsampling,
                    0,
                    0
                );
                Raster raster = reader.readRaster(0, parameters);
                if (raster.getNumBands() != expected.components()) {
                    throw invalidJpeg();
                }
            } finally {
                reader.dispose();
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new OperationException(
                "INVALID_JPEG",
                "An input is not a decodable JPEG image",
                exception
            );
        }
    }

    private void validateFrame(byte[] payload, int components) {
        if (components < 1
                || components > 4
                || payload.length != 6 + components * 3) {
            throw invalidJpeg();
        }
        Set<Integer> identifiers = new HashSet<>();
        for (int index = 0; index < components; index++) {
            int offset = 6 + index * 3;
            int identifier = Byte.toUnsignedInt(payload[offset]);
            int sampling = Byte.toUnsignedInt(payload[offset + 1]);
            int horizontal = sampling >>> 4;
            int vertical = sampling & 0x0F;
            int table = Byte.toUnsignedInt(payload[offset + 2]);
            if (!identifiers.add(identifier)
                    || horizontal < 1
                    || horizontal > 4
                    || vertical < 1
                    || vertical > 4
                    || table > 3) {
                throw invalidJpeg();
            }
        }
    }

    private void validateScan(
            byte[] payload,
            int frameMarker,
            Set<Integer> frameComponents) {
        if (frameMarker == 0 || payload.length < 6) {
            throw invalidJpeg();
        }
        int components = Byte.toUnsignedInt(payload[0]);
        if (components < 1
                || components > frameComponents.size()
                || payload.length != 4 + components * 2) {
            throw invalidJpeg();
        }
        Set<Integer> scanComponents = new HashSet<>();
        for (int index = 0; index < components; index++) {
            int offset = 1 + index * 2;
            int identifier = Byte.toUnsignedInt(payload[offset]);
            int tables = Byte.toUnsignedInt(payload[offset + 1]);
            if (!frameComponents.contains(identifier)
                    || !scanComponents.add(identifier)
                    || (tables >>> 4) > 3
                    || (tables & 0x0F) > 3) {
                throw invalidJpeg();
            }
        }
        int spectralStart = Byte.toUnsignedInt(
            payload[payload.length - 3]
        );
        int spectralEnd = Byte.toUnsignedInt(
            payload[payload.length - 2]
        );
        int approximation = Byte.toUnsignedInt(
            payload[payload.length - 1]
        );
        if (spectralStart > spectralEnd
                || spectralEnd > 63
                || (approximation >>> 4) > 13
                || (approximation & 0x0F) > 13
                || frameMarker != 0xC2
                    && (spectralStart != 0
                        || spectralEnd != 63
                        || approximation != 0)) {
            throw invalidJpeg();
        }
    }

    private int nextMarker(DataInputStream input) throws IOException {
        if (input.readUnsignedByte() != 0xFF) {
            throw invalidJpeg();
        }
        int marker;
        do {
            marker = input.readUnsignedByte();
        } while (marker == 0xFF);
        if (marker == 0x00) {
            throw invalidJpeg();
        }
        return marker;
    }

    private int nextEntropyMarker(DataInputStream input)
            throws IOException {
        while (true) {
            if (input.readUnsignedByte() != 0xFF) {
                continue;
            }
            int marker;
            do {
                marker = input.readUnsignedByte();
            } while (marker == 0xFF);
            if (marker == 0x00
                    || marker >= 0xD0 && marker <= 0xD7) {
                continue;
            }
            return marker;
        }
    }

    private void writeMetadataStrippedCopy(
            Path source,
            Path destination,
            JpegInfo expected,
            Runnable cancellationCheck) throws IOException {
        try (InputStream file = Files.newInputStream(source);
             InputStream checked = new CheckpointInputStream(
                 file,
                 cancellationCheck
             );
             DataInputStream input = new DataInputStream(
                 new BufferedInputStream(checked)
             );
             OutputStream fileOutput = Files.newOutputStream(destination);
             DataOutputStream output = new DataOutputStream(
                 new BufferedOutputStream(fileOutput)
             )) {
            if (input.readUnsignedShort() != 0xFFD8) {
                throw invalidJpeg();
            }
            output.writeShort(0xFFD8);
            if (expected.adobe()) {
                writeAdobeMarker(output, expected.adobeTransform());
            }
            boolean sawScan = false;
            int pendingMarker = -1;
            int markers = 0;
            while (true) {
                if (++markers > MAX_MARKERS) {
                    throw invalidJpeg();
                }

                int marker = pendingMarker >= 0
                    ? pendingMarker
                    : nextMarker(input);
                pendingMarker = -1;
                if (marker == 0xD9) {
                    if (!sawScan) {
                        throw invalidJpeg();
                    }
                    output.writeShort(0xFFD9);
                    return;
                }
                if (marker == 0xD8
                        || marker >= 0xD0 && marker <= 0xD7) {
                    throw invalidJpeg();
                }
                if (marker == 0x01) {
                    output.writeShort(0xFF01);
                    continue;
                }
                int segmentLength = input.readUnsignedShort();
                if (segmentLength < 2) {
                    throw invalidJpeg();
                }
                int payloadLength = segmentLength - 2;
                boolean metadata = marker >= 0xE0 && marker <= 0xEF
                    || marker == 0xFE;
                if (metadata) {
                    input.skipNBytes(payloadLength);
                } else {
                    output.writeByte(0xFF);
                    output.writeByte(marker);
                    output.writeShort(segmentLength);
                    copyExactly(input, output, payloadLength);
                }
                if (marker == 0xDA) {
                    sawScan = true;
                    pendingMarker = copyEntropy(input, output);
                }
            }

        } catch (EOFException exception) {
            throw invalidJpeg();
        }
    }

    private void writeAdobeMarker(
            DataOutputStream output,
            int transform) throws IOException {
        output.writeByte(0xFF);
        output.writeByte(0xEE);
        output.writeShort(14);
        output.writeBytes("Adobe");
        output.writeShort(100);
        output.writeShort(0);
        output.writeShort(0);
        output.writeByte(transform);
    }

    private int copyEntropy(
            DataInputStream input,
            DataOutputStream output) throws IOException {
        while (true) {
            int value = input.readUnsignedByte();
            if (value != 0xFF) {
                output.writeByte(value);
                continue;
            }
            int fillBytes = 1;
            int marker;
            do {
                marker = input.readUnsignedByte();
                if (marker == 0xFF) {
                    fillBytes++;
                }
            } while (marker == 0xFF);
            if (marker == 0x00
                    || marker >= 0xD0 && marker <= 0xD7) {
                for (int index = 0; index < fillBytes; index++) {
                    output.writeByte(0xFF);
                }
                output.writeByte(marker);
                continue;
            }
            return marker;
        }
    }

    private void copyExactly(
            DataInputStream input,
            DataOutputStream output,
            int bytes) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int remaining = bytes;
        while (remaining > 0) {
            int read = input.read(
                buffer,
                0,
                Math.min(buffer.length, remaining)
            );
            if (read < 0) {
                throw invalidJpeg();
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private JpegInfo markerMetadata(
            int orientation,
            int width,
            int height,
            int components,
            int adobeTransform,
            boolean progressive) {
        if (width < 1
                || height < 1
                || components != 1
                    && components != 3
                    && components != 4
                || adobeTransform == 1 && components != 3
                || adobeTransform == 2 && components != 4) {
            throw invalidJpeg();
        }
        return new JpegInfo(
            width,
            height,
            orientation,
            components,
            adobeTransform,
            progressive
        );
    }

    private int adobeTransform(byte[] payload) {
        boolean adobe = payload.length >= 12
            && payload[0] == 'A'
            && payload[1] == 'd'
            && payload[2] == 'o'
            && payload[3] == 'b'
            && payload[4] == 'e';
        if (!adobe) {
            return -1;
        }
        int transform = Byte.toUnsignedInt(payload[11]);
        if (transform > 2) {
            throw invalidJpeg();
        }
        return transform;
    }

    private boolean isStartOfFrame(int marker) {
        return marker >= 0xC0
            && marker <= 0xCF
            && marker != 0xC4
            && marker != 0xC8
            && marker != 0xCC;
    }

    private int parseExif(byte[] payload) {
        if (payload.length < 14
                || payload[0] != 'E'
                || payload[1] != 'x'
                || payload[2] != 'i'
                || payload[3] != 'f'
                || payload[4] != 0
                || payload[5] != 0) {
            return 0;
        }
        ByteOrder byteOrder;
        if (payload[6] == 'I' && payload[7] == 'I') {
            byteOrder = ByteOrder.LITTLE_ENDIAN;
        } else if (payload[6] == 'M' && payload[7] == 'M') {
            byteOrder = ByteOrder.BIG_ENDIAN;
        } else {
            return 0;
        }
        ByteBuffer tiff = ByteBuffer.wrap(payload)
            .order(byteOrder);
        if (unsignedShort(tiff, 8) != 42) {
            return 0;
        }
        long ifdOffset = unsignedInt(tiff, 10);
        long ifdStart = 6L + ifdOffset;
        if (ifdStart < 0 || ifdStart + 2 > payload.length) {
            return 0;
        }
        int entries = unsignedShort(tiff, (int) ifdStart);
        long entriesEnd = ifdStart + 2L + 12L * entries;
        if (entriesEnd > payload.length) {
            return 0;
        }
        for (int index = 0; index < entries; index++) {
            int offset = (int) ifdStart + 2 + index * 12;
            int tag = unsignedShort(tiff, offset);
            int type = unsignedShort(tiff, offset + 2);
            long count = unsignedInt(tiff, offset + 4);
            if (tag == 0x0112 && type == 3 && count == 1) {
                int orientation = unsignedShort(tiff, offset + 8);
                return orientation >= 1 && orientation <= 8
                    ? orientation
                    : 0;
            }
        }
        return 0;
    }

    private int unsignedShort(ByteBuffer buffer, int offset) {
        return Short.toUnsignedInt(buffer.getShort(offset));
    }

    private int unsignedShort(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset]) << 8)
            | Byte.toUnsignedInt(bytes[offset + 1]);
    }

    private long unsignedInt(ByteBuffer buffer, int offset) {
        return Integer.toUnsignedLong(buffer.getInt(offset));
    }

    private OperationException invalidJpeg() {
        return new OperationException(
            "INVALID_JPEG",
            "An input is not a readable JPEG image"
        );
    }

    record JpegInfo(
        int width,
        int height,
        int orientation,
        int components,
        int adobeTransform,
        boolean progressive
    ) {

        boolean adobe() {
            return adobeTransform >= 0;
        }

        int displayWidth() {
            return orientation >= 5 ? height : width;
        }

        int displayHeight() {
            return orientation >= 5 ? width : height;
        }
    }

}
