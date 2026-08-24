package com.pdftools.service;

import com.pdftools.dto.PdfOperationResult;
import com.pdftools.exception.PdfProcessingException;
import com.pdftools.operations.LegacyOperationExecutor;
import com.pdftools.operations.LegacyOperationGuard;
import com.pdftools.operations.remove.RemovePdfOperation;
import com.pdftools.util.FilenameSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;

@Service
public class LegacyRemoveService {

    private final LegacyOperationExecutor operationExecutor;
    private final RemovePdfOperation removePdfOperation;
    private final ObjectMapper objectMapper;
    private final Path outputDirectory;

    public LegacyRemoveService(
            LegacyOperationExecutor operationExecutor,
            RemovePdfOperation removePdfOperation,
            ObjectMapper objectMapper,
            @Value("${pdf.upload.dir}") String outputDirectory) {
        this.operationExecutor = operationExecutor;
        this.removePdfOperation = removePdfOperation;
        this.objectMapper = objectMapper;
        this.outputDirectory = Path.of(outputDirectory);
    }

    public PdfOperationResult removePages(
            MultipartFile file,
            String pages,
            String originalFilename,
            LegacyOperationGuard guard) throws PdfProcessingException {
        String multipartFilename = file == null
            ? null
            : file.getOriginalFilename();
        String sourceFilename = FilenameSanitizer.sanitize(
            originalFilename == null || originalFilename.isBlank()
                ? multipartFilename
                : originalFilename,
            "source.pdf"
        );
        ObjectNode options = objectMapper.createObjectNode();
        if (pages != null) {
            options.put("pages", pages);
        }
        return operationExecutor.executeSinglePdf(
            removePdfOperation,
            file,
            options,
            sourceFilename,
            outputDirectory,
            ".legacy-remove-",
            "removed_",
            ".pdf",
            "Pages removed successfully",
            guard
        );
    }
}
