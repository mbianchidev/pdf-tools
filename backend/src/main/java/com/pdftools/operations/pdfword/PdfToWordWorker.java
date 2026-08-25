package com.pdftools.operations.pdfword;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import com.pdftools.operations.shared.extraction.PdfImageExtractionBudget;
import com.pdftools.operations.shared.extraction.PdfPageContent;
import com.pdftools.operations.shared.extraction.PositionedPdfImageExtractor;
import com.pdftools.operations.shared.extraction.PositionedPdfPageRasterizer;
import com.pdftools.operations.shared.extraction.PositionedPdfTextExtractor;
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
            PositionedPdfTextExtractor textExtractor =
                new PositionedPdfTextExtractor(
                    properties.getMaxTextCharacters(),
                    "PDF_WORD",
                    "Word"
                );
            PdfImageExtractionBudget imageBudget =
                new PdfImageExtractionBudget(
                    properties,
                    "PDF_WORD",
                    "Word"
                );
            PositionedPdfPageRasterizer rasterizer =
                new PositionedPdfPageRasterizer(
                    document,
                    properties,
                    imageBudget,
                    "PDF_WORD",
                    "Word"
                );
            List<PdfPageContent> extracted = new ArrayList<>(
                pages.size()
            );
            for (int index = 0; index < pages.size(); index++) {
                var page = pages.get(index);
                VisualPageSpace pageSpace = VisualPageSpace.from(page);
                List<PdfPageContent.TextLine> lines;
                List<PdfPageContent.PageImage> images;
                if (request.plan().mode().equals("visual")) {
                    lines = List.of();
                    images = List.of(rasterizer.render(index, pageSpace));
                } else {
                    lines = textExtractor.extract(document, index);
                    images = request.plan().includeImages()
                        ? new PositionedPdfImageExtractor(
                            page,
                            properties,
                            imageBudget,
                            "PDF_WORD",
                            "Word"
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
                extracted.add(new PdfPageContent(
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
