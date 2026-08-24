package com.pdftools.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

@Component
public class MultipartTextPartReader {

    public String read(
            HttpServletRequest request,
            String name,
            int maxBytes,
            boolean required) {
        Part part;
        try {
            part = request.getPart(name);
        } catch (IOException | ServletException exception) {
            throw failure(
                HttpStatus.BAD_REQUEST,
                MultipartTextPartException.Reason.UNREADABLE,
                name,
                "The multipart upload could not be processed",
                exception
            );
        }
        if (part == null) {
            if (required) {
                throw failure(
                    HttpStatus.BAD_REQUEST,
                    MultipartTextPartException.Reason.MISSING,
                    name,
                    "Missing required multipart field: " + name,
                    null
                );
            }
            return null;
        }
        if (part.getSize() > maxBytes) {
            throw tooLarge(name);
        }

        try (InputStream input = part.getInputStream()) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw tooLarge(name);
            }
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw failure(
                HttpStatus.BAD_REQUEST,
                MultipartTextPartException.Reason.INVALID_UTF8,
                name,
                "Multipart field " + name + " must contain valid UTF-8",
                exception
            );
        } catch (IOException exception) {
            throw failure(
                HttpStatus.BAD_REQUEST,
                MultipartTextPartException.Reason.UNREADABLE,
                name,
                "Multipart field " + name + " could not be read",
                exception
            );
        }
    }

    private MultipartTextPartException tooLarge(String name) {
        return failure(
            HttpStatus.PAYLOAD_TOO_LARGE,
            MultipartTextPartException.Reason.TOO_LARGE,
            name,
            "Multipart field " + name + " exceeds its size limit",
            null
        );
    }

    private MultipartTextPartException failure(
            HttpStatus status,
            MultipartTextPartException.Reason reason,
            String name,
            String message,
            Throwable cause) {
        return new MultipartTextPartException(
            status,
            reason,
            name,
            message,
            cause
        );
    }
}
