package com.pdftools.operations.protect;

import com.pdftools.operations.BoundedOutputStream;
import com.pdftools.operations.OperationCancelledException;
import com.pdftools.operations.OperationException;
import com.pdftools.operations.OutputLimitExceededException;
import com.pdftools.operations.split.SplitProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class PdfProtectionEngine {

    private static final int HEADER_SCAN_BYTES = 1024;

    private final SplitProperties properties;

    public PdfProtectionEngine(SplitProperties properties) {
        this.properties = properties;
    }

    public Path protect(
            Path source,
            Path workspace,
            ProtectPlanFactory.ProtectPlan plan,
            IntConsumer progress,
            Runnable cancellationCheck) {
        requirePdfHeader(source);
        Path scratchDirectory = workspace.resolve(".pdfbox-scratch");
        Path output = workspace.resolve("protected.pdf");
        try {
            Files.createDirectories(scratchDirectory);
        } catch (IOException exception) {
            throw new OperationException(
                "PROTECT_SCRATCH_FAILED",
                "The protection scratch directory could not be created",
                exception
            );
        }
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            () -> new ScratchFile(scratchDirectory.toFile());
        try (RandomAccessReadBufferedFile randomAccess =
                 new RandomAccessReadBufferedFile(source);
             PDDocument document = Loader.loadPDF(
                 randomAccess,
                 scratchCache
             )) {
            cancellationCheck.run();
            if (document.isEncrypted()) {
                throw encryptedPdf();
            }
            progress.accept(25);
            document.protect(policy(plan));
            progress.accept(60);
            try (OutputStream fileOutput = Files.newOutputStream(output);
                 BoundedOutputStream bounded = new BoundedOutputStream(
                     fileOutput,
                     properties.getMaxOutputBytes(),
                     cancellationCheck
                 )) {
                document.save(bounded);
            }
            cancellationCheck.run();
            progress.accept(90);
            return output;
        } catch (InvalidPasswordException exception) {
            delete(output);
            throw encryptedPdf();
        } catch (OutputLimitExceededException exception) {
            delete(output);
            throw new OperationException(
                "PROTECTED_OUTPUT_SIZE_LIMIT_EXCEEDED",
                "Protected PDF exceeds the configured output limit"
            );
        } catch (OperationException | OperationCancelledException exception) {
            delete(output);
            throw exception;
        } catch (IOException exception) {
            delete(output);
            throw new OperationException(
                "INVALID_PDF",
                "The input is not a readable PDF",
                exception
            );
        }
    }

    private StandardProtectionPolicy policy(
            ProtectPlanFactory.ProtectPlan plan) {
        AccessPermission permission = new AccessPermission();
        permission.setCanPrint(!plan.print().equals("none"));
        permission.setCanPrintFaithful(plan.print().equals("high"));
        permission.setCanExtractContent(plan.copy());
        permission.setCanModify(plan.modify());
        permission.setCanModifyAnnotations(plan.annotate());
        permission.setCanFillInForm(plan.fillForms());
        permission.setCanExtractForAccessibility(plan.accessibility());
        permission.setCanAssembleDocument(plan.assemble());
        StandardProtectionPolicy policy = new StandardProtectionPolicy(
            plan.ownerPassword(),
            plan.userPassword(),
            permission
        );
        policy.setEncryptionKeyLength(256);
        policy.setPreferAES(true);
        return policy;
    }

    private void requirePdfHeader(Path source) {
        try (InputStream input = Files.newInputStream(source)) {
            String prefix = new String(
                input.readNBytes(HEADER_SCAN_BYTES),
                StandardCharsets.ISO_8859_1
            );
            if (!prefix.contains("%PDF-")) {
                throw new OperationException(
                    "INVALID_PDF",
                    "The input is not a readable PDF"
                );
            }
        } catch (OperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OperationException(
                "INVALID_PDF",
                "The input is not a readable PDF",
                exception
            );
        }
    }

    private OperationException encryptedPdf() {
        return new OperationException(
            "ENCRYPTED_PDF",
            "Unlock the PDF before protecting it again"
        );
    }

    private void delete(Path output) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            throw new OperationException(
                "PROTECT_CLEANUP_FAILED",
                "Partial protected output could not be removed",
                exception
            );
        }
    }
}
