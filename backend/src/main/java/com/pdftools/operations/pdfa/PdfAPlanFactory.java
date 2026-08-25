package com.pdftools.operations.pdfa;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

@Component
public class PdfAPlanFactory {

    public PdfAPlan create(JsonNode options) {
        JsonNode node = options.get("profile");
        String value = "pdfa-2b";
        if (node != null) {
            if (!node.isTextual()) {
                throw invalidProfile();
            }
            value = node.asText().trim().toLowerCase(Locale.ROOT);
        }
        try {
            return new PdfAPlan(PdfAProfile.fromOption(value));
        } catch (IllegalArgumentException exception) {
            throw invalidProfile();
        }
    }

    private OperationException invalidProfile() {
        return new OperationException(
            "INVALID_PDFA_PROFILE",
            "profile must be pdfa-1b, pdfa-2b, or pdfa-3b"
        );
    }

    public enum PdfAProfile {
        PDFA_1_B("pdfa-1b", 1, PDFAFlavour.PDFA_1_B),
        PDFA_2_B("pdfa-2b", 2, PDFAFlavour.PDFA_2_B),
        PDFA_3_B("pdfa-3b", 3, PDFAFlavour.PDFA_3_B);

        private final String option;
        private final int libreOfficeVersion;
        private final PDFAFlavour flavour;

        PdfAProfile(
                String option,
                int libreOfficeVersion,
                PDFAFlavour flavour) {
            this.option = option;
            this.libreOfficeVersion = libreOfficeVersion;
            this.flavour = flavour;
        }

        public String option() {
            return option;
        }

        public int libreOfficeVersion() {
            return libreOfficeVersion;
        }

        public PDFAFlavour flavour() {
            return flavour;
        }

        public String exportFilter() {
            return "draw_pdf_Export:{\"SelectPdfVersion\":"
                + "{\"type\":\"long\",\"value\":\""
                + libreOfficeVersion + "\"}}";
        }

        static PdfAProfile fromOption(String value) {
            for (PdfAProfile profile : values()) {
                if (profile.option.equals(value)) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("Unknown PDF/A profile");
        }
    }

    public record PdfAPlan(PdfAProfile profile) {
    }
}
