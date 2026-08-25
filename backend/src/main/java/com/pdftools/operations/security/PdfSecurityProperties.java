package com.pdftools.operations.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pdf.operations.security")
public class PdfSecurityProperties {

    private long maxOutputBytes = 128L * 1024L * 1024L;

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }
}
