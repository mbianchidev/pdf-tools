package com.pdftools.operations.pdfmarkdown;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import com.pdftools.operations.shared.extraction.PdfImageExtractionBudget;
import com.pdftools.operations.shared.extraction.PdfPageContent;
import com.pdftools.operations.shared.extraction.PositionedPdfImageExtractor;
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

final class PdfToMarkdownWorker {

    void convert(
            PdfToMarkdownRequest request,
            Path progressFile) {
        PdfToMarkdownProperties properties = request.properties();
        Path scratch = request.workspace().resolve(
            ".pdf-markdown-scratch"
        );
        try {
            Files.createDirectories(scratch);
        } catch (IOException exception) {
            throw new OperationException(
                "PDF_MARKDOWN_SCRATCH_FAILED",
                "PDF-to-Markdown scratch could not be created",
                exception
            );
        }
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratch.toFile());
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
                    "PDF_MARKDOWN",
                    "Markdown"
                );
            PdfImageExtractionBudget imageBudget =
                new PdfImageExtractionBudget(
                    properties,
                    "PDF_MARKDOWN",
                    "Markdown"
                );
            List<PdfPageContent> extracted = new ArrayList<>(
                pages.size()
            );
            boolean hasText = false;
            for (int index = 0; index < pages.size(); index++) {
                var page = pages.get(index);
                VisualPageSpace space = VisualPageSpace.from(page);
                List<PdfPageContent.TextLine> lines =
                    textExtractor.extract(document, index);
                hasText |= lines.stream()
                    .anyMatch(line -> !line.text().isBlank());
                List<PdfPageContent.PageImage> images =
                    request.plan().includeImages()
                        ? new PositionedPdfImageExtractor(
                            page,
                            properties,
                            imageBudget,
                            "PDF_MARKDOWN",
                            "Markdown"
                        ).extract()
                        : List.of();
                extracted.add(new PdfPageContent(
                    space.width(),
                    space.height(),
                    space.userUnit(),
                    lines,
                    images
                ));
                writeProgress(progressFile, index + 1);
            }
            if (!hasText) {
                throw new OperationException(
                    "IMAGE_ONLY_PDF_NOT_SUPPORTED",
                    "PDF to Markdown requires extractable text; "
                        + "image-only PDFs are not supported"
                );
            }
            new PdfToMarkdownBundleWriter(properties).write(
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
                "PDF_MARKDOWN_CONVERSION_FAILED",
                "The PDF could not be converted to Markdown",
                exception
            );
        }
    }

    private void writeProgress(Path path, int completed) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
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
                "PDF_MARKDOWN_WORKER_PROTOCOL_ERROR",
                "PDF-to-Markdown progress could not be written",
                exception
            );
        }
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "PDF to Markdown requires an unencrypted PDF",
            cause
        );
    }
}
