package com.pdftools.operations.jpgpdf;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.CheckpointInputStream;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.OutputLimitExceededException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceCMYK;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

@Component
public class JpgToPdfEngine {

    private static final Logger logger =
        LoggerFactory.getLogger(JpgToPdfEngine.class);
    private static final float A4_WIDTH = 595.28f;
    private static final float A4_HEIGHT = 841.89f;
    private static final float LETTER_WIDTH = 612;
    private static final float LETTER_HEIGHT = 792;
    private static final float LEGAL_WIDTH = 612;
    private static final float LEGAL_HEIGHT = 1008;

    private final JpgToPdfProperties properties;
    private final JpegInspector inspector = new JpegInspector();
    private final JpegValidationService validationService;

    public JpgToPdfEngine(
            JpgToPdfProperties properties,
            JpegValidationService validationService) {
        this.properties = properties;
        this.validationService = validationService;
    }

    public Path create(
            List<OperationInput> inputs,
            Path workspace,
            JpgToPdfPlanFactory.JpgToPdfPlan plan,
            IntConsumer progress,
            Runnable cancellationCheck) {
        Path scratchDirectory = workspace.resolve(".pdfbox-scratch");
        Path output = workspace.resolve("images.pdf");
        try {
            Files.createDirectories(scratchDirectory);
        } catch (IOException exception) {
            throw new OperationException(
                "JPG_PDF_SCRATCH_FAILED",
                "JPG-to-PDF scratch storage could not be created",
                exception
            );
        }
        List<JpegInspector.JpegInfo> images = inspect(
            inputs,
            cancellationCheck
        );
        List<JpegValidationInput> validationInputs =
            new ArrayList<>(inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            validationInputs.add(new JpegValidationInput(
                inputs.get(index).path(),
                images.get(index)
            ));
        }
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratchDirectory.toFile());
        try (JpegValidationService.ValidationArtifacts validated =
                 validationService.validate(
                     validationInputs,
                     workspace,
                     cancellationCheck
                 );
             PDDocument document = new PDDocument(scratchCache)) {
            for (int index = 0; index < inputs.size(); index++) {
                cancellationCheck.run();
                addImagePage(
                    document,
                    validated.paths().get(index),
                    images.get(index),
                    plan,
                    cancellationCheck
                );
                int nextProgress = 5 + (int) Math.floor(
                    85.0 * (index + 1) / inputs.size()
                );
                progress.accept(Math.min(nextProgress, 90));
            }
            setDeterministicDocumentId(document, inputs, plan);
            try (OutputStream fileOutput = Files.newOutputStream(output);
                 BoundedOutputStream bounded = new BoundedOutputStream(
                     fileOutput,
                     properties.getMaxOutputBytes(),
                     cancellationCheck
                 )) {
                document.save(bounded);
            }
            cancellationCheck.run();
            return output;
        } catch (OutputLimitExceededException exception) {
            throw cleanup(output, new OperationException(
                "JPG_PDF_OUTPUT_SIZE_LIMIT_EXCEEDED",
                "The generated PDF exceeds the configured output limit"
            ));
        } catch (OperationException | OperationCancelledException exception) {
            throw cleanup(output, exception);
        } catch (IOException exception) {
            throw cleanup(output, new OperationException(
                "JPG_PDF_CREATION_FAILED",
                "The JPG images could not be converted to PDF",
                exception
            ));
        }
    }

    private List<JpegInspector.JpegInfo> inspect(
            List<OperationInput> inputs,
            Runnable cancellationCheck) {
        List<JpegInspector.JpegInfo> result =
            new ArrayList<>(inputs.size());
        long totalPixels = 0;
        for (int index = 0; index < inputs.size(); index++) {
            OperationInput input = inputs.get(index);
            cancellationCheck.run();
            JpegInspector.JpegInfo info = inspector.inspect(
                input.path(),
                cancellationCheck
            );
            long pixels;
            try {
                pixels = Math.multiplyExact(
                    (long) info.width(),
                    info.height()
                );
                totalPixels = Math.addExact(totalPixels, pixels);
            } catch (ArithmeticException exception) {
                throw imageLimit();
            }
            if (info.width() < 1
                    || info.height() < 1
                    || info.width() > properties.getMaxImageDimension()
                    || info.height() > properties.getMaxImageDimension()
                    || pixels > properties.getMaxPixelsPerImage()
                    || totalPixels > properties.getMaxTotalPixels()) {
                throw imageLimit();
            }
            enforceProgressiveMemoryLimit(info);
            result.add(info);
        }
        return List.copyOf(result);
    }

    private void enforceProgressiveMemoryLimit(
            JpegInspector.JpegInfo info) {
        if (!info.progressive()) {
            return;
        }
        try {
            long blocksWide = Math.addExact(
                (long) info.width(),
                7
            ) / 8;
            long blocksHigh = Math.addExact(
                (long) info.height(),
                7
            ) / 8;
            long bytes = Math.multiplyExact(
                Math.multiplyExact(blocksWide, blocksHigh),
                128L * info.components()
            );
            if (bytes > properties.getMaxProgressiveCoefficientBytes()) {
                throw progressiveMemoryLimit();
            }
        } catch (ArithmeticException exception) {
            throw progressiveMemoryLimit();
        }
    }

    private OperationException progressiveMemoryLimit() {
        return new OperationException(
            "JPEG_PROGRESSIVE_MEMORY_LIMIT_EXCEEDED",
            "A progressive JPEG exceeds the validation memory limit"
        );
    }

    private void addImagePage(
            PDDocument document,
            Path imagePath,
            JpegInspector.JpegInfo info,
            JpgToPdfPlanFactory.JpgToPdfPlan plan,
            Runnable cancellationCheck) throws IOException {
        PageLayout layout = layout(info, plan);
        PDPage page = new PDPage(new PDRectangle(
            layout.pageWidth(),
            layout.pageHeight()
        ));
        document.addPage(page);
        try (InputStream fileInput = Files.newInputStream(imagePath);
             InputStream checked = new CheckpointInputStream(
                 fileInput,
                 cancellationCheck
             );
             PDPageContentStream content =
                 new PDPageContentStream(document, page)) {
            PDImageXObject image = new PDImageXObject(
                document,
                checked,
                COSName.DCT_DECODE,
                info.width(),
                info.height(),
                8,
                colorSpace(info.components())
            );
            if (info.components() == 4 && info.adobe()) {
                COSArray decode = new COSArray();
                for (int index = 0; index < 4; index++) {
                    decode.add(COSInteger.ONE);
                    decode.add(COSInteger.ZERO);
                }
                image.setDecode(decode);
            }
            content.drawImage(image, imageMatrix(info, layout));
        }
    }

    private PDColorSpace colorSpace(int components) {
        return switch (components) {
            case 1 -> PDDeviceGray.INSTANCE;
            case 3 -> PDDeviceRGB.INSTANCE;
            case 4 -> PDDeviceCMYK.INSTANCE;
            default -> throw new OperationException(
                "INVALID_JPEG",
                "JPEG color components are not supported"
            );
        };
    }

    private void setDeterministicDocumentId(
            PDDocument document,
            List<OperationInput> inputs,
            JpgToPdfPlanFactory.JpgToPdfPlan plan) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
        inputs.forEach(input -> {
            digest.update(input.sha256().getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
        });
        digest.update(plan.pageSize().getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        digest.update(plan.orientation().getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        digest.update(Float.toString(plan.margin())
            .getBytes(StandardCharsets.US_ASCII));
        byte[] id = digest.digest();
        COSArray ids = new COSArray();
        ids.add(new COSString(id));
        ids.add(new COSString(id));
        document.getDocument().setDocumentID(ids);
    }

    private PageLayout layout(
            JpegInspector.JpegInfo info,
            JpgToPdfPlanFactory.JpgToPdfPlan plan) {
        float pageWidth;
        float pageHeight;
        if (plan.pageSize().equals("fit")) {
            float imageWidth = info.displayWidth() * 72f
                / plan.fitImageDpi();
            float imageHeight = info.displayHeight() * 72f
                / plan.fitImageDpi();
            float maxContent = plan.maxFitPagePoints() - 2 * plan.margin();
            float scale = Math.min(
                1,
                Math.min(maxContent / imageWidth, maxContent / imageHeight)
            );
            pageWidth = imageWidth * scale + 2 * plan.margin();
            pageHeight = imageHeight * scale + 2 * plan.margin();
        } else {
            float[] paper = paperSize(plan.pageSize());
            pageWidth = paper[0];
            pageHeight = paper[1];
        }
        boolean landscape = switch (plan.orientation()) {
            case "portrait" -> false;
            case "landscape" -> true;
            default -> info.displayWidth() > info.displayHeight();
        };
        if (landscape != (pageWidth > pageHeight)) {
            float swap = pageWidth;
            pageWidth = pageHeight;
            pageHeight = swap;
        }
        float availableWidth = pageWidth - 2 * plan.margin();
        float availableHeight = pageHeight - 2 * plan.margin();
        float scale = Math.min(
            availableWidth / info.displayWidth(),
            availableHeight / info.displayHeight()
        );
        float drawWidth = info.displayWidth() * scale;
        float drawHeight = info.displayHeight() * scale;
        return new PageLayout(
            pageWidth,
            pageHeight,
            (pageWidth - drawWidth) / 2,
            (pageHeight - drawHeight) / 2,
            drawWidth,
            drawHeight
        );
    }

    private float[] paperSize(String pageSize) {
        return switch (pageSize) {
            case "a4" -> new float[]{A4_WIDTH, A4_HEIGHT};
            case "letter" -> new float[]{LETTER_WIDTH, LETTER_HEIGHT};
            case "legal" -> new float[]{LEGAL_WIDTH, LEGAL_HEIGHT};
            default -> throw new IllegalArgumentException(
                "Unexpected standard page size: " + pageSize
            );
        };
    }

    private Matrix imageMatrix(
            JpegInspector.JpegInfo info,
            PageLayout layout) {
        float x = layout.x();
        float y = layout.y();
        float width = layout.drawWidth();
        float height = layout.drawHeight();
        return switch (info.orientation()) {
            case 2 -> new Matrix(-width, 0, 0, height, x + width, y);
            case 3 -> new Matrix(
                -width,
                0,
                0,
                -height,
                x + width,
                y + height
            );
            case 4 -> new Matrix(
                width,
                0,
                0,
                -height,
                x,
                y + height
            );
            case 5 -> new Matrix(
                0,
                -height,
                -width,
                0,
                x + width,
                y + height
            );
            case 6 -> new Matrix(
                0,
                -height,
                width,
                0,
                x,
                y + height
            );
            case 7 -> new Matrix(0, height, width, 0, x, y);
            case 8 -> new Matrix(
                0,
                height,
                -width,
                0,
                x + width,
                y
            );
            default -> new Matrix(width, 0, 0, height, x, y);
        };
    }

    private OperationException imageLimit() {
        return new OperationException(
            "JPEG_DIMENSION_LIMIT_EXCEEDED",
            "JPEG dimensions exceed the configured conversion limit"
        );
    }

    private <T extends RuntimeException> T cleanup(
            Path output,
            T failure) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            OperationException cleanupFailure = new OperationException(
                "JPG_PDF_CLEANUP_FAILED",
                "Partial PDF output could not be removed",
                exception
            );
            failure.addSuppressed(cleanupFailure);
            logger.error(
                "Could not remove partial JPG-to-PDF output {}",
                output,
                cleanupFailure
            );
        }
        return failure;
    }

    private record PageLayout(
        float pageWidth,
        float pageHeight,
        float x,
        float y,
        float drawWidth,
        float drawHeight
    ) {
    }
}
