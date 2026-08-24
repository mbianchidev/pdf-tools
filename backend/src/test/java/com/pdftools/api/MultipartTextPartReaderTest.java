package com.pdftools.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultipartTextPartReaderTest {

    private final MultipartTextPartReader reader = new MultipartTextPartReader();

    @Test
    void readsBoundedUtf8TextAndAllowsMissingOptionalParts() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addPart(new MockPart(
            "options",
            "{\"mode\":\"individual\"}".getBytes(StandardCharsets.UTF_8)
        ));

        assertEquals(
            "{\"mode\":\"individual\"}",
            reader.read(request, "options", 64, true)
        );
        assertNull(reader.read(request, "missing", 64, false));
    }

    @Test
    void rejectsOversizedAndMalformedTextBeforeDecoding() {
        MockHttpServletRequest oversized = new MockHttpServletRequest();
        oversized.addPart(new MockPart("groups", new byte[65]));
        MultipartTextPartException tooLarge = assertThrows(
            MultipartTextPartException.class,
            () -> reader.read(oversized, "groups", 64, false)
        );
        assertEquals(
            MultipartTextPartException.Reason.TOO_LARGE,
            tooLarge.getReason()
        );

        MockHttpServletRequest malformed = new MockHttpServletRequest();
        malformed.addPart(new MockPart(
            "groups",
            new byte[] {(byte) 0xc3, 0x28}
        ));
        MultipartTextPartException invalidUtf8 = assertThrows(
            MultipartTextPartException.class,
            () -> reader.read(malformed, "groups", 64, false)
        );
        assertEquals(
            MultipartTextPartException.Reason.INVALID_UTF8,
            invalidUtf8.getReason()
        );
    }
}
