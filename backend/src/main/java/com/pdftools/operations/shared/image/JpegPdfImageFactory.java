package com.pdftools.operations.shared.image;

import com.pdftools.operations.CheckpointInputStream;
import com.pdftools.operations.OperationException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceCMYK;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class JpegPdfImageFactory {

    public PDImageXObject create(
            PDDocument document,
            Path imagePath,
            JpegInspector.JpegInfo info,
            Runnable cancellationCheck) throws IOException {
        try (InputStream fileInput = Files.newInputStream(imagePath);
             InputStream checked = new CheckpointInputStream(
                 fileInput,
                 cancellationCheck
             )) {
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
            return image;
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
}
