package com.pdftools.operations.pdfexcel;

import com.pdftools.operations.OperationException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import com.pdftools.operations.shared.extraction.PdfPageContent;
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

final class PdfToExcelWorker {

    void convert(PdfToExcelRequest request, Path progressFile) {
        PdfToExcelProperties properties = request.properties();
        Path scratch = request.workspace().resolve(".pdf-excel-scratch");
        try {
            Files.createDirectories(scratch);
        } catch (IOException exception) {
            throw new OperationException(
                "PDF_EXCEL_SCRATCH_FAILED",
                "PDF-to-Excel scratch could not be created",
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
            PositionedPdfTextExtractor extractor =
                new PositionedPdfTextExtractor(
                    properties.getMaxTextCharacters(),
                    "PDF_EXCEL",
                    "Excel"
                );
            List<PdfPageContent> extracted = new ArrayList<>(
                pages.size()
            );
            for (int index = 0; index < pages.size(); index++) {
                var page = pages.get(index);
                VisualPageSpace space = VisualPageSpace.from(page);
                extracted.add(new PdfPageContent(
                    space.width(),
                    space.height(),
                    space.userUnit(),
                    extractor.extract(document, index),
                    List.of()
                ));
                writeProgress(progressFile, index + 1);
            }
            new PdfToExcelWorkbookWriter(properties).write(
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
                "PDF_EXCEL_CONVERSION_FAILED",
                "The PDF could not be converted to Excel",
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
                "PDF_EXCEL_WORKER_PROTOCOL_ERROR",
                "PDF-to-Excel progress could not be written",
                exception
            );
        }
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "PDF to Excel requires an unencrypted PDF",
            cause
        );
    }
}
