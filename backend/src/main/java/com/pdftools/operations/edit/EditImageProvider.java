package com.pdftools.operations.edit;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.OperationInput;
import com.pdftools.operations.watermark.WatermarkImagePreparer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EditImageProvider {

    private final List<OperationInput> inputs;
    private final Path workspace;
    private final WatermarkImagePreparer preparer;
    private final EditProperties properties;
    private final Map<Integer, ImageAsset> assets = new HashMap<>();
    private long totalDecodedBytes;

    EditImageProvider(
            List<OperationInput> inputs,
            Path workspace,
            WatermarkImagePreparer preparer,
            EditProperties properties) {
        this.inputs = List.copyOf(inputs);
        this.workspace = workspace;
        this.preparer = preparer;
        this.properties = properties;
    }

    ImageAsset get(
            int index,
            PDDocument document,
            Runnable cancellationCheck) {
        ImageAsset existing = assets.get(index);
        if (existing != null) {
            return existing;
        }
        Path imageWorkspace = workspace.resolve(
            ".edit-image-" + index
        );
        try {
            Files.createDirectories(imageWorkspace);
        } catch (IOException exception) {
            throw new OperationException(
                "EDIT_IMAGE_SCRATCH_FAILED",
                "Edit image scratch storage could not be created",
                exception
            );
        }
        WatermarkImagePreparer.PreparedImage prepared =
            preparer.prepare(
                inputs.get(index),
                imageWorkspace,
                cancellationCheck
            );
        try {
            totalDecodedBytes = Math.addExact(
                totalDecodedBytes,
                prepared.decodedBytes()
            );
            if (totalDecodedBytes
                    > properties.getMaxTotalDecodedImageBytes()) {
                throw new OperationException(
                    "EDIT_DECODED_IMAGE_LIMIT_EXCEEDED",
                    "Edit images exceed the decoded memory budget"
                );
            }
            PDImageXObject image = prepared.create(
                document,
                cancellationCheck
            );
            ImageAsset asset = new ImageAsset(image, prepared);
            assets.put(index, asset);
            return asset;
        } catch (IOException exception) {
            throw new OperationException(
                "EDIT_IMAGE_EMBED_FAILED",
                "An edit image could not be embedded",
                exception
            );
        } finally {
            prepared.close();
        }
    }

    record ImageAsset(
        PDImageXObject image,
        WatermarkImagePreparer.PreparedImage prepared
    ) {
        int displayWidth() {
            return prepared.displayWidth();
        }

        int displayHeight() {
            return prepared.displayHeight();
        }

        Matrix matrix(
                float x,
                float y,
                float width,
                float height) {
            return prepared.matrix(x, y, width, height);
        }
    }
}
