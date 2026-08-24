package com.pdftools.operations.protect;

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
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.IntConsumer;

@Component
public class PdfProtectionEngine {

    private final PdfSecurityProperties properties;

    public PdfProtectionEngine(PdfSecurityProperties properties) {
        this.properties = properties;
    }

    public Path protect(
            Path source,
            Path workspace,
            ProtectPlanFactory.ProtectPlan plan,
            IntConsumer progress,
            Runnable cancellationCheck) {
        PdfInputValidator.requirePdfHeader(source);
        Path output = workspace.resolve("protected.pdf");
        RandomAccessStreamCache.StreamCacheCreateFunction scratchCache =
            PdfSecurityFiles.scratchCache(
                workspace,
                "PROTECT_SCRATCH_FAILED",
                "The protection scratch directory could not be created"
            );
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
            throw cleanup(output, encryptedPdf());
        } catch (OutputLimitExceededException exception) {
            throw cleanup(output, new OperationException(
                "PROTECTED_OUTPUT_SIZE_LIMIT_EXCEEDED",
                "Protected PDF exceeds the configured output limit"
            ));
        } catch (OperationException | OperationCancelledException exception) {
            throw cleanup(output, exception);
        } catch (IOException exception) {
            throw cleanup(output, new OperationException(
                "INVALID_PDF",
                "The input is not a readable PDF",
                exception
            ));
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

    private OperationException encryptedPdf() {
        return new OperationException(
            "ENCRYPTED_PDF",
            "Unlock the PDF before protecting it again"
        );
    }

    private <T extends RuntimeException> T cleanup(Path output, T failure) {
        return PdfSecurityFiles.cleanup(
            output,
            failure,
            "PROTECT_CLEANUP_FAILED",
            "Partial protected output could not be removed"
        );
    }

}
