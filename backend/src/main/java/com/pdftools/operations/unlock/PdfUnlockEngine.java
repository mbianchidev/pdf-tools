package com.pdftools.operations.unlock;

import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.shared.pdf.PdfInputValidator;
import com.pdftools.operations.security.PdfSecurityFiles;
import com.pdftools.operations.security.PdfSecurityProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class PdfUnlockEngine {

    private final PdfSecurityProperties properties;

    public PdfUnlockEngine(PdfSecurityProperties properties) {
        this.properties = properties;
    }

    public Path unlock(
            Path source,
            Path workspace,
            UnlockPlanFactory.UnlockPlan plan,
            IntConsumer progress,
            Runnable cancellationCheck) {
        PdfInputValidator.requirePdfHeader(source);
        Path output = workspace.resolve("unlocked.pdf");
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            PdfSecurityFiles.scratchCache(
                workspace,
                "UNLOCK_SCRATCH_FAILED",
                "The unlock scratch directory could not be created"
            );
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(source);
             PDDocument document = load(
                 randomAccess,
                 plan.password(),
                 scratchCache
             )) {
            cancellationCheck.run();
            if (!document.isEncrypted()) {
                throw new OperationException(
                    "PDF_NOT_ENCRYPTED",
                    "The input PDF is not password protected"
                );
            }
            progress.accept(25);
            document.setAllSecurityToBeRemoved(true);
            progress.accept(60);
            PdfSecurityFiles.save(
                document,
                output,
                properties.getMaxOutputBytes(),
                cancellationCheck
            );
            cancellationCheck.run();
            progress.accept(90);
            return output;
        } catch (InvalidPasswordException exception) {
            throw cleanup(output, new OperationException(
                "INVALID_PASSWORD",
                "The password did not unlock this PDF"
            ));
        } catch (OutputLimitExceededException exception) {
            throw cleanup(output, new OperationException(
                "UNLOCKED_OUTPUT_SIZE_LIMIT_EXCEEDED",
                "Unlocked PDF exceeds the configured output limit"
            ));
        } catch (OperationException | OperationCancelledException exception) {
            throw cleanup(output, exception);
        } catch (IOException exception) {
            throw cleanup(output, new OperationException(
                "INVALID_PDF",
                "The input is not a readable password-protected PDF",
                exception
            ));
        }
    }

    private PDDocument load(
            RandomAccessReadBufferedFile randomAccess,
            String password,
            RandomAccessStreamCache.StreamCacheCreateFunction scratchCache)
            throws IOException {
        try {
            return Loader.loadPDF(randomAccess, password, scratchCache);
        } catch (IllegalArgumentException exception) {
            throw new OperationException(
                "INVALID_PASSWORD",
                "The password contains characters unsupported by this PDF"
            );
        }
    }

    private <T extends RuntimeException> T cleanup(Path output, T failure) {
        return PdfSecurityFiles.cleanup(
            output,
            failure,
            "UNLOCK_CLEANUP_FAILED",
            "Partial unlocked output could not be removed"
        );
    }
}
