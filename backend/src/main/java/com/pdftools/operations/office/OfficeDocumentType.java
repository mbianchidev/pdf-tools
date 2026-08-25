package com.pdftools.operations.office;

import java.util.Locale;

public enum OfficeDocumentType {
    WORD(
        "word",
        "WORD",
        "Word document",
        "writer_pdf_Export",
        "word-to-pdf.pdf",
        "INVALID_WORD_PDF_OUTPUT",
        ".docx",
        ".doc"
    ),
    POWERPOINT(
        "powerpoint",
        "POWERPOINT",
        "PowerPoint presentation",
        "impress_pdf_Export:{\"ExportHiddenSlides\":"
            + "{\"type\":\"boolean\",\"value\":\"true\"}}",
        "powerpoint-to-pdf.pdf",
        "INVALID_POWERPOINT_PDF_OUTPUT",
        ".pptx",
        ".ppt"
    ),
    EXCEL(
        "excel",
        "EXCEL",
        "Excel workbook",
        "calc_pdf_Export",
        "excel-to-pdf.pdf",
        "INVALID_EXCEL_PDF_OUTPUT",
        ".xlsx",
        ".xls"
    );

    private final String key;
    private final String codePrefix;
    private final String label;
    private final String exportFilter;
    private final String outputFilename;
    private final String invalidPdfCode;
    private final String modernExtension;
    private final String legacyExtension;

    OfficeDocumentType(
            String key,
            String codePrefix,
            String label,
            String exportFilter,
            String outputFilename,
            String invalidPdfCode,
            String modernExtension,
            String legacyExtension) {
        this.key = key;
        this.codePrefix = codePrefix;
        this.label = label;
        this.exportFilter = exportFilter;
        this.outputFilename = outputFilename;
        this.invalidPdfCode = invalidPdfCode;
        this.modernExtension = modernExtension;
        this.legacyExtension = legacyExtension;
    }

    public String key() {
        return key;
    }

    public String code(String suffix) {
        return codePrefix + "_" + suffix;
    }

    public String label() {
        return label;
    }

    public String exportFilter() {
        return exportFilter;
    }

    public String outputFilename() {
        return outputFilename;
    }

    public String invalidPdfCode() {
        return invalidPdfCode;
    }

    public String extension(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(modernExtension)
            ? modernExtension
            : legacyExtension;
    }
}
