package com.pdftools.operations.pdfword;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import com.pdftools.operations.shared.pdf.PdfPageTreeReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class PdfToWordWorker {

    void convert(PdfToWordRequest request, Path progressFile) {
        PdfToWordProperties properties = request.properties();
        Path scratchDirectory = request.workspace().resolve(
            ".pdf-word-scratch"
        );
        try {
            Files.createDirectories(scratchDirectory);
        } catch (IOException exception) {
            throw new OperationException(
                "PDF_WORD_SCRATCH_FAILED",
                "PDF-to-Word scratch could not be created",
                exception
            );
        }
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratchDirectory.toFile());
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(request.source());
             PDDocument document = Loader.loadPDF(
                 randomAccess,
                 scratchCache
             )) {
            if (document.isEncrypted()) {
                throw encryptedPdf(null);
            }
            List<org.apache.pdfbox.pdmodel.PDPage> pages =
                new PdfPageTreeReader(properties).read(
                    document,
                    () -> {
                    }
                ).pages();
            PdfToWordTextExtractor textExtractor =
                new PdfToWordTextExtractor(
                    properties.getMaxTextCharacters()
                );
            PdfToWordImageBudget imageBudget =
                new PdfToWordImageBudget(properties);
            PdfToWordRasterizer rasterizer = new PdfToWordRasterizer(
                document,
                properties,
                imageBudget
            );
            List<PdfToWordPage> extracted = new ArrayList<>(
                pages.size()
            );
            for (int index = 0; index < pages.size(); index++) {
                var page = pages.get(index);
                VisualPageSpace pageSpace = VisualPageSpace.from(page);
                List<PdfToWordPage.TextLine> lines;
                List<PdfToWordPage.PageImage> images;
                if (request.plan().mode().equals("visual")) {
                    lines = List.of();
                    images = List.of(rasterizer.render(index, pageSpace));
                } else {
                    lines = textExtractor.extract(document, index);
                    images = request.plan().includeImages()
                        ? new PdfToWordImageExtractor(
                            page,
                            properties,
                            imageBudget
                        ).extract()
                        : List.of();
                    if (lines.isEmpty()
                            && images.isEmpty()
                            && request.plan().includeImages()) {
                        images = List.of(
                            rasterizer.render(index, pageSpace)
                        );
                    }
                }
                extracted.add(new PdfToWordPage(
                    pageSpace.width(),
                    pageSpace.height(),
                    pageSpace.userUnit(),
                    lines,
                    images
                ));
                writeProgress(progressFile, index + 1);
            }
            new PdfToWordDocxWriter(properties).write(
                extracted,
                request.plan(),
                request.output(),
                () -> {
                }
            );
        } catch (InvalidPasswordException exception) {
            throw encryptedPdf(exception);
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new OperationException(
                "PDF_WORD_CONVERSION_FAILED",
                "The PDF could not be converted to Word",
                exception
            );
        }
    }

    private void writeProgress(Path path, int completed) {
        Path temporary = path.resolveSibling(
            path.getFileName() + ".tmp"
        );
        try {
            Files.writeString(temporary, Integer.toString(completed));
            try {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            throw new OperationException(
                "PDF_WORD_WORKER_PROTOCOL_ERROR",
                "PDF-to-Word progress could not be written",
                exception
            );
        }
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "PDF to Word requires an unencrypted PDF",
            cause
        );
    }
}
