package org.apache.pdfbox.pdmodel.encryption;

public final class PdfBoxPasswordPreparation {

    private PdfBoxPasswordPreparation() {
    }

    public static String prepareStored(String password) {
        return SaslPrep.saslPrepStored(password);
    }
}
