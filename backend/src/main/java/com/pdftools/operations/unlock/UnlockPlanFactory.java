package com.pdftools.operations.unlock;

import com.pdftools.operations.OperationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;

@Component
public class UnlockPlanFactory {

    private static final int MAX_PASSWORD_BYTES = 127;

    public void validateShape(JsonNode options) {
        password(options);
    }

    public UnlockPlan create(JsonNode options) {
        return new UnlockPlan(password(options));
    }

    private String password(JsonNode options) {
        JsonNode node = options.get("password");
        if (node == null || node.isTextual() && node.asText().isEmpty()) {
            throw new OperationException(
                "PASSWORD_REQUIRED",
                "The current PDF password is required"
            );
        }
        if (!node.isTextual()) {
            throw new OperationException(
                "INVALID_PASSWORD",
                "password must be a string"
            );
        }
        String value = node.asText();
        if (value.getBytes(StandardCharsets.UTF_8).length
                > MAX_PASSWORD_BYTES) {
            throw new OperationException(
                "PASSWORD_TOO_LONG",
                "Password must stay within "
                    + MAX_PASSWORD_BYTES
                    + " UTF-8 bytes"
            );
        }
        return value;
    }

    public record UnlockPlan(String password) {
    }
}
