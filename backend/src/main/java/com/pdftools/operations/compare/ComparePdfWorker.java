package com.pdftools.operations.compare;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.coordinates.VisualPageSpace;
import com.pdftools.operations.shared.extraction.PdfPageContent;
import com.pdftools.operations.shared.extraction.PositionedPdfTextExtractor;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import com.pdftools.operations.shared.pdf.PdfPageTreeReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ComparePdfWorker {

    private static final Logger logger =
        LoggerFactory.getLogger(ComparePdfWorker.class);

    private final CompareWorkerRequest request;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ComparePdfWorker(CompareWorkerRequest request) {
        this.request = request;
    }

    void compare(Path progressFile) {
        validateInput(request.baseline());
        validateInput(request.candidate());
        Path baselineScratch = request.workspace().resolve(
            ".compare-baseline-scratch"
        );
        Path candidateScratch = request.workspace().resolve(
            ".compare-candidate-scratch"
        );
        createDirectories(baselineScratch, candidateScratch);
        RandomAccessStreamCache.StreamCacheCreateFunction baselineCache =
            () -> new ScratchFile(baselineScratch.toFile());
        RandomAccessStreamCache.StreamCacheCreateFunction candidateCache =
            () -> new ScratchFile(candidateScratch.toFile());
        RuntimeException failure = null;
        try (RandomAccessReadBufferedFile baselineAccess =
                 new RandomAccessReadBufferedFile(request.baseline());
             RandomAccessReadBufferedFile candidateAccess =
                 new RandomAccessReadBufferedFile(request.candidate());
             PDDocument baseline = Loader.loadPDF(
                 baselineAccess,
                 baselineCache
             );
             PDDocument candidate = Loader.loadPDF(
                 candidateAccess,
                 candidateCache
             )) {
            if (baseline.isEncrypted() || candidate.isEncrypted()) {
                throw encryptedPdf(null);
            }
            List<PDPage> baselinePages =
                new PdfPageTreeReader(request).read(
                    baseline,
                    () -> {
                    }
                ).pages();
            List<PDPage> candidatePages =
                new PdfPageTreeReader(request).read(
                    candidate,
                    () -> {
                    }
                ).pages();
            if (baselinePages.isEmpty() || candidatePages.isEmpty()) {
                throw invalidPdf(null);
            }
            CompareReport report = comparePages(
                baseline,
                baselinePages,
                candidate,
                candidatePages,
                progressFile
            );
            byte[] reportBytes = objectMapper.writeValueAsBytes(report);
            if (reportBytes.length > request.maxReportBytes()) {
                throw new OperationException(
                    "COMPARE_REPORT_LIMIT_EXCEEDED",
                    "The PDF comparison report exceeds its size limit"
                );
            }
            Files.write(request.report(), reportBytes);
            writeArchive(report, reportBytes);
        } catch (InvalidPasswordException exception) {
            failure = encryptedPdf(exception);
            throw failure;
        } catch (OperationException exception) {
            failure = exception;
            throw exception;
        } catch (IOException | RuntimeException exception) {
            failure = new OperationException(
                "PDF_COMPARE_FAILED",
                "The PDFs could not be compared",
                exception
            );
            throw failure;
        } finally {
            if (failure != null) {
                delete(request.report(), failure);
                delete(request.archive(), failure);
            }
        }
    }

    private CompareReport comparePages(
            PDDocument baseline,
            List<PDPage> baselinePages,
            PDDocument candidate,
            List<PDPage> candidatePages,
            Path progressFile) throws IOException {
        PositionedPdfTextExtractor baselineText =
            new PositionedPdfTextExtractor(
                request.maxTextCharactersPerDocument(),
                "COMPARE_BASELINE",
                "baseline comparison"
            );
        PositionedPdfTextExtractor candidateText =
            new PositionedPdfTextExtractor(
                request.maxTextCharactersPerDocument(),
                "COMPARE_CANDIDATE",
                "candidate comparison"
            );
        TextLayoutComparator textLayout = new TextLayoutComparator(
            properties(),
            request.plan().layoutTolerancePoints()
        );
        RenderedPageComparator rendered = new RenderedPageComparator(
            properties(),
            request.plan()
        );
        PDFRenderer baselineRenderer = new PDFRenderer(baseline);
        PDFRenderer candidateRenderer = new PDFRenderer(candidate);
        baselineRenderer.setSubsamplingAllowed(true);
        candidateRenderer.setSubsamplingAllowed(true);
        int comparedPages = Math.max(
            baselinePages.size(),
            candidatePages.size()
        );
        List<CompareReport.PageComparison> pages = new ArrayList<>(
            comparedPages
        );
        int textChangedPages = 0;
        int layoutChangedPages = 0;
        int visualChangedPages = 0;
        int addedLines = 0;
        int removedLines = 0;
        double maxVisualDifference = 0;
        for (int index = 0; index < comparedPages; index++) {
            boolean baselinePresent = index < baselinePages.size();
            boolean candidatePresent = index < candidatePages.size();
            VisualPageSpace baselineSpace = baselinePresent
                ? VisualPageSpace.from(baselinePages.get(index))
                : null;
            VisualPageSpace candidateSpace = candidatePresent
                ? VisualPageSpace.from(candidatePages.get(index))
                : null;
            List<PdfPageContent.TextLine> baselineLines =
                baselinePresent
                    ? baselineText.extract(baseline, index)
                    : List.of();
            List<PdfPageContent.TextLine> candidateLines =
                candidatePresent
                    ? candidateText.extract(candidate, index)
                    : List.of();
            TextLayoutComparator.Result textResult = textLayout.compare(
                baselineLines,
                baselineSpace,
                candidateLines,
                candidateSpace
            );
            CompareReport.VisualComparison visual = rendered.compare(
                baselineRenderer,
                baseline,
                baselinePresent ? index : -1,
                candidateRenderer,
                candidate,
                candidatePresent ? index : -1,
                request.workspace(),
                index + 1
            );
            CompareReport.PageComparison page =
                new CompareReport.PageComparison(
                    index + 1,
                    baselinePresent,
                    candidatePresent,
                    textResult.text(),
                    textResult.layout(),
                    visual
                );
            pages.add(page);
            textChangedPages += page.text().changed() ? 1 : 0;
            layoutChangedPages += page.layout().changed() ? 1 : 0;
            visualChangedPages += page.visual().changed() ? 1 : 0;
            addedLines += page.text().addedLines();
            removedLines += page.text().removedLines();
            maxVisualDifference = Math.max(
                maxVisualDifference,
                page.visual().differencePercent()
            );
            writeProgress(progressFile, index + 1);
        }
        boolean identical = pages.stream()
            .noneMatch(CompareReport.PageComparison::changed);
        return new CompareReport(
            identical ? "identical" : "different",
            new CompareReport.Summary(
                baselinePages.size(),
                candidatePages.size(),
                comparedPages,
                textChangedPages,
                layoutChangedPages,
                visualChangedPages,
                addedLines,
                removedLines,
                maxVisualDifference
            ),
            List.copyOf(pages)
        );
    }

    private CompareProperties properties() {
        CompareProperties properties = new CompareProperties();
        properties.setMaxTextLinesPerPage(
            request.maxTextLinesPerPage()
        );
        properties.setMaxLineCharacters(request.maxLineCharacters());
        properties.setMaxDiffMatrixCells(
            request.maxDiffMatrixCells()
        );
        properties.setMaxTextChanges(request.maxTextChanges());
        properties.setMaxPixelsPerPage(request.maxPixelsPerPage());
        properties.setMaxTotalRenderPixels(
            request.maxTotalRenderPixels()
        );
        properties.setMaxImageDimension(request.maxImageDimension());
        properties.setMaxDiffImageBytes(request.maxDiffImageBytes());
        properties.setMaxTotalDiffImageBytes(
            request.maxTotalDiffImageBytes()
        );
        return properties;
    }

    private void writeArchive(
            CompareReport report,
            byte[] reportBytes) throws IOException {
        try (OutputStream fileOutput =
                 Files.newOutputStream(request.archive());
             BoundedOutputStream bounded = new BoundedOutputStream(
                 fileOutput,
                 request.maxArchiveBytes(),
                 () -> {
                 }
             );
             ZipOutputStream zip = new ZipOutputStream(
                 new BufferedOutputStream(bounded)
             )) {
            zip.putNextEntry(entry("comparison-report.json"));
            zip.write(reportBytes);
            zip.closeEntry();
            for (CompareReport.PageComparison page : report.pages()) {
                String name = page.visual().diffImage();
                if (name == null) {
                    continue;
                }
                Path image = request.workspace().resolve(
                    Path.of(name).getFileName()
                );
                zip.putNextEntry(entry(name));
                try (InputStream input = Files.newInputStream(image)) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
        } catch (OutputLimitExceededException exception) {
            throw new OperationException(
                "COMPARE_ARCHIVE_LIMIT_EXCEEDED",
                "The comparison archive exceeds its size limit",
                exception
            );
        }
    }

    private ZipEntry entry(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        return entry;
    }

    private void writeProgress(Path path, int pages) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, Integer.toString(pages));
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
    }

    private void validateInput(Path source) {
        PdfInputValidator.requirePdfHeader(source);
        try {
            if (Files.isSymbolicLink(source)
                    || !Files.isRegularFile(
                        source,
                        LinkOption.NOFOLLOW_LINKS)
                    || Files.size(source) < 1
                    || Files.size(source) > request.maxInputBytes()) {
                throw invalidPdf(null);
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidPdf(exception);
        }
    }

    private void createDirectories(Path... paths) {
        try {
            for (Path path : paths) {
                Files.createDirectories(path);
            }
        } catch (IOException exception) {
            throw new OperationException(
                "COMPARE_SCRATCH_FAILED",
                "PDF comparison scratch could not be created",
                exception
            );
        }
    }

    private OperationException encryptedPdf(Throwable cause) {
        return new OperationException(
            "ENCRYPTED_PDF_NOT_SUPPORTED",
            "Compare PDF requires two unencrypted PDFs",
            cause
        );
    }

    private OperationException invalidPdf(Throwable cause) {
        return new OperationException(
            "INVALID_PDF",
            "A comparison input is not a readable PDF",
            cause
        );
    }

    private void delete(Path path, RuntimeException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
            logger.error(
                "Could not remove partial comparison output {}",
                path,
                exception
            );
        }
    }
}
