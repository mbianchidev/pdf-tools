package com.pdftools.operations.watermark;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.shared.image.JpegImageTransform;
import com.pdftools.operations.shared.image.JpegInspector;
import com.pdftools.operations.shared.image.JpegPdfImageFactory;
import com.pdftools.operations.shared.image.JpegResourceGuard;
import com.pdftools.operations.shared.image.JpegValidationInput;
import com.pdftools.operations.shared.image.JpegValidationProperties;
import com.pdftools.operations.shared.image.JpegValidationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Component
public class WatermarkImagePreparer {

    private final WatermarkProperties properties;
    private final JpegValidationService validationService;
    private final JpegValidationProperties validationProperties;
    private final JpegPdfImageFactory jpegImageFactory;
    private final JpegInspector jpegInspector = new JpegInspector();

    public WatermarkImagePreparer(
            WatermarkProperties properties,
            JpegValidationService validationService,
            JpegValidationProperties validationProperties,
            JpegPdfImageFactory jpegImageFactory) {
        this.properties = properties;
        this.validationService = validationService;
        this.validationProperties = validationProperties;
        this.jpegImageFactory = jpegImageFactory;
    }

    public PreparedImage prepare(
            OperationInput input,
            Path workspace,
            Runnable cancellationCheck) {
        String filename = input.originalFilename()
            .toLowerCase(Locale.ROOT);
        if (filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")) {
            return prepareJpeg(input, workspace, cancellationCheck);
        }
        return preparePng(input, cancellationCheck);
    }

    private PreparedImage prepareJpeg(
            OperationInput input,
            Path workspace,
            Runnable cancellationCheck) {
        JpegInspector.JpegInfo info = jpegInspector.inspect(
            input.path(),
            cancellationCheck
        );
        JpegResourceGuard.enforce(
            info,
            properties.getMaxImageDimension(),
            properties.getMaxImagePixels(),
            validationProperties.getMaxProgressiveCoefficientBytes(),
            this::imageLimit,
            this::imageLimit
        );
        JpegValidationService.ValidationArtifacts artifacts =
            validationService.validate(
                List.of(new JpegValidationInput(input.path(), info)),
                workspace,
                cancellationCheck
            );
        return PreparedImage.jpeg(
            artifacts,
            info,
            jpegImageFactory
        );
    }

    private PreparedImage preparePng(
            OperationInput input,
            Runnable cancellationCheck) {
        cancellationCheck.run();
        try (FileImageInputStream imageInput =
                new FileImageInputStream(input.path().toFile())) {
            Iterator<ImageReader> readers =
                ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                if (!reader.getFormatName().equalsIgnoreCase("png")) {
                    throw invalidImage();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                enforceDimensions(width, height);
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
                cancellationCheck.run();
                return PreparedImage.png(image);
            } finally {
                reader.dispose();
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new OperationException(
                "INVALID_WATERMARK_IMAGE",
                "The watermark image could not be decoded",
                exception
            );
        }
    }

    private void enforceDimensions(int width, int height) {
        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, height);
        } catch (ArithmeticException exception) {
            throw imageLimit();
        }
        if (width < 1
                || height < 1
                || width > properties.getMaxImageDimension()
                || height > properties.getMaxImageDimension()
                || pixels > properties.getMaxImagePixels()) {
            throw imageLimit();
        }
    }

    private OperationException imageLimit() {
        return new OperationException(
            "WATERMARK_IMAGE_DIMENSION_LIMIT_EXCEEDED",
            "The watermark image exceeds the configured dimensions"
        );
    }

    private OperationException invalidImage() {
        return new OperationException(
            "INVALID_WATERMARK_IMAGE",
            "The watermark image must be a readable PNG or JPEG"
        );
    }

    public static final class PreparedImage implements AutoCloseable {

        private final BufferedImage png;
        private final JpegValidationService.ValidationArtifacts artifacts;
        private final JpegInspector.JpegInfo jpegInfo;
        private final JpegPdfImageFactory jpegImageFactory;

        private PreparedImage(
                BufferedImage png,
                JpegValidationService.ValidationArtifacts artifacts,
                JpegInspector.JpegInfo jpegInfo,
                JpegPdfImageFactory jpegImageFactory) {
            this.png = png;
            this.artifacts = artifacts;
            this.jpegInfo = jpegInfo;
            this.jpegImageFactory = jpegImageFactory;
        }

        static PreparedImage png(BufferedImage image) {
            return new PreparedImage(image, null, null, null);
        }

        static PreparedImage jpeg(
                JpegValidationService.ValidationArtifacts artifacts,
                JpegInspector.JpegInfo info,
                JpegPdfImageFactory imageFactory) {
            return new PreparedImage(
                null,
                artifacts,
                info,
                imageFactory
            );
        }

        public int displayWidth() {
            return jpegInfo == null
                ? png.getWidth()
                : jpegInfo.displayWidth();
        }

        public int displayHeight() {
            return jpegInfo == null
                ? png.getHeight()
                : jpegInfo.displayHeight();
        }

        public PDImageXObject create(
                PDDocument document,
                Runnable cancellationCheck) throws IOException {
            cancellationCheck.run();
            if (jpegInfo != null) {
                return jpegImageFactory.create(
                    document,
                    artifacts.paths().getFirst(),
                    jpegInfo,
                    cancellationCheck
                );
            }
            return LosslessFactory.createFromImage(document, png);
        }

        public Matrix matrix(
                float x,
                float y,
                float width,
                float height) {
            if (jpegInfo != null) {
                return JpegImageTransform.matrix(
                    jpegInfo,
                    x,
                    y,
                    width,
                    height
                );
            }
            return new Matrix(width, 0, 0, height, x, y);
        }

        @Override
        public void close() {
            if (png != null) {
                png.flush();
            }
            if (artifacts != null) {
                artifacts.close();
            }
        }
    }
}
